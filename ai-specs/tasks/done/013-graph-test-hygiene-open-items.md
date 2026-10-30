# 013 — Graph test hygiene: the three open items

Task Identifier: `013-graph-test-hygiene-open-items`
Ticket ID: none (no external ticket exists for this task)
Base commit: `127c3c3f96` (`plugin/graph-workspace`, in sync with `origin/plugin/graph-workspace`)
Worktree: `.worktrees/test-hygiene-open-items`, branch `pm/graph-test-hygiene-open-items`
Predecessors: `010-make-graph-test-suite-meaningful`, `011-lifecycle-close-boundary-flake`,
`012-graph-workspace-lineage-divergence`

## 1. Scope

Close the three items left open by task 010, then close the task records.

| Item | Question |
|---|---|
| 1 | The `MapLeaseManagerShould` callback-close probes fail on a loaded host. Is the product wrong, or the probe? |
| 2 | `GraphWorkspacePerformanceDiagnostic.verifyRawReferenceFrameBound()` has never been reached end-to-end. Does its call site work? |
| 3 | `sdd-state` / `plan-policy.mjs` were reported absent, so the PM plan gate could not run. Restore or drop it. |

Explicitly out of scope: any production source change, any relaxation of a product guarantee, any
settle/liveness budget inherited from task 010, the presentation-disarm behaviour, the lineage
reconciliation in task 012, and the two acceptance drivers excluded from `test`.

## 2. Item 3 is resolved before any work: the tooling was never missing

The premise in task 010 §8 ("`sdd-state` and `plan-policy.mjs` are not installed on this machine") is
**false**. Both are shipped with this machine's SDD skill:

```
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/sdd-state       (bash wrapper)
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/sdd-state.mjs
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/lib/plan-policy.mjs
```

They were not on `PATH`, and the earlier search looked for a `sdd-state` binary rather than at the
skill's own entrypoint. The PM skill already documents the correct invocation
(`SDD_STATE="$SDD_SKILL_DIR/scripts/sdd-state"`), so nothing needs installing and no gate needs
dropping. This task runs `sdd-state validate-plan` on its own plan and records the command and exit
status.

## 3. Item 1 — the callback-close probe samples thread state instead of observing ordering

### 3.1 The product guarantee under test

`MapLeaseManager.close()` takes `settlementLock` with a *blocking* `lock()`. Every path that runs on
the event dispatch thread must therefore **not** take that lock while a settlement owns it; it must
use `settlementLock.tryLock()` and defer the work instead
(`MapLeaseManager.tryOrDeferSettlementOnEdt`, line 765). An EDT that waits there freezes the user
interface for the length of a settlement.

`MapLeaseManagerShould.completesExternalReloadCallbackCloseWithoutBlockingTheDetectingEdt` and
`completesLifecycleCallbackCloseWithoutBlockingTheDetectingEdt` assert that guarantee. The whole
assertion rests on `CallbackCloseProbe` being inside the settlement critical section: it is called
from `MapLeaseManager.completeReservedRequest` *before* `settlementLock.lock()` and registers a
`whenComplete` handler, and that handler then runs inside `future.complete(...)` while the lock is
held. So anything the handler observes about the EDT happened during settlement.

The assertion does not observe that. It **samples `Thread.getState()`**.

### 3.2 Root cause, verified

`DetectingEdt.TaskObservation.observeWaiting()` reads two facts that are not read atomically:

```java
if (watching.get() && active.get() && edtThread.getState() == Thread.State.WAITING) {
    waiting.set(true);          // latched permanently -> AssertionError in the test
    waitingOrFinished.countDown();
}
```

An idle `DetectingEdt` thread parks in `LinkedBlockingQueue.take()`, which **is** `WAITING`. If the
observed task finishes between the `active` read and the `getState()` read, the sample reports
WAITING and the probe raises

```
AssertionError: EDT invalidation waited for lease settlement
```

although the invalidation had already completed. Two paths reach the same sampler: the observer
thread's `Thread.yield()` spin loop (`observeWaits`) and the callback thread's direct
`observeWaiting()` call inside `awaitWaitingOrFinished`.

On an idle host that window is a few nanoseconds. On a loaded host the spinning observer thread is
preempted inside it, and the false failure becomes frequent — which is exactly the reported
load-sensitivity, and why the failure rate tracks ambient load rather than the diff.

**Deterministic reproduction (no machine load).** A scratch copy of the test — outside the worktree —
inserted a 20 ms park between the `active` read and the `getState()` read, emulating precisely the
preemption a loaded host performs there. Production source untouched. The probe then reported the
reported failure, and the stack trace recorded at the moment of the false latch shows the EDT was
**not** in the settlement path at all:

```
SCRATCH observed WAITING; stack of map-lease-detecting-edt
  at java.base/jdk.internal.misc.Unsafe.park(Native Method)
  at java.util.concurrent.locks.LockSupport.park(LockSupport.java:371)
  at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(...)
  at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:435)
  at ...MapLeaseManagerShould$DetectingEdt.runLoop(MapLeaseManagerShould.java:2394)
```

`LinkedBlockingQueue.take()` is the queue-idle park that follows every completed task. The task under
observation had finished; the probe sampled the thread afterwards and blamed lease settlement.

Verdict: **the probe is demonstrably unsound, and its failure does not establish a production
defect.** Its message asserts a cause it cannot distinguish. The claim is deliberately narrower than
"the product is correct": the scratch reproduction proves this failure is reachable while the
product behaves correctly, and it explains the observed load correlation, but the original loaded-host
failures were not re-captured with an unmodified run, so the attribution of every one of them to this
race is an inference rather than a measurement.

### 3.3 Design of the repair

Replace the state sample with an **ordering witness**, and delete the sampling machinery.

`TaskObservation` already counts down when a task finishes. Keep only that:

- `activate()` counts down `started` before the task body runs.
- `finish()` counts down `finished` after it returns.
- Nothing reads `Thread.getState()`; nothing spins; no observer thread exists.

Completion ordering alone is **not** sufficient, and the design was revised after review to say so.
It excludes an unbounded block, but not a bounded one: replacing `tryLock()` with
`tryLock(100, MILLISECONDS)` stalls the EDT for 100 ms, times out, defers, and completes while the
callback still owns the lock — the ordering witness passes. That is a real violation of "closing must
not block the event dispatch thread", so the probe must measure the stall itself.

The repair therefore asserts three independent facts inside the completion handler:

```java
if (!settlementLock.get().isHeldByCurrentThread()) { … "not running under the settlement lock" }
if (!observationToUse.awaitFinished(EDT_LIVENESS_BUDGET_MILLIS)) { … "did not complete" }
if (observationToUse.deferrals() != 1) { … "did not take the failed-tryLock path" }
if (observationToUse.waitedMillis() != 0L) { … "parked for N ms inside its own task" }
```

1. **Lock ownership** (`isHeldByCurrentThread`, read via the existing `privateField` helper) proves
the premise the ordering argument depends on: this handler is inside the settlement critical
section. It is not assumed from the call site.
2. **Ordering** — the invalidation finished while that lock was still held. Causally sufficient for
"the EDT did not wait for settlement to be released": an unbounded `lock()` never finishes, at any
budget.
3. **Identity** — `TaskObservation` snapshots the settlement hook's failed-tryLock counter at task
start and reports the delta. `expectNextTask()` attaches the observation to whichever runnable is
dequeued next, which is not by itself proof that it is the invalidation; `deferrals() == 1` proves
the observed task is the one that took the defer path, so the ordering fact is about the right task.
4. **No contention at all** — `TaskObservation` snapshots the JVM's own per-thread contention
accounting at task start and task end, **both** halves of it: the park counters (`getWaitedCount`,
`getWaitedTime`) and the intrinsic-monitor counters (`getBlockedCount`, `getBlockedTime`). The deltas
over the task body are exactly "did this thread wait while executing the invalidation", and they catch
the bounded counterexample above. Counts are differenced as well as durations because counts are exact
events while the millisecond durations round a sub-millisecond contention to zero. Contention accounting
is enabled for the fixture's lifetime and its absence is reported as a probe failure, never as a pass.

   The second half is not optional. A first delivery of this design differenced only park time, and the
   frontier audit falsified it: a production mutant that makes the event dispatch thread **block** on
   intrinsic-monitor entry — `close()` blocking the EDT, in the terms of the guarantee — was accepted by
   both probes, because `getBlockedTime()` is recorded separately from `getWaitedTime()`. Reproduced
   here with a 300 ms holder: both probes passed the first delivery, and the observed task's accounting
   read `blockedCount=1 blockedMillis=300 waitedCount=0 waitedMillis=0`. Measured on this JVM: untimed
   failing `tryLock()` → no event of either kind, `tryLock(100 ms)` that times out → 1 park event of
   100 ms, contended monitor → 1 block event of 300 ms, plain work → none.

The waited-time witness is not load-sensitive: it is a cumulative JVM counter over the task's
execution, so a descheduled thread accrues scheduling delay but no park time, and a thread that
really parks accrues exactly its wait.

The budget is a named, documented constant `EDT_LIVENESS_BUDGET_MILLIS = 20_000`, matching the
display-settle liveness discipline in this module. It is a pure liveness bound on "the EDT is
scheduled at all". Every other thread-scheduling wait in this fixture is parameterized to the same
constant and documented as a scheduling bound: `DetectingEdt` readiness, `DetectingEdt.call`,
`DetectingEdt.close`, `CallbackCloseProbe`'s operation start, operation join and composite callback
wait, and `acquire(...)`'s future wait.

Deleted by the repair: the `map-lease-detecting-edt-observer` thread, `observeWaits()`,
`observeWaiting()`, the `active` / `watching` / `waiting` flags, `currentObservation`,
`awaitWaitingOrFinished()`, `awaitStarted()`, `waitingAtBoundary()`, and the two
`assertThat(probe.waitingAtBoundary()).isFalse()` lines. Net removal: one thread, one busy spin loop
and one non-atomic observation. This also removes a source of avoidable CPU contention from the
suite.

The repair does not claim the callback-close path is immune to starvation. A finite liveness deadline
can still expire on a pathological host, and that would be reported as a failure of the probe, not of
the product. The claim is narrower and checkable: the sampling race is removed, and every remaining
wait is a scheduling bound whose value does not affect whether a real violation is detected.

### 3.4 Which variant is the more attractive option, and why the alternatives lose

| Option | Verdict |
|---|---|
| Raise the 1 s windows | Rejected. Keeps the state sample; the false positive is a *race*, not a slow wait, so a bigger budget does not remove it. |
| Delete the two tests | Rejected. They cover a real guarantee; the coverage would be lost, not repaired. |
| Assert only inside the `boundaryAssertion` hook | Insufficient. The hook cannot see the EDT task at all. |
| Ordering witness (chosen) | Removes the race by construction, keeps the guarantee, removes machinery. |

### 3.5 Falsifiability evidence gathered before planning

Prototype changes were made in `/tmp/scratch3` against the real classpath; production sources were
never modified for this.

| Probe | Production variant | Result |
|---|---|---|
| revised probe | pristine | **pass** 3/3, and 49/49 for the class |
| revised probe | `tryLock()` → `lock()` (unbounded block) | **fail**: `EDT invalidation did not complete while the settlement lock was held (started=true)` after the budget |
| revised probe | `tryLock()` → `tryLock(100, MILLISECONDS)` (bounded stall) | **fail**: `EDT invalidation parked for 100 ms inside its own task while the settlement lock was held` |
| old sampling probe | `tryLock()` → `lock()` | fail, `EDT invalidation waited for lease settlement` |
| old sampling probe | pristine + artificial 20 ms preemption window | **fail** — the false positive, with the EDT stack at `LinkedBlockingQueue.take()` |
| revised probe | pristine, `deferrals() != 1` mutated to `!= 0` | **fail**: `The observed EDT task did not take the failed-settlement-tryLock path (deferrals=1)` — so the identity assertion is not vacuous and the observed task really is the invalidation |

So the repair keeps sensitivity to both the unbounded and the bounded violation, removes the false
positive, and pins observation identity. An earlier draft claimed the unbounded mutant alone proved
observation identity; it does not, because the callback's own synchronous cleanup can time out behind
a blocked invalidation and fail the test for the wrong reason. The `deferrals()` assertion replaces
that inference with a measurement.

### 3.6 Pre-existing, independent flake found while measuring: a different probe, not this one

Whole-class runs pinned to two CPUs surfaced an intermittent failure in
`retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects` at
`assertThat(environment.model).isSameAs(oldModel)` — the external reload had already replaced the
model when the deferred lifecycle-removal retry was inspected. It is **not** the reported failure and
**not** caused by this change: the same class run 25× pinned to the same two CPUs fails it **3/25 on
unmodified code** (versus 2/20 with the revised probes, i.e. statistically indistinguishable).

It lives in the other probe family (`DeliveryRaceProbe` plus `awaitOperationReturnOrLockWait`, which
samples `operation.getState()` and has its own 1 s windows), and it is an ordering assumption about
when the reload may land rather than a thread-state confusion. Repairing it needs its own design and
its own falsifiability argument, exactly as the previous vacuous probe rewrite in task 011 §6.3
demonstrated. **Recorded here, not fixed here.**

### 3.7 `LayoutWorkerShould`

Reported load-sensitive but with no recorded failure text, and not reproduced here: the class passes
28/28 unconstrained and 3× 28/28 pinned to two cores. Its wall-clock windows are `future.get(5 s)` in
`await()` and `engine.started.await(5 s)`; measured per-test cost is at most 2.53 s
(`restartTheEscalationCountForANewRequest`, 6001 sequential steps), so a whole-test 5 s window has a
per-step margin of ~0.4 ms against 5 s. No failure has been observed, so no change is planned unless
the observed failure text shows a specific, reproducible overrun. Recorded rather than guessed at.

## 4. Item 2 — the performance-diagnostic call site

`GraphWorkspacePerformanceDiagnostic.run()` calls `verifyRawReferenceFrameBound()` after
`runLifecycleProbes(workspaces.get(0))` (line 195). The bound it enforces is
`RAW_REFERENCE_MAX_FRAME_NANOS = 5 s`, and task 010 §1.3 measured the raw reference fixture at
5.472–5.858 s on this host on the first frame. Therefore the *expected* verdict of a successful
end-to-end run on this machine is a `DiagnosticFailure` naming the per-frame durations — not a pass.

That is still the correct verification: reaching line 195 and raising that failure proves the call
site works, that the constant is the one enforced, and that the message keeps the frame list. A
green run would prove the same thing with the opposite verdict. The task is to *reach* it, not to
make it pass, and definitely not to move the constant.

Preconditions: the run must be exclusive (it is a wall-clock gate), and `PROCESS_DEADLINE_NANOS` is
40 minutes, so the run needs a launch with a hard timeout above that and must not overlap with other
build or test work.

Reachability and the performance verdict are **recorded separately**, because a run can end in four
ways and only one of them is a clean statement about the call site:

| Outcome | Call site | Performance verdict |
|---|---|---|
| completes, all rows pass | reached | pass |
| completes, bound exceeded | reached | fail (expected on this host) |
| fails inside the raw-reference fixture or the ledger/graph step after line 195 | reached | inconclusive |
| fails in a scenario row or the deadline before line 195 | **not reached** | inconclusive |

The predicted outcome is the second row, but it is a prediction, not a premise: the diagnostic does
substantial scenario work before it reaches the raw reference, and a run that finishes green is an
equally good proof of reachability. In the fourth case the honest statement is "cannot be verified on
this host in this run", item 2 stays open, and no partial-run mode is added to manufacture
reachability.

## 5. Specification

### S1 — ordering, not sampling, on the callback-close path
The `DetectingEdt` fixture and both callback-close probes contain no `Thread.getState()` call, no
observer thread and no spin loop. The rest of `MapLeaseManagerShould` is out of scope: the unrelated
`awaitOperationReturnOrLockWait` / `DeliveryRaceProbe` family keeps its sampler and its 1 s windows
(§3.6), and no source check is written that would silently require rewriting it.

### S2 — the guarantee is still guarded, at both boundaries
With `MapLeaseManager.tryOrDeferSettlementOnEdt` mutated to `lock()`, both callback-close probes fail.
With it mutated to `tryLock(100, MILLISECONDS)`, both fail on the waited-time assertion. Production
restored byte-identically afterwards.

### S3 — no production change
`git diff --exit-code 127c3c3f96 -- freeplane_plugin_graph/src/main/java` is empty and no untracked
file exists under that directory. A clean `git status` alone is not evidence: it cannot see a
production edit committed during an earlier task.

### S4 — no budget is weakened
`DISPLAY_SETTLE_BUDGET_MILLIS`, `X11_WM_BORDER_TOLERANCE`, `X11_AWT_BOUNDS_TOLERANCE`,
`RAW_REFERENCE_MAX_FRAME_NANOS`, `OPERATION_TIMEOUT_NANOS` and every assertion inherited from task 010
are unchanged.

### S5 — diagnostic call site reached
`gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` either reaches
`verifyRawReferenceFrameBound()` and reports its verdict, or the run is recorded as not having reached
it with the reason. Either way the exact command, duration and terminal output are recorded.

### S6 — plan gate runs
`sdd-state validate-plan` is executed on this task's plan file and exits 0. The command and exit
status are recorded.

### S7 — suite state is reported, not asserted away
The module suite is run through `scripts/run-suite-on-xvfb.sh` and its exact result is recorded.
`MapLeaseManagerShould` is additionally run 25× under CPU-pinned contention and the observed failure
count is reported. The result is a measured rate, never a claim that flakiness is absent — the
pre-existing `retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects` flake (§3.6) is expected to
appear in that count and its presence is not a regression.

### S8 — task records close truthfully
`010` and `012` move to `ai-specs/tasks/done/` only when their remaining content is a record of
completed work. `010` §8's false tooling claim and the suite document's stale
`-PgraphDisplayAcceptance` row are corrected; the dead `graphDisplayAcceptance` property is removed
from `build.gradle`.

## 6. Files expected to change

| File | Change |
|---|---|
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java` | probe redesign (§3.3) |
| `freeplane_plugin_graph/build.gradle` | remove the dead `graphDisplayAcceptance` system property |
| `freeplane_plugin_graph/doc/running-the-test-suite.md` | drop the stale gate row; record the liveness/ordering rule |
| `ai-specs/tasks/in-progress/013-…md`, `013-…-plan.md` | this record and its plan |
| `ai-specs/tasks/in-progress/010-…md` → `done/`, `in-progress/012-…md` → `done/` | close-out |

No production file, no fixture, no test resource.

## 7. Implementation

### 7.1 Task 1 — `MapLeaseManagerShould` probe redesign (delivered)

`DetectingEdt` lost its observer thread, its `Thread.yield()` spin loop and its sampled
`active`/`watching`/`waiting` state. `TaskObservation` now reports four measured facts about the one
task it observes: `started`, `finished`, the delta of the settlement hook's failed-tryLock counter, and
the delta of the JVM's per-thread park time (`ThreadMXBean.getThreadInfo(...).getWaitedTime()`).
`DetectingEdt` implements `MapLeaseManager.SettlementTestHook` and owns that counter, so the probe can
prove *which* task it observed.

`CallbackCloseProbe` asserts, inside the completion handler and therefore inside the settlement
critical section:

| Assertion | Evidence |
|---|---|
| settlement lock held by this thread | the premise of every ordering claim below |
| observed task finished within the liveness budget | it did not wait for settlement release |
| observed task performed exactly one failed settlement try-lock | it is the invalidation task |
| observed task recorded no contention event of either kind | it did not stall: no park (a bounded timed try-lock) and no intrinsic-monitor block (`close()` blocking the EDT) |

Every thread-scheduling wait in the fixture now shares `EDT_LIVENESS_BUDGET_MILLIS = 20_000` and is
documented as a scheduling bound: `DetectingEdt` readiness, `DetectingEdt.call`, `DetectingEdt.close`,
`operationStarted.await`, `operationRunner.join`, the composite `callbackFinished.await`
(2× the constant) and `acquire(...)`.

Diff: 1 file, +137/−79. No assertion about the product was removed; the two removed lines sampled a
thread state that the four assertions above replace.

### 7.2 Task 2 — dead switch and document (delivered)

`build.gradle`: the `graphDisplayAcceptance` system property is gone. Task 010 §12.2 removed the gate
it fed and no Java source ever read it. `doc/running-the-test-suite.md`: the stale
`panDuringPresentationIsPreservedThroughExit` row is removed from the gate table, and the two rules
this task establishes are written down — a liveness budget is never a latency assertion, and a
concurrency probe asserts ordering or a measured fact and never a sampled thread state.

## 8. Verification evidence

All commands run in `.worktrees/test-hygiene-open-items` with
`JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu`.

### 8.1 Falsifiability (S2) — disposable production mutants, worktree untouched

Mutants compiled from the delivered production source into `/tmp`, shadowed ahead of the built
classes on the classpath:

| Variant of `tryOrDeferSettlementOnEdt` | `completesLifecycleCallbackClose…` | `completesExternalReloadCallbackClose…` |
|---|---|---|
| pristine `tryLock()` | **pass** | **pass** |
| `lock()` — unbounded block | **fail**: `EDT invalidation did not complete while the settlement lock was held (started=true)` after the 20 s budget, 22.8 s | **fail**, same message, 23.3 s |
| `tryLock(100, MILLISECONDS)` — bounded park | **fail**: `… parked 1 time(s) for 100 ms, blocked on a monitor 0 time(s) …`, 3.1 s | **fail**, `… parked 1 time(s) for 101 ms …`, 3.0 s |
| bounded intrinsic-monitor block on the EDT (300 ms holder) | **fail**: `… parked 0 time(s) …, blocked on a monitor 1 time(s) for 300 ms …`, 3.3 s | **fail**, same message, 2.8 s |
| **first delivery** (park time only) against the same monitor block | **pass** — the audit's finding, reproduced here | **pass** |

Observation identity (S3 of §3.3) is pinned by a separate check: a test-only copy with
`deferrals() != 1` relaxed to `!= 0` fails with
`The observed EDT task did not take the failed-settlement-tryLock path (deferrals=1)`. The assertion
therefore measures the identity it claims rather than passing vacuously.

Reproduction of the original defect (kept from `3a`): a test-only copy that parks 20 ms between the
old sampler's `active` read and its `getState()` read reported
`EDT invalidation waited for lease settlement` on pristine production, with the observed EDT stack at
`LinkedBlockingQueue.take()` inside `DetectingEdt.runLoop`.

### 8.2 Stability (S7)

| Run | Command | Result |
|---|---|---|
| class, 3× unconstrained | `SingleMethodRunner MapLeaseManagerShould` | 49/49 each |
| class, 25× pinned to CPUs 0-1 | `taskset -c 0,1 …` | **3 failures / 25**, all `retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects` |
| same, **unmodified** code, 25× pinned | control | **3 failures / 25**, same test |
| both callback-close probes, 5× pinned each | `taskset -c 0,1 …` | **5/5 pass each** |
| both probes, 30 runs on pristine production | accounting probe | not one contention event of either kind was recorded, which is why exact counts are safe to assert |

The residual count is the pre-existing `DeliveryRaceProbe` flake of §3.6, at the same rate with and
without this change. It is not hidden: it is attributed, and left for its own task.

### 8.3 Module suite (S7)

Both runs via `freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh` (Xvfb + openbox):

| Run | Result |
|---|---|
| 1 | **exit 1** — 1458 tests, **1 failed**, 4 skipped, 13m25s. `GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromNormalWindow`: `Display condition did not settle within 20000 ms; bounds=Rectangle[0,0,1000,700]` |
| 2 | **exit 0** — 1458 tests, **0 failed**, 4 skipped, 13m33s |
| 3 — final delivered tree, after the contention-count change | **exit 0** — 1458 tests, **0 failed**, 4 skipped, 13m30s |

Run 1's failure is a window-manager placement race, not a settle-budget problem, and not reachable
from this diff: the fixture recorded `DISPLAY before bounds=(120,100,1000,700) … restored
bounds=(0,0,1000,700)`, so openbox placed the window at the origin after the presentation round trip
and the condition legitimately never became true. The test passed 3/3 in isolation immediately
afterwards, and this diff touches only `MapLeaseManagerShould`, one unreferenced Gradle system
property and one document. Recorded as a pre-existing intermittent in a test this task does not
change.

### 8.4 No production change (S3)

```
git diff --exit-code 127c3c3f96 -- freeplane_plugin_graph/src/main/java   → exit 0
git status --porcelain freeplane_plugin_graph/src/main/java               → 0 lines
```

### 8.5 Plan gate (S6)

```
~/.pi/agent/skills/subagent-driven-development/scripts/sdd-state \
  validate-plan ai-specs/tasks/in-progress/013-graph-test-hygiene-open-items-plan.md   → exit 0
```
`5` tasks parsed, `5` `## Task` headings, so no task was silently truncated: 1 advanced, 2 standard,
3 standard, 4 advanced, 5 standard.

## 9. Item 2 — executed, not verified

```
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic
EXIT=1   DURATION=2435s (40m35s)
```

Terminal failure:

```
GraphWorkspacePerformanceDiagnostic$DiagnosticFailure: Forty-minute diagnostic process deadline exceeded
	at …GraphWorkspacePerformanceDiagnostic.checkDeadline(GraphWorkspacePerformanceDiagnostic.java:1140)
	at …GraphWorkspacePerformanceDiagnostic.runScenario(GraphWorkspacePerformanceDiagnostic.java:300)
	at …GraphWorkspacePerformanceDiagnostic.run(GraphWorkspacePerformanceDiagnostic.java:192)
```

This is the fourth row of §4's outcome table: the process deadline expired **inside the scenario
sweep**, before `run() :195` is reached. `run()` creates its output directory and writes the generated
fixtures before the sweep, so `freeplane_plugin_graph/build/graph-performance/` does exist and holds
`two-map.fpg`, `three-map.fpg` and `reference-2000-5000.fpg` — but no ledger and no measurement
output, because `writeLedger()` is only reached after the sweep. So the answer to item 2 is *no*: the
`verifyRawReferenceFrameBound()` call site was **not** exercised.

What the run does establish, and it is worth more than the original "needs an idle machine" note:
the raw-reference gate is **unreachable in practice on this host**, because the diagnostic spends its
entire 40-minute process budget in the preceding scenario sweep. The blocker is
`PROCESS_DEADLINE_NANOS`, not `RAW_REFERENCE_MAX_FRAME_NANOS`.

Deliberately not done, per the standing constraint: no raise of `PROCESS_DEADLINE_NANOS`, no raise of
`RAW_REFERENCE_MAX_FRAME_NANOS`, no "raw reference only" mode added to make the call site reachable.
Each would turn a known-red gate into a passing one without verifying anything. Item 2 stays open.

## 10. What remains open

1. **Item 2** (§9). Unreachable call site; needs a decision that is not "loosen a bound". The honest
   options are a diagnostic that can be asked to run one stage, or accepting that the wall-clock gate
   is exercised only on a dedicated performance host. Both are design decisions for the owner.
2. **`retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects`** (§3.6). Pre-existing, reproduced at
   3/25 pinned to two CPUs on unmodified code, a different probe family, needs its own design and
   falsifiability argument.
3. **`GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromNormalWindow`** (§8.3).
   Pre-existing window-manager placement race under Xvfb + openbox, 1 failure in 2 full-suite runs,
   3/3 passing in isolation.
4. **`LayoutWorkerShould`** (§3.7). Still unreproduced; 28/28 unconstrained and 3× 28/28 pinned to two
   CPUs. No change was made, because guessing at a budget is the mistake task 010 already paid for.

## 11. Frontier audit (independent subsession) and its disposition

A fresh frontier subsession audited the three delivered commits read-only, with its own mutation
battery, and returned **FAIL** with two findings. Both were reproduced here before being acted on.

### 11.1 MAJOR — the probe measured park time only, so an intrinsic-monitor block escaped it

The audit's mutant put bounded contention on a monitor that the event dispatch thread must enter, and
both delivered probes **passed** it. Reproduced here with a 300 ms holder: both probes passed, while the
observed task's accounting read `blockedCount=1 blockedMillis=300 waitedCount=0 waitedMillis=0`.
`ThreadInfo.getBlockedTime()` is recorded separately from `getWaitedTime()`, so

```text
closing must not block the event dispatch thread
```

was only half covered: a bounded `tryLock` was caught, a `synchronized` entry was not.

**Fixed.** `TaskObservation` now differences both halves of the JVM's contention accounting, counts as
well as durations. Verified: the monitor mutant fails both probes
(`… blocked on a monitor 1 time(s) for 300 ms …`), and 30 pristine runs recorded no contention event of
either kind, so asserting exact counts does not reintroduce load sensitivity.

### 11.2 MINOR — §9 named the wrong output directory

The record said `build/graph-performance/` was not created. It is
`freeplane_plugin_graph/build/graph-performance/`, it exists, and it holds the three generated fixtures;
what is missing is the ledger, which `writeLedger()` only writes after the sweep. Corrected in §9.

### 11.3 What the audit confirmed independently

Root-cause mechanism (§3.2) — including the `LinkedBlockingQueue.take()` stack; the four assertions and
the fact that the handler really runs under `settlementLock`; the mutation battery outcomes for
`lock()`, `tryLock(100 ms)`, the identity relaxation and a completion-outside-the-lock variant; the
production byte-identity; that no named budget or tolerance was weakened; and that the plan gate exits 0
with five tasks. It judged §9's non-reachability claim supported by the recorded log but the *general*
claim unverified, which §9 already limits to "on this host in this run".

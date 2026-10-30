# 013 plan — graph test hygiene: the three open items

Source record: `ai-specs/tasks/in-progress/013-graph-test-hygiene-open-items.md`
Base commit: `127c3c3f96`

## Global Constraints

- No production source change. `freeplane_plugin_graph/src/main/java/**` stays byte-identical to
  `127c3c3f96`. Production mutations are performed only in disposable copies outside the worktree.
- No weakened assertion and no weakened budget: `DISPLAY_SETTLE_BUDGET_MILLIS`,
  `X11_WM_BORDER_TOLERANCE`, `X11_AWT_BOUNDS_TOLERANCE`, `RAW_REFERENCE_MAX_FRAME_NANOS`,
  `OPERATION_TIMEOUT_NANOS` and every assertion inherited from task 010 are unchanged.
- No synthetic machine-wide CPU load. Contention is produced only by pinning the process under test
  to a small CPU set (`taskset`) or by running real test work.
- Java 8 source level, 4-space indent, UTF-8. Toolchain
  `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu`. Use `gradle`, never `gradlew` or Maven.
- The module suite is run through `freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh`.
- Every verification step records its exact command and its exact result. A green claim without the
  recorded output is not evidence.

## Task 1: Replace the sampled EDT thread state with ordering, identity and park-time evidence

**Implementer tier:** Advanced

Files: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`

- Add the documented liveness constant `EDT_LIVENESS_BUDGET_MILLIS = 20_000L` explaining that it
  bounds scheduling, never latency.
- Reduce `DetectingEdt.TaskObservation` to measured facts about one task: `started`, `finished`, the
  delta of the settlement hook's failed-tryLock counter, and the delta of the JVM's per-thread park
  time (`ThreadMXBean.getThreadInfo(...).getWaitedTime()`). Delete `active`, `watching`, `waiting`,
  `awaitWaitingOrFinished()`, `awaitStarted()` and `observeWaiting()`.
- Make `DetectingEdt` implement `MapLeaseManager.SettlementTestHook` so it owns the failed-tryLock
  counter that identifies the invalidation task, and enable thread contention monitoring for its
  lifetime (fail if unsupported rather than passing silently).
- Delete the `map-lease-detecting-edt-observer` thread, `observeWaits()`, `currentObservation` and the
  `observer` field from `DetectingEdt`.
- In `CallbackCloseProbe`, capture the settlement lock via the existing `privateField` helper and
  assert, inside the completion handler: the lock is held by the current thread; the observed task
  finished within the liveness budget; the observed task performed exactly one failed settlement
  try-lock; and the observed task's park time is zero. Replace the sampled boundary with those four
  checks and remove `waitingAtBoundary` and its accessor.
- Parameterize the fixture's thread-scheduling waits to the same constant: `ready.await`,
  `DetectingEdt.call`'s result wait, `DetectingEdt.close`'s join, `operationStarted.await`,
  `operationRunner.join`, `callbackFinished.await` (twice the constant, since it bounds a callback
  that itself waits on one such window) and `acquire(...)`'s future wait.
- Remove the two `assertThat(probe.waitingAtBoundary()).isFalse()` assertions. Every other assertion
  in both tests is retained unchanged.

Verification: `MapLeaseManagerShould` compiles and passes 49/49; both callback-close probes pass 5/5
pinned to two CPUs; no `Thread.getState()` and no spin loop remains in `DetectingEdt` or
`CallbackCloseProbe`.

## Task 2: Remove the dead display-acceptance switch and correct the suite document

**Implementer tier:** Standard

Files: `freeplane_plugin_graph/build.gradle`,
`freeplane_plugin_graph/doc/running-the-test-suite.md`

- Delete the `graphDisplayAcceptance` system property from the `test` task: task 010 §12.2 removed the
  gate it fed and no test reads it.
- In the suite document, replace the stale `panDuringPresentationIsPreservedThroughExit` row (it is
  not gated any more) and add the two rules this task establishes: a liveness budget must never
  report host load as a defect, and a concurrency probe must assert ordering rather than sample
  thread state.

Verification: `grep -rn graphDisplayAcceptance` over tracked sources returns only the records that
describe its history; the document's table lists no gate that no longer exists.

## Task 3: Execute the performance diagnostic and record whether its call site is reached

**Implementer tier:** Standard

Files: `ai-specs/tasks/in-progress/013-graph-test-hygiene-open-items.md` (evidence only)

- Run `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` with a hard wall-clock timeout
  above `PROCESS_DEADLINE_NANOS`, exclusive of all other build and test work, capturing the full
  output.
- Record: the exact command, the wall-clock duration, whether `verifyRawReferenceFrameBound()` was
  reached, and the verdict it produced. The expected verdict on this host is the
  `DiagnosticFailure` naming the per-frame durations, because the first raw reference frame measures
  5.5–5.9 s against a 5 s bound.
- Do not change `RAW_REFERENCE_MAX_FRAME_NANOS`, and do not add a partial-run mode to reach the call
  site. If a scenario row fails first, record the call site as still unverified with the reason.

Verification: the recorded output contains either the frame list from
`verifyRawReferenceFrameBound()` or the failing stage that preceded it, plus the measured duration.

## Task 4: Prove the repaired probes are neither vacuous nor newly flaky

**Implementer tier:** Advanced

Files: none in the worktree; disposable copies only.

- Sensitivity to an unbounded block: compile a copy of `MapLeaseManager` with
  `tryOrDeferSettlementOnEdt` taking `settlementLock.lock()` instead of `tryLock()`, shadow it ahead
  of the built classes on the classpath, and confirm **both** callback-close probes fail on the
  ordering message.
- Sensitivity to a bounded stall: repeat with `settlementLock.tryLock(100, MILLISECONDS)` and confirm
  **both** fail on the contention message reporting a park event. This is the case the ordering witness
  alone cannot catch, and the reason the contention assertion exists.
- Sensitivity to a bounded block: repeat with a mutant that makes the event dispatch thread block on
  intrinsic-monitor entry for a bounded time, and confirm **both** fail reporting a block event. Park
  time alone misses this, because the JVM accounts for it separately.
- Non-vacuity of observation identity: build a test-only copy with `deferrals() != 1` relaxed to
  `!= 0` and confirm the probe fails while reporting `deferrals=1`, proving the observed task is the
  invalidation and not a neighbour.
- Restore and confirm the worktree is untouched: `git status --porcelain` empty for
  `src/main/java`, `git diff --exit-code 127c3c3f96 -- freeplane_plugin_graph/src/main/java` empty, and
  no untracked file under that directory.
- Robustness: run `MapLeaseManagerShould` 25× pinned to two CPUs and 3× unconstrained; report the
  observed failure count together with the pre-existing
  `retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects` rate measured on unmodified code
  (3/25 under the same pinning), so a non-zero count is attributed rather than hidden.
- Suite: run `freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh` and record tests/failures/skips
  and duration.

Verification: both mutants fail both probes with the expected distinct messages; the relaxed-identity
copy fails; pristine runs show 0 failures; the suite result is recorded verbatim including any
failure.

## Task 5: Close the task records

**Implementer tier:** Standard

Files: `ai-specs/tasks/in-progress/010-…md`, `ai-specs/tasks/in-progress/012-…md`,
`ai-specs/tasks/in-progress/013-…md`

- Correct task 010 §8: the SDD tooling exists at
  `~/.pi/agent/skills/subagent-driven-development/scripts/sdd-state`; the gate was never
  unrunnable, it was off `PATH`. Add the close-out section pointing at this task.
- Confirm task 012's open decisions are resolved before moving it: the backup ref
  `origin/backup/graph-workspace-pre-sync-20261029` exists at `2b78cde01c`, and local and remote
  `plugin/graph-workspace` both point at `127c3c3f96`. Record the verification and close.
- `git mv` both into `ai-specs/tasks/done/` only after their remaining content describes completed
  work.

Verification: `sdd-state validate-plan` exits 0 on this plan; `git status` shows the moves;
no `in-progress` record still names open work covered here.

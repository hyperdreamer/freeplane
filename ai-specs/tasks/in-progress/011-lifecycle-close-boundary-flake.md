# 011 — Lifecycle close-boundary flake: diagnostic determination

Task Identifier: `011-lifecycle-close-boundary-flake`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/lifecycle-flake-diagnosis` (off delivered tip `67fa2e14a3`)
Status: **diagnostic phase only — no fix implemented**

## 1. Symptom

`GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose` fails intermittently inside the full
module suite:

```
java.lang.AssertionError: [stale projection listener after close]
Expecting AtomicInteger(1) to have value: 0 but did not.
    at GraphWorkspaceLifecycleShould.java:331
```

It passed in the pre-change baseline run, failed once in a post-change full-suite run, and passed 5/5 when
run alone. It is unchanged since base `bfaed5611d`, and this repository already carries a skill for it
(`verify-graph-workspace-lifecycle-close-boundary`).

## 2. Question

Is the delivered callback a genuine **post-close stale delivery** (a production per-listener guard gap in
`GraphUpdateCoordinator.publishProjection`), or a **test-barrier artefact** (a legitimate pre-close
delivery that the test's synchronization fails to exclude)?

The distinction decides the task: the first is a production behaviour change, the second is a test-only
repair.

## 3. Method

1. Read the barrier: the first projection listener counts down `firstProjectionEntered`, then blocks on
   `releaseFirstProjection.await(5, TimeUnit.SECONDS)`. The second listener increments
   `secondProjectionCallbacks`, which is asserted to be exactly `0`.
2. Read the production guard.
3. Reproduce by loading the machine (18 detached CPU-burning processes on 22 cores) and running the focused
   test repeatedly with `--rerun-tasks`.
4. Instrument the test temporarily — probe edits were reverted and the tree verified clean — to record, for
   each event: whether the listener-1 wait was released or self-timed-out, whether listener 2 fired, and
   whether close had already reached the coordinator boundary (`boundaryReached`) when it fired.

## 4. Evidence

Ten focused runs under load: **2 passed, 8 failed** (4 at line 309, the coordinator-boundary
`awaitCondition` budget; 4 at line 331, the stale-callback assertion).

Probe result, perfectly correlated across all ten runs:

| Listener-1 wait | Listener 2 fired | `boundaryReached` at fire time | Outcome |
|---|---|---|---|
| **self-timed-out** (`await` returned `false`) × 8 | yes × 8 | **`false` × 8** | failed × 8 |
| released by latch (`true`) × 2 | no | — | passed × 2 |

Every single listener-2 firing recorded `boundaryReached=false` and `listener1Held=true`.

Representative runs:

```
PROBE listener1 await_released=false ns=100688240151464
PROBE listener2 fired ns=100688240754842 boundaryReached=false listener1Held=true

PROBE closed_boundary_reached ns=101144293947492
PROBE listener1 await_released=true  ns=101144294274557      <- passing run
```

## 5. Determination

**Test-barrier artefact. The production per-listener closed guard is sound.**

Three independent findings support this:

1. **The guard is correctly synchronized.** `close()` sets `closed = true` and clears `projectionListeners`
   inside `synchronized (monitor)` (`GraphUpdateCoordinator.java:333-339`), and `publishProjection` both
   snapshots the listener list and re-checks `closed` before each listener under the same monitor
   (`:485-500`). The field is non-volatile, but every access is inside that monitor, so the monitor supplies
   the happens-before edge. A post-close delivery is therefore not reachable.
2. **Observed deliveries were pre-close.** `boundaryReached=false` at every fire time is direct evidence
   that close had not yet reached the coordinator boundary, so those callbacks were legitimate.
3. **The correlation is exact.** Failures occur precisely when the listener-1 wait self-times-out.

### 5.1 Root cause

The probe listener's own bounded wait — `releaseFirstProjection.await(5, TimeUnit.SECONDS)` — creates an
**implicit 5-second deadline on the entire close path**. The latch is only counted down in the test's
`finally` block, which runs *after* two `awaitCondition` calls that each permit 5000 ms. So the listener
always self-releases first: `await_released=false` occurred in 8 of 10 runs and, notably, the barrier never
holds as intended even in passing runs where close happened to win the race.

Once the listener self-releases, the EDT proceeds to listener 2 while `closed` is still `false`, and the
`hasValue(0)` assertion observes a perfectly legal pre-close callback. The test also polls
`execute(viewport(...))` while waiting for the boundary, and each accepted command publishes another
projection, so there is a steady supply of these legitimate deliveries.

Secondary defect: the two `awaitCondition(..., 5000L, ...)` budgets are themselves load-sensitive, which is
why four runs aborted at line 309 before reaching the assertion at all.

## 6. Repair attempt and why the obvious fix is wrong

**Attempted:** remove the bound from the probe listener's wait (hold until the `finally` release) and raise the
two `awaitCondition` budgets to 30 s. Verified not with machine load but with a deterministic in-test slowdown
— the close thread sleeps 8 s before calling `close()`.

**Result: deadlock.** The focused test hung past 600 s. `jstack` of the Gradle test worker showed a clean
lock cycle:

| Thread | State |
|---|---|
| `AWT-EventQueue-0` | blocked in `CountDownLatch.await` (the probe listener), dispatched from `GraphUpdateCoordinator$7.run:501` |
| JUnit test thread | inside `DefaultGraphWorkspaceHandle.execute:77`, **holding** monitor `<0x…e32f0a48>`, waiting for the EDT |
| close thread | `DefaultGraphWorkspaceHandle.close:95`, **waiting** for monitor `<0x…e32f0a48>` held by the test thread |

The test thread can never reach its `finally` release, because it is blocked waiting on an EDT that only that
release could unblock.

### 6.1 The real design flaw

The test's **barrier** and its **liveness poll** are mutually exclusive:

- The poll proves closure by calling `callbackHandle.execute(...)` until it throws `IllegalStateException`.
- `execute` takes the handle monitor and, while the handle is still open, needs the EDT to publish.
- Blocking the EDT inside a projection listener therefore stalls the poll *while the poll holds the handle
  monitor*, which in turn stalls `close()`.

The original 5 s bound on the listener wait was not arbitrary sloppiness — it was what let the EDT drain so the
poll could advance. That is also exactly why the test flakes once close takes longer than 5 s. Any repair that
merely lengthens or removes that bound converts a flake into a hang. **This is a design defect, not a tuning
defect.**

### 6.2 Revised design (proposed, needs approval)

Stop blocking the EDT entirely and assert the property directly rather than manufacturing an in-flight
dispatch:

1. Register a second listener that increments a counter only when `closeReturned` is already `true`.
2. Trigger the projection.
3. `handle.close()` to completion.
4. Set `closeReturned = true`.
5. Drain the EDT deterministically with `SwingUtilities.invokeAndWait(() -> {})` — no sleeps, no polling.
6. Assert the after-close counter is `0`.

This keeps the property under test (no delivery after close), removes every timing assumption, cannot deadlock
because nothing blocks the EDT, and is still test-only. It does drop the deliberately in-flight dispatch
scenario; if that scenario must be retained, it needs a production-level observability seam, which is a
behaviour change and out of scope here.

**State:** the attempted fix was reverted. `git status` is clean on `pm/lifecycle-flake-diagnosis`; no source
file is modified in the committed state, and no synthetic load is running.

## 6b. Superseded first draft of the repair (kept for the record)

1. Remove the bound from the probe listener's wait so it holds until the `finally` release, making the
   barrier actually load-bearing. This is safe against deadlock: in passing runs the coordinator boundary
   was reached *while* listener 1 was still blocked, so the boundary condition does not need the EDT; the
   release happens before `closing.join(...)`.
2. Give the two `awaitCondition` budgets a load-tolerant value, or express the boundary as an event rather
   than a poll.
3. Keep the assertions unchanged. With the barrier fixed, `hasValue(0)` becomes correct rather than
   accidentally satisfied.

Constraints carried from the existing skill: do not change production close ordering, APIs, resources, build
configuration or timeouts for a test-only repair; do not introduce sleeps or a new synchronization seam;
every disposable listener registration must be closed; the latch release must stay in a `finally` before
the close-thread join.

## 7. Out-of-scope observation

Under 18× CPU load, close took longer than 5 s to reach the coordinator boundary. That is a latency
observation about a loaded machine, not a correctness defect, and it is recorded here only because it is
what widens the race window.

## 8. Verification performed

- Probe edits reverted; `git status` clean on `pm/lifecycle-flake-diagnosis`.
- All 18 synthetic load processes terminated (verified zero remaining).
- No production file modified; no test file modified in the committed state.

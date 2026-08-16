# Layout Settle Loop Lifecycle Remediation Design

**Status:** Approved 2026-08-17

## Goal

Make `LayoutSettleLoop` linearize its logical run state and its physical
`FrameStepper` lifecycle so that pause, restart, reset, a newer accepted batch,
and close cannot issue stale worker operations or strand a layout run.

## Scope

The implementation changes only these production and test files:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`

It preserves the public `LayoutSettleLoop` API, Java 8 compatibility,
generation ordering, immutable off-EDT layout computation, EDT-only canvas
publication, listener exception isolation, and the existing coordinator-owned
shutdown policy. It does not change `LayoutWorker`, `GraphUpdateCoordinator`,
`WorkspaceMapCoordinator`, `ProjectionBatcher`, build files, or Task 19/21
prerequisite files.

## Problem Model

`monitor` currently protects logical state but releases before every
`FrameStepper` operation. `WorkerStepper` synchronizes individual calls, but
cannot serialize a logical transition such as reset followed by initial submit.
That permits a newer accepted start to submit to a worker that a concurrent
reset is about to close. It also permits the loop to clear its logical paused
flag while the underlying `LayoutWorker` remains physically paused.

The returned `CompletableFuture` is also completed while `monitor` is held.
A non-async dependent may synchronously reenter `start`, `reset`, or `close`
and observe or alter partially committed state.

## Lifecycle Architecture

`LayoutSettleLoop` owns two coordinated domains:

1. `monitor` owns logical state: `closed`, current run identity, accepted
   generation, pause intent, frame/publication claims, and terminal state.
2. The existing daemon continuation executor owns every physical
   `FrameStepper` invocation and all completion handling. It is the sole
   lifecycle lane; no second executor is introduced.

Every logical control transition increments a monotonic control revision. A
queued lifecycle command captures both its run token and its control revision.
Immediately before each physical operation it verifies that the loop remains
open, the run remains current where applicable, the token matches, the control
revision matches, and the desired pause mode permits the operation. Reset
performs the same verification again after worker recreation and before its
initial submit.

This uses one ordering domain for worker commands and frame completion work.
A blocking `LayoutWorker.close()` can occupy the continuation lane during reset
or close, but never blocks an EDT or other caller. Logical cancellation occurs
before that work begins, so blocked physical teardown cannot publish or submit
for an obsolete run.

## Transition Rules

### Start

`start()` validates its inputs and, under `monitor`, rejects non-newer accepted
generations, invalidates the prior logical run, creates a new run token,
records running as the desired mode, claims its initial frame, and queues a
start reconciliation command. The command revalidates the token and revision,
explicitly invokes `FrameStepper.restart()` to reconcile any sticky physical
pause, revalidates again, and only then invokes `FrameStepper.submit()`.

If a newer control transition supersedes the queued command, it must not call
the stepper. It releases the claimed frame for the superseded run and, when the
same current run is paused, sets `restartRequested` so a later restart can
claim exactly one frame for that current run.

### Pause and Restart

`pause()` synchronously records logical pause intent under `monitor`, marks an
in-flight frame or publication for discard, increments the control revision,
and queues the physical pause command. `restart()` records running intent,
increments the revision, and queues a restart reconciliation command. A restart
may claim a frame only when the current run has neither a frame nor publication
in flight.

Queued pause and restart commands must validate their captured revision. Thus a
later opposite command on the same run prevents an obsolete physical command
from changing the worker after the newer intent has linearized.

### Reset

`reset()` creates a replacement logical run for the current accepted
projection under `monitor`, invalidates the old run, increments both the run
token and control revision, claims the initial frame, and queues reset
reconciliation. The caller does not invoke `FrameStepper.reset()` directly.

The queued reset first verifies that its run and revision are still current,
then calls `FrameStepper.reset()` on the continuation executor. It revalidates
after reset before invoking `FrameStepper.restart()` and `FrameStepper.submit()`.
If a newer start, reset, pause, or close won while reset was blocked, reset
performs no follow-up submission. Later current lifecycle commands own the
replacement worker state.

### Submit and Step

`submit()` and `step()` are lifecycle commands, not direct worker calls. They
revalidate state immediately before invoking the stepper. A command skipped
because it is stale, paused, or closed must clear its logical frame claim; it
must never leave `frameInFlight` permanently true. Completion callbacks remain
on the same continuation executor and retain the existing token checks before
off-EDT geometry and EDT publication.

### Completion

Logical terminal transitions mark a run terminal under `monitor`, detach it
from current state as required, and collect its result future for completion
after the monitor is released. `Run` keeps explicit terminal state so that a
brief notification gap cannot be treated as a live run. No external
`CompletableFuture.complete(...)` call occurs while `monitor` is held.

### Close

`close()` first marks the loop logically closed under `monitor`, invalidates
the current run, increments the control revision, and prevents every later
non-close command from reaching the stepper. It queues one terminal physical
close command behind any already-running lifecycle command. The close command
owns `FrameStepper.close()` and then shuts down the continuation executor.

Calls from the EDT or lifecycle lane return after logical close; their queued
physical shutdown completes asynchronously, and a shutdown failure cannot be
reported to that caller. Calls from ordinary external threads wait for the
queued close command and rethrow its `RuntimeException`, preserving existing
synchronous failure reporting. The test seam verifies this close-completion
policy, including no post-close worker operation and no replacement worker
leak.

## Required Invariants

- Only the numerically latest accepted generation may remain the current run.
- A physical stepper operation is issued only from the continuation lifecycle
  lane and only when its token and control revision are current.
- A new accepted start reconciles a previously paused worker to running before
  its first submission.
- At most one frame or publication claim is active for a current run.
- A skipped lifecycle command releases its claim or records a valid deferred
  restart; it cannot strand settlement.
- A paused, superseded, reset, or closed run cannot publish canvas state.
- Reset and close never permit an obsolete operation to submit work to a worker
  that is being replaced or has been closed.
- Completion dependents cannot reenter while a logical state transition is
  partially committed.
- Listener failures remain isolated and do not terminate later settlement.

## Deterministic Test Design

`LayoutSettleLoopShould` gains a package-private manual dispatcher seam for the
continuation lifecycle path. Tests drain named queued commands deliberately;
they use futures and explicit gates, never sleeps or timing assumptions. A
recording `FrameStepper` models sticky pause, blocks reset or close on demand,
records command order, and fails on concurrent invocation.

The regression set covers:

1. Pause a worker, accept a newer generation, and prove the worker restarts
   before a non-idle frame can advance to idle.
2. Block reset, accept a newer generation, and prove its submit occurs only
   after reset has completed or the reset was superseded before physical work.
3. Queue two resets and prove only the final current reset can recreate and
   submit a worker.
4. Close while reset is queued or blocked and prove no post-close reset,
   replacement-worker leak, submission, or canvas callback occurs.
5. Supersede or pause a run before its queued submit or step and prove the
   abandoned frame claim is released and later restart settles exactly once.
6. Attach a non-async completion dependent that invokes `start`, `reset`, or
   `close`, then prove the outer transition cannot clobber that reentrant
   state or issue stale worker work.
7. Retain existing frame publication, failed-frame, generation rejection,
   listener-isolation, and queued-EDT pause tests through the controlled
   dispatcher so the serialization change preserves their observable behavior.

After green, mutation checks remove the start-time worker restart and bypass
the reset lifecycle barrier independently. Their corresponding deterministic
regressions must fail, after which exact production bytes are restored before
focused and full graph-module verification.

## Alternatives Rejected

Holding `monitor` across stepper calls is rejected because `reset()` and
`close()` may block in `LayoutWorker.close()`, and completion callbacks can
reenter loop control methods. Relying only on `WorkerStepper` synchronization
is rejected because it serializes individual calls but not their logical
ownership. A separate lifecycle executor is rejected for this remediation
because it creates a second ordering domain between worker commands and frame
completion handling; the existing serial continuation executor can provide one
verifiable lifecycle order with a narrower change.

## Verification

The implementation must demonstrate red-before-green behavior for each new
regression, execute the two one-mechanism mutants, restore exact source bytes,
run the focused `LayoutSettleLoopShould` suite, run the relevant `LayoutWorker`
controls, then run `:freeplane_plugin_graph:test` with zero failures and zero
errors. The deterministic SDD final review covers the full range from
`9248c6e227bb82fab8e6139f46db37b62174309f` through the successor branch HEAD.

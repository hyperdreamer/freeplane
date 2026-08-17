# Deferred Restart Claim Recovery Design

**Status:** User-approved design

**Decision:** Repair the `LayoutSettleLoop` pause/restart race with a private, post-restart reconciliation path. The reconciliation owns one replacement claim only after the lifecycle worker has physically restarted, then dispatches `submit` or `step` according to the run's `requestSubmitted` state.

## Context

The terminal final review of `headless-resource-scope-clean-suite-remediation-v1` found F-3 in `LayoutSettleLoop.queueRestart()`. A queued start, reset, or step command can own `frameInFlight` while waiting on the serialized lifecycle dispatcher. If a caller invokes `pause()` and then `restart()` before that command runs, `restart()` records `restartRequested` but cannot claim work because the stale command still owns the frame.

When the stale command reaches `claimIsCurrentAndRunning()`, its revision is obsolete and `releaseClaimLocked()` clears `frameInFlight`. The queued restart command physically calls `worker.restart()`, but its captured `shouldStep` value remains false and it schedules no replacement operation. No frame or publication completion remains to invoke `resumeAfterDiscardLocked()`, so the current run's result can remain incomplete indefinitely.

The predecessor final report already verified that the accepted F-1 repair correctly submits an unsubmitted request after the older pause/restart ordering. F-3 is separate: it requires restart to occur before the queued lifecycle command drains.

## Requirements

1. A final running pause/restart intent must not strand a live run after a stale queued start, reset, or step releases its frame claim.
2. The worker must physically restart before any recovery submit or step is scheduled.
3. Recovery must create exactly one frame claim and dispatch exactly one operation for that claim.
4. A run whose request has not yet been submitted must recover with `submit`; a run that already submitted its request must recover with `step`.
5. An active frame or publication must retain its existing completion-driven recovery path; the fix must not create duplicate work.
6. Existing stale-revision, pause, close, reset, and public API behavior must remain unchanged. A physical restart failure must not trigger an extra recovery operation.
7. The repair must be proven by deterministic tests for queued start, reset, and step orderings without wall-clock scheduling assumptions.

## Design

### State Ownership

`LayoutSettleLoop` continues to serialize physical worker operations through `LifecycleDispatcher` and protects run ownership fields with `monitor`. `restartRequested` remains the record that the final requested state is running after a pause. `frameInFlight`, `publicationInFlight`, `claimRevision`, and `requestSubmitted` remain private fields of `Run`; no public type or method changes.

`queueRestart()` remains the sole lifecycle command that calls `worker.restart()`. The fix does not schedule work from `releaseClaimLocked()`, because that method runs under `monitor`, is used by multiple stale-command paths, and has no guarantee that the worker has restarted.

### Post-Restart Reconciliation

After `queueRestart()` verifies the run/revision is current and physically restarts the worker, it chooses one of two paths:

- When the `restart()` call already claimed a frame (`shouldStep` is true), preserve the current path and dispatch that existing claim.
- When `shouldStep` was false, atomically inspect the current run after the physical restart. If `restartRequested` is set, the loop is running, and neither a frame nor publication remains in flight, consume the restart request, claim one frame at the current control revision, and select the operation from `requestSubmitted`.

The continuation selection is private and explicit: `requestSubmitted == false` selects `submit`; `requestSubmitted == true` selects `step`. The selected operation runs outside `monitor` through the existing `submitClaimed()` or `stepClaimed()` validation paths, preserving stale-revision release behavior.

If a frame or publication is still in flight, reconciliation does nothing. Its existing completion path observes `restartRequested` and calls `resumeAfterDiscardLocked()` when it releases the last active operation. This preserves the one-claim invariant and prevents duplicate submissions or steps.

If the loop is no longer current/running after physical restart, the existing stale-command release behavior applies. Reconciliation occurs only after a successful physical restart. A restart failure does not dispatch a recovery submit or step; the existing error path for an already-claimed restart remains unchanged.

## Regression Test Design

All regressions use the existing `ManualLifecycleDispatcher`, `RecordingStepper`, `ImmediateEdt`, and completion helpers in `LayoutSettleLoopShould`. The tests control dispatcher order directly and assert observable completion and worker operations rather than sleeping or polling time.

| Scenario | Controlled ordering | Required result |
| --- | --- | --- |
| Queued start | Call `start`, then `pause` and `restart` before draining the lifecycle queue. | The original completion settles, exactly one `submit` occurs after restart, and no `step` occurs before the first request has been submitted. |
| Queued reset | Let an initial run settle, call `reset`, then `pause` and `restart` before the reset command drains. | The replacement run emits its state and performs one replacement `submit`, not a step. |
| Queued step | Drive an initial non-idle frame until the follow-up step is queued, then call `pause` and `restart` before that step drains. | The run advances and completes through exactly one replacement `step`, with no extra submit. |

Before production code changes, each test must demonstrate the defect by failing because its expected completion/state or replacement operation is absent. The implementation then makes the tests pass without changing their ordering or assertions.

## Scope and Compatibility

Files allowed for the repair:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`

The successor deterministic run will carry F-1 and F-2 as already-fixed predecessor findings and F-3 as the target finding. Its final reviewer must inspect the entire branch from merge base `9248c6e227bb82fab8e6139f46db37b62174309f`, not only this remediation commit. The terminal predecessor run and its evidence remain immutable.

No coordinator, adapter fixture, build, dependency, serialization, or public API changes are part of this repair.

## Verification

The implementation plan will require:

1. A focused red/green cycle for all three deterministic regressions.
2. A mutation proof that restores the prior missing deferred-restart reconciliation and demonstrates that the new regression suite fails for the expected reason.
3. Focused `LayoutSettleLoopShould` verification followed by `gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks` under Java `21.0.8-zulu`.
4. Independent task review and a Frontier whole-branch final review that reconciles F-1, F-2, and F-3.

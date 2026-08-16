# Asynchronous Reset Observability Recovery Design

## Status

Approved design direction: preserve nonblocking reset lifecycle semantics and
observe reset completion through the existing canvas-state callback path.

## Goal

Repair the `LayoutSettleLoop` lifecycle remediation from a clean baseline while
keeping `GraphUpdateCoordinator.resetLayout()` nonblocking and making its
compatibility regression assert observable reset completion rather than an
obsolete synchronous worker-call timing assumption.

## Context

The interrupted v6 implementation moved every physical `FrameStepper` operation
to a serialized lifecycle lane. Its focused coordinator test exposed an older
assumption: `GraphUpdateCoordinatorShould.resetsAnIdleRunWithANewCurrentProjectionSubmission`
expects `FrameStepper.reset()` and the follow-up submit to have executed before
`resetLayout()` returns.

That assumption conflicts with the approved lifecycle architecture. A reset must
first atomically install a replacement logical run under `LayoutSettleLoop`'s
monitor, then queue physical reset, restart, and submit work. A blocked physical
operation must not stall an EDT or ordinary caller.

The v6 worktree remains immutable diagnostic evidence. Its uncommitted diff is
not a source of truth and will not be copied into this recovery branch.

## Decision

`GraphUpdateCoordinator.resetLayout()` retains its current `void` API and its
nonblocking behavior. Returning from it means that the reset intent has been
accepted by `LayoutSettleLoop` and physical lifecycle work has been queued; it
does not mean that `FrameStepper.reset()` or `FrameStepper.submit()` has already
run.

The reset completion observation boundary is the existing
`CanvasStateListener`. A reset-generated state for the current accepted
projection proves that the queued reset, restart, submit, frame handling, and
EDT publication have completed in the correct order.

## Lifecycle Design

`LayoutSettleLoop.reset()` will:

1. Under `monitor`, reject a closed loop or absent current run, invalidate the
   old logical run, create a replacement run for the current accepted
   projection, increment token and control revision, and claim its initial
   frame.
2. Queue one reset reconciliation command on the loop-owned lifecycle lane.
3. Have that command revalidate the replacement run and revision before
   `FrameStepper.reset()`, then revalidate again before `restart()` and
   `submit()`.
4. Skip stale reset follow-up work after a newer start, newer reset, pause, or
   close; release any stale claim rather than stranding settlement.

`start()`, `pause()`, `restart()`, frame completion, and `close()` continue to
share that same lane. No caller invokes a `FrameStepper` method directly after
leaving `monitor`.

## Coordinator Compatibility Test

Only `GraphUpdateCoordinatorShould` changes outside the loop files, and only as
a test adaptation. The existing reset test will register a listener that retains
the initial publication and completes a second future for the reset-generated
current-generation state. It will:

1. Accept generation 10 and await the first canvas state.
2. Call `coordinator.resetLayout()` without asserting immediate worker counts.
3. Await the second canvas state through the normal listener path.
4. Assert one physical reset, two submissions, and that the second request uses
   `coordinator.currentProjection()`.

The test uses futures and existing deterministic test helpers. It does not use
sleep, polling, or a timing-dependent assertion after `resetLayout()` returns.

## Scope

Implementation may modify only:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`

`GraphUpdateCoordinator.java`, `LayoutWorker.java`, Task 19/21 files, build
configuration, and the preserved v6 worktree are out of scope. Public
`LayoutSettleLoop` signatures remain unchanged.

## Verification

The recovery repeats test-driven red and green phases from the clean baseline.
It runs focused lifecycle, coordinator, and neighboring graph controls, then the
complete `freeplane_plugin_graph` test suite with JUnit XML aggregation requiring
zero failures and zero errors.

Two one-mechanism mutants are mandatory: remove start-time physical restart, and
bypass the reset lifecycle barrier. Each corresponding deterministic regression
must fail. The source is restored byte-for-byte after each mutation.

## Acceptance Criteria

- Physical `FrameStepper` operations are serialized on the lifecycle lane.
- A reset caller is not blocked by a held physical reset.
- No newer start, reset, pause, or close permits stale reset follow-up work.
- The coordinator reset regression observes the reset result through a current
  canvas state rather than synchronous stepper timing.
- Existing generation ordering, failure retention, listener isolation,
  EDT-only canvas delivery, and close-caller contracts remain covered.
- No file outside the stated allowlist changes.

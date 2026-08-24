# Graph Workspace Lifecycle Close-Boundary Remediation Design

**Date:** 2026-08-24

## Problem

After the clean-suite timeout remediation was fast-forwarded into `main` at
`69ab99b522cb09a7572e1877b470a02b71a35e8f`, a full `gradle test` run
completed 708 of 709 tests and failed
`GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose`. The failure
reported that the second projection listener received a stale callback.

The implementation already guards every projection listener delivery with the
`GraphUpdateCoordinator.closed` state. The integration probe, however, starts
`handle.close()` on a background thread, waits only until the handle rejects
new commands, and immediately releases its deliberately blocked first
projection listener. Handle rejection happens at the beginning of session
shutdown. It does not prove that `GraphUpdateCoordinator.close()` has cleared
listeners and made its per-listener close guard active. The EDT can therefore
resume and deliver the queued second listener before the closing thread reaches
coordinator teardown. The test observes scheduling rather than the accepted
post-close contract.

## Contract

The accepted callback boundary is `GraphUpdateCoordinator`'s logical closed
state. `GraphWorkspaceHandle` rejects commands once handle closing begins, but
that early state does not itself suppress a projection callback already queued
on the EDT. The coordinator sets its own closed state synchronously when its
close path begins and rechecks that state before every listener invocation.
A projection, canvas, or status callback must not be delivered after that
coordinator boundary, including an observer already captured in a queued EDT
batch. `GraphWorkspaceHandle.close()` completes only after the later resource
and EDT view-disposal work; no callback after the coordinator boundary
necessarily proves the user-selected no-callback-after-close-completion
contract without incorrectly treating handle-closing start as completion.

## Scope

Only this test fixture may change:

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java`

No production source, API, resource fixture, build configuration, test timeout,
or unrelated lifecycle test changes are authorized. The remedy corrects a
race-prone acceptance probe; it does not alter Graph Workspace shutdown
semantics.

## Design

### Use the Existing Teardown Signal

The probe already captures a `ResourceBaseline` before opening the workspace.
After it adds and projects a real map, the map lifecycle listener count must be
strictly above `baseline.mapLifecycleListeners`; this establishes that the
subsequent teardown wait cannot succeed vacuously.

The probe retains its first listener, which blocks on `releaseFirstProjection`,
and its second listener, which increments a stale-delivery counter. It starts
`handle.close()` off the EDT and first waits for the existing handle-closing
rejection boundary.

Before releasing the first listener, it then waits, with the existing bounded
condition helper, until `freeplane.mapLifecycleListenerCount()` equals the
recorded baseline. `GraphUpdateCoordinator.close()` marks itself closed and
clears its listeners before it closes its owned map coordinator; releasing the
workspace map lease detaches the observed lifecycle listener. Thus a return to
that listener baseline proves the coordinator has already entered its logical
closed state and begun owned-map teardown, while the closing thread is still
blocked on EDT view disposal.

Only after that condition is met may the test release the first listener and
join the close thread. The existing second-listener zero assertion then proves
that the pre-captured callback was stopped by the coordinator close boundary,
not by favorable scheduling. The existing source-map mutation, post-close
resource baseline, callback-counter, and closed-handle assertions remain.

### Preserve Failure Cleanup

The test must release `releaseFirstProjection` from a `finally` path covering
the close-boundary wait and close-thread join. A timeout or assertion failure
must not leave the EDT blocked in the first listener or leave the background
close thread alive to contaminate later tests. The ordinary success path still
checks that the close thread terminated and did not report a failure. Bounded
latches and joins remain the only synchronization mechanism; sleeps and
retry-on-failure loops are forbidden.

## Falsifiability and Verification

1. Confirm the inherited test has the known race-prone ordering: command
   rejection is awaited before release, with no coordinator-teardown barrier.
2. Implement the test-only barrier and run the focused lifecycle class.
3. In a disposable working state, temporarily remove only the `closed` check
   immediately before each projection-listener invocation in
   `GraphUpdateCoordinator.publishProjection`. The revised probe must fail
   because the queued second listener is called after the first listener is
   released. Restore the exact production source before every green gate.
4. Run the focused lifecycle class repeatedly with fresh task execution, then
   run `:freeplane_plugin_graph:clean :freeplane_plugin_graph:check
   :freeplane_plugin_graph:test :freeplane_plugin_graph:build` and the full
   repository `test` suite using the required Zulu Java 21 runtime.
5. Require zero test failures/errors, a clean worktree apart from the planned
   commit, and `git diff --check` success.

## Non-Goals

- Enforcing a new production rule that suppresses callbacks at the instant
  `GraphWorkspaceHandle.close()` starts.
- Changing `DefaultGraphWorkspaceController`, `DefaultGraphWorkspaceHandle`,
  `GraphUpdateCoordinator`, or their close ordering.
- Hiding the failure with a delay, retry, swallowed assertion, or larger
  timeout.
- Revisiting the completed map-version-cache remediation or its test fixture
  scope.

## Success Criteria

The lifecycle probe deterministically distinguishes handle-closing from the
coordinator's logical close state, proves no queued projection listener can run
after that state is active, always releases its blocking test callback during
failure cleanup, and passes the focused, graph-plugin, and full repository test
gates without production changes.

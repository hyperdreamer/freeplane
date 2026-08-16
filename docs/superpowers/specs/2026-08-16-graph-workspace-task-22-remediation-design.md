# Graph Workspace Task 22 Acquisition Barrier Remediation Design

- Date: 2026-08-16
- Parent task: Graph Workspace Task 22, Batch changes and capture projection input
- Predecessor run: `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan`, revision 35 `FINAL_BLOCKED`
- Starting implementation: `369077d8bf049ad0bf528a9fc9a30433ad0be68e`
- Original merge base: `afba8436462753a38aef73f91bf21ba6715e8460`

## Goal

Resolve the remaining load-bearing F-3 lifecycle defect without reopening the terminal predecessor run or changing the already-approved snapshot and batching contracts. A same-ID map acquisition that is loading must remain a barrier even when its current document registration is temporarily removed or inactive; the latest later active registration must wait for settlement and then be acquired exactly once.

## Root Cause

`MapLeaseManager.acquire` retains a loading entry keyed by `MapReferenceId` and rejects a changed path until that entry settles. The current `WorkspaceMapCoordinator` stores `acquisitionInFlight` and `deferredAcquisition` on the current `Registration`. A remove/deactivate document event replaces or drops that registration, so the marker disappears. A later active registration with the same ID is then acquired immediately, races the manager's rebind guard, becomes `UNREADABLE`, and is never retried by the old completion.

## Scope

The new implementation allowlist is exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java`

No public API, adapter, lease manager, projection value, build file, or unrelated test changes are permitted. The predecessor's seven-file Task 22 implementation remains inherited and unchanged except for the two paths above.

The new run carries these prior findings:

- F-1: fixed; snapshot failure remains capture-local and later captures retry.
- F-2: fixed; lifecycle and deterministic ordering coverage is present.
- F-3: open residual; this design resolves it.
- F-4: fixed; accepted callback dispatch is synchronized with batcher close.

## Architecture

Add one coordinator-owned acquisition barrier map keyed by `MapReferenceId`. Each barrier identifies the source registration and acquisition generation that owns the still-unsettled completion, plus the latest registration state that should receive a retry once the source settles.

The barrier is created atomically with the call to the injected lease acquirer. Document installation updates the barrier's latest registration whenever the same ID is changed, removed, deactivated, or reactivated. A later active registration is marked loading but is not acquired while the barrier exists. When the source completion reaches the EDT, the coordinator claims and closes any stale lease, removes the barrier, and retries only the current latest active registration. If no active registration remains, the barrier is simply cleared; a later registration arriving after settlement acquires normally. Close clears barriers and suppresses all queued retries.

The barrier map is the only active path for this protocol. Registration-local deferred/in-flight flags are removed or made obsolete rather than retained as a competing fallback. Existing generation checks, pending-completion ownership, stale lease closure, unchanged-registration reuse, and close guards remain in force.

## State Transitions

1. Initial active registration starts acquisition and creates a barrier whose latest registration is itself.
2. An identity change, removal, or deactivation detaches the current lease and updates the barrier latest pointer; no replacement acquire is started.
3. Repeated changes update only the latest pointer. No stale intermediate registration can be retried.
4. Re-activation before settlement updates the latest pointer to the new active registration and remains deferred.
5. Source completion claims its pending lease, closes it outside the monitor, clears the barrier, and starts the latest active registration on the EDT.
6. Completion after close, removal without reactivation, or a stale duplicate has no retry side effect.
7. A replacement that arrives after the barrier has settled follows the ordinary acquisition path.

## Invariants

- At most one same-ID acquisition is in flight through the coordinator.
- The coordinator never asks `MapLeaseManager` to rebind a changed path while its prior same-ID load is unsettled.
- Only the latest active registration can be retried.
- Every returned stale lease is closed exactly once, including invalid and exceptional completions.
- Inactive, removed, and closed states cannot be resurrected by a stale completion.
- All state transitions and lease acquisition calls remain EDT-scoped as in Task 22.
- Capture never closes or mutates leases and continues to retry transient snapshot failures locally.

## Testing Design

Use the existing injected `LeaseAcquirer`, deterministic `TestEdt`, immutable workspace fixtures, and fake leases in `WorkspaceMapCoordinatorShould`.

The required red-to-green regressions are:

1. A pending initial load followed by removal, then re-addition of the same ID with a changed URI before the old completion. Assert only the initial acquisition occurs before settlement; after settlement assert exactly one replacement acquisition, the stale lease is closed, the replacement is `AVAILABLE`, and capture includes it.
2. The same sequence through an inactive registration, followed by reactivation with a changed URI.
3. Repeated active replacements before settlement. Assert only the final registration is acquired after the old completion.
4. Existing direct active replacement, stale completion, close, ordering, and transient snapshot retry tests remain green.

The red phase must run each new regression against the inherited implementation and observe the changed replacement acquisition occurring too early or the replacement remaining unreadable. No production edit precedes that evidence.

## Alternatives Considered

### Keep the marker on `Registration`

This is the inherited approach and fails whenever the document removes or replaces the registration object. It cannot represent an in-flight manager entry with no current active registration.

### Cancel the old manager load on document removal

The coordinator has no supported cancellation contract for `MapLeaseManager.acquire`, and synthesizing one would change adapter ownership and introduce a second lifecycle protocol. It also would not safely handle completions already queued on the EDT.

### Coordinator-level barrier keyed by map ID

This is the selected approach. It matches the manager's actual identity key, keeps the public APIs unchanged, handles arbitrary document transitions, and gives the stale completion one authoritative place to select the latest state.

## Verification

The fresh run must prove the named red tests fail for the inherited mechanism, then pass the focused coordinator/batcher suite and the full graph-module suite. It must inspect XML counts, `git diff --check`, exact two-file implementation scope relative to the inherited Task 22 HEAD, and a clean worktree. A fresh Frontier final reviewer must inspect the original merge-base-to-new-HEAD range and reconcile F-1 through F-4.

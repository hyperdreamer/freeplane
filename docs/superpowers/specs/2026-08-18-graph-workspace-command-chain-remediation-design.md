# Graph Workspace Command Chain Remediation Design

**Date:** 2026-08-18
**Task:** Batch F, backlog Tasks 30-32
**Carried review findings:** F-1 and F-2 from the blocked Task 30 SDD run

## Goal

Complete the Graph Workspace command chain by repairing Task 30's map Retry ownership and router test coverage, then implement Tasks 31 and 32 in order. Retry must reacquire the lease owned by the existing `WorkspaceMapCoordinator` registration without changing the registration UUID or stored URI.

## Scope

The successor Task 30 remediation may modify these existing coordinator paths in addition to the original six Task 30 paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java`

Tasks 31 and 32 retain their original exact file allowlists. No `freeplane_api`, module export, launcher, or unrelated core changes are included.

## Design

`GraphCommandRouter` receives the concrete `WorkspaceMapCoordinator` as its map-retry dependency. The router remains responsible for finding the immutable current `MapReference`, rejecting missing and inactive records, and translating synchronous coordinator failures into the existing command result keys. It no longer accepts an unconstrained callback capable of bypassing coordinator-owned lease state.

`WorkspaceMapCoordinator` exposes one public operation:

```java
public void retry(MapReference reference);
```

The operation is a deep module seam. Callers supply the exact immutable registration record; the coordinator validates that its current registration has the same ID, active state, and record identity, then owns all EDT handoff, lease invalidation, acquisition generation, and completion handling.

## Retry flow

1. The router reads the current workspace document and looks up the requested ID.
2. Missing or inactive records are rejected before the coordinator is called.
3. The coordinator executes the retry state transition on the EDT. A mismatched, removed, inactive, closed, or otherwise invalid registration fails synchronously.
4. Under the coordinator monitor, the current registration's lease is detached and its acquisition generation is advanced. Its availability becomes `LOADING`; the `MapReference` itself is retained unchanged.
5. The old lease is closed outside the monitor. If no same-ID acquisition is pending, a new acquisition starts through the existing `LeaseAcquirer` and completion barrier.
6. If an acquisition for that registration is already pending, the registration is recorded as the barrier's latest retry target. The old completion is treated as stale and exactly one replacement acquisition starts after settlement. The retry never races a second same-ID load against the first.
7. A valid replacement lease is installed only on the EDT after generation, registration, active-state, and map-ID checks. Invalid or failed completions are closed and leave the registration `UNREADABLE` when it is still current.

The retry operation is asynchronous in its lease acquisition but synchronous in its acceptance: returning normally means the replacement attempt was accepted. The router returns the existing applied or rejected command result immediately; later acquisition failure is represented by the coordinator's normal availability state and capture behavior.

## Test design

`GraphCommandRouterShould` will capture and assert exact payloads rather than only invocation counts. Coverage includes:

- every session command and its exact target service;
- active Retry passes the unchanged ID and URI record to the concrete coordinator;
- inactive and missing Retry never call the coordinator and return explicit reasons;
- same-map, cross-map, and self connector requests preserve exact source, target, and direction payloads and preserve delegated rejection results;
- transient source-command rejection does not cause duplicate execution or implicit save;
- workspace/map undo, display, viewport, save, layout, navigation, purge, and contributor payloads;
- Save As reservation-before-write, commit-after-published-identity, failure release, and protection of another live target.

`WorkspaceMapCoordinatorShould` will add a deterministic retry test with an initial pending acquisition. It will issue Retry using the same `MapReference`, complete the old future with a stale lease, assert that exactly one second acquisition uses the same immutable reference, assert that the stale lease is closed, and capture a successful projection using the replacement lease. It will also cover synchronous rejection for a stale registration and preservation of UUID/URI.

The named concurrency mutant removes the retry-triggered acquisition or generation invalidation. The pending-retry test must fail under that mutation; the production SHA is restored and the green suite rerun before staging.

## Tasks 31 and 32

Task 31 continues to implement `DefaultPurgeCommandHandler` with generation, pending-change, operational-state, EDT, and undo revalidation. Task 32 continues to implement `ContributorDeletionPlan` and `DefaultContributorDeletionHandler` with exact descriptor validation, owner-local undo, prevalidation, and rollback compensation. Their public interfaces and exact allowlists remain unchanged from Batch F.

## Verification gates

The successor run uses the deterministic SDD controller. Task 30 remediation must pass independent spec and quality review before Task 31 dispatch. Each later task receives the same implementer/reviewer gate. The final Frontier review covers the successor branch and confirms that carried findings F-1 and F-2 are resolved, all task allowlists are exact, and the worktree is clean.

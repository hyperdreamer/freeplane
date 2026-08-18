# Graph Workspace Final Remediation Design

- Date: 2026-08-18
- Status: Approved for autonomous successor recovery
- Parent branch HEAD: `af034e5d9bd1c6a58be81ec245835fbff35e1ec8`
- Parent SDD run: terminal `FINAL_BLOCKED` after final review

## Context

The Batch F Task 32 recovery implementation passed its task-level review loop and resolved the original contributor-deletion findings. The whole-branch Frontier review then reproduced two additional load-bearing defects:

- `FINAL-F2`: mixed native/workspace deletion compensates an applied purge with generic `GraphWorkspaceStore.undo()`. `WorkspaceHistory` is global and undo removes its current head, so an unrelated applied workspace command can be consumed instead of the purge. The existing five-file Task 32 allowlist has no exact-history seam.
- `FINAL-F4`: `LayoutSettleLoop.fail()` publishes a failed frame as idle, and `finishPublication()` terminalizes the run. `restart()` requires a live run, so the command route can report Restart Layout as applied while no replacement submit occurs.

The parent run and its digest-pinned plan remain immutable evidence. This successor starts from the parent HEAD in a new worktree and carries both findings into a fresh deterministic run.

## Goals

1. Make cross-resource purge compensation conditional on the exact workspace mutation that published it. A concurrent or interposed workspace command must never be undone as compensation for contributor deletion.
2. Keep a failed layout run restartable, recreate the failed worker engine through the existing `LayoutWorker.restart()` contract, and resubmit the current immutable request before publishing a recovered frame.
3. Preserve existing public command behavior, Java 8 compatibility, EDT/lifecycle serialization, workspace history semantics, and the resolved Task 32 native transaction and validation behavior.

## Non-Goals

- No new graph command types or public API exports.
- No change to native connector ownership, descriptor matching, map transaction grouping, or the already-resolved contributor findings except for the exact workspace compensation call site.
- No changes to GraphStream, `LayoutWorker`, projection, geometry, translations, persistence schema, or unrelated task code.
- No generic retry that guesses which workspace history entry to undo.

## Task 1 Design: Exact Workspace Compensation

### Chosen seam

`GraphWorkspaceStore` will expose a package-owned compensation-capable execution result for callers that need to coordinate another resource. The normal `execute(WorkspaceCommand)` API remains unchanged for all existing callers. The new result is a small deep interface that owns the history token and the conditional restore operation inside the store monitor; `DefaultContributorDeletionHandler` never receives or mutates `WorkspaceDocument` history state directly.

Conceptually:

```java
public WorkspaceMutation executeWithCompensation(WorkspaceCommand command);

public static final class WorkspaceMutation {
    public GraphCommandResult result();
    public GraphCommandResult compensateIfCurrent();
}
```

The exact nested naming may follow local conventions, but these invariants are fixed:

- `executeWithCompensation` applies exactly one supplied `WorkspaceCommand` through the existing `WorkspaceHistory` path.
- An applied mutation captures an opaque history token identifying the entry object, a monotonic store/history publication revision, the before/after documents, the redo-stack identity and contents, the workspace file identity, the dirty/debounce envelope, and the persisted bytes/digests before and after publication.
- `compensateIfCurrent()` executes under the store monitor and succeeds only when the exact entry object, publication revision, redo identity, current file identity, current document, and expected persisted bytes still match. Equal documents alone are insufficient: command -> undo, undo -> redo, and other ABA sequences are conflicts.
- If another command, undo, redo, save, autosave, save-as, or document replacement has interposed, compensation either restores the captured before bytes synchronously after verifying the current bytes are exactly the captured after bytes, or returns a deterministic rejected conflict/incomplete result without changing history. It never calls generic `undo()` and never reports recovery complete after an unverified disk state.
- Successful compensation removes only the target history entry, restores the exact prior redo stack and dirty/debounce envelope, verifies any restored persisted bytes, and publishes the appropriate document-changed event. Repeated calls are idempotent after success and retryable after a transient write failure.
- Read-only, rejected, and no-op commands return a result with no compensatable token.

`WorkspaceHistory` will replace the unqualified document-only undo entries with internal identity-bearing entries and a monotonic state revision while preserving its existing public `execute`, `undo`, `redo`, `canUndo`, `canRedo`, and `clear` behavior. A conditional compensation method will check entry identity, revision, redo identity, and current document before popping only the matching entry. `GraphWorkspaceStore` will include persistence/save generation and file-byte checks so a clean autosave or save-as cannot make an in-memory-only compensation claim. Existing undo/redo tests must remain green.

`DefaultContributorDeletionHandler` will use `executeWithCompensation(WorkspaceCommands.purgeRelationships(...))` for the mixed path. Its `PendingRecovery` will retain the mutation handle when native recovery or workspace compensation is incomplete. Both initial failure handling and later retries use the same deterministic workspace-first, then native order; recovery attempts workspace compensation through the exact mutation handle, retains the handle on a conflict, and clears it only after both resources are proven restored. The pending gate applies only to work initiated by this handler, not unrelated workspace commands. The existing one-purge success path and `undo_incomplete` metadata remain unchanged.

### Task 1 verification model

- Real `WorkspaceHistory` tests prove an old token cannot undo an interposed command, an interposed command followed by undo/redo cannot satisfy an ABA predicate, and a matching token compensates exactly once while restoring redo.
- Real `GraphWorkspaceStore` tests prove conditional compensation restores the purge, refuses after an interposed command, handles clean/dirty autosave states, rejects or safely restores a save-as/document replacement, and verifies reopened file bytes.
- Handler tests use a real or deterministic store seam to model native commit failure, an interposed workspace command, rejected exact compensation, later retry, and eventual success. They assert workspace compensation is attempted before native recovery on both initial and pending paths, and no new deletion begins while recovery is unresolved.
- Existing Task 32 native transaction, descriptor mutant, mixed-success, and compatibility suites remain green.

## Task 2 Design: Restart After Layout Failure

`LayoutSettleLoop` will distinguish a failed-but-restartable run from a terminally superseded or closed run. A failed publication may complete the run's settlement future and publish `OperationalStatus.FAILED`, but it must not mark the current run terminal or detach it from `currentRun`. The current run retains its immutable `LayoutRequest` and projection generation for recovery.

On `restart()` for a failed current run:

1. Under `monitor`, clear pause intent and increment the control revision. If failed publication is still in flight, record a `restartRequested` recovery intent without claiming a second frame; the publication completion owns the handoff. Otherwise claim exactly one recovery frame and mark that a fresh submit is required.
2. On the existing lifecycle executor, validate the run token/revision. After the failed publication releases its claim, the newest restart revision alone claims recovery, calls `FrameStepper.restart()` once, validates again, and calls `FrameStepper.submit(currentRequest)` rather than `step()` when the run's previous submit failed.
3. Only a successful replacement frame can publish `SETTLING` or `IDLE` and clear the failed marker. A restart or submit failure leaves the run restartable and publishes/retains `FAILED` without issuing stale follow-up work.
4. Reset, a newer start, and close cancel any deferred failed-recovery intent and release its claim. A second reentrant restart supersedes the older revision but cannot strand the newer recovery request. Completion of settlement futures occurs after releasing `monitor` while the current failed run remains live.

The implementation will use existing `LayoutWorker.restart()` behavior, which replaces a failed engine before its next submit. `GraphUpdateCoordinator.restartLayout()` and the router's applied result remain compatible; a real coordinator/lifecycle test will prove that the applied command now causes a replacement submission and recovered current-generation state. No new public command is needed.

### Task 2 verification model

- A deterministic `LayoutSettleLoopShould` regression uses a frame stepper whose first submit returns a failed frame and whose second submit returns an idle frame. It asserts failure publication, two restart calls, two submits, current generation preservation, and recovered `IDLE` publication.
- Tests cover restart failure, a second restart during failed EDT publication, reset/close superseding deferred recovery, and no stale submission after a failed run.
- `GraphUpdateCoordinatorShould` adds the live failed-frame -> Restart Layout -> recovered-IDLE command-chain assertion using the package-private lifecycle/stepper seam. `GraphCommandRouterShould` retains the public routing/result contract assertion.
- Existing lifecycle, worker, coordinator, and full graph-plugin tests remain green.

## Allowlist

The successor plan must authorize exactly the paths needed by the two seams and their tests:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

No other tracked path is permitted. If implementation proves a path outside this list is load-bearing, the successor run must block rather than silently widening itself.

## Acceptance

The successor is complete only after both tasks pass independent spec/quality review, all carried findings are reconciled, the final Frontier review passes over the original merge base through the new HEAD, the full graph-plugin suite is fresh and green, and the successor worktree has no tracked or staged changes. The parent terminal run is never reopened.

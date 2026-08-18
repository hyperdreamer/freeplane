# Graph Workspace Final Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Resolve the two load-bearing whole-branch findings carried from the blocked Batch F final review: exact workspace purge compensation and restartable layout recovery after failure.

**Architecture:** Task 1 deepens `GraphWorkspaceStore` and `WorkspaceHistory` with an opaque, token-bound compensation handle. `DefaultContributorDeletionHandler` retains that handle alongside native recovery and can compensate only the exact purge it published. Task 2 keeps a failed `LayoutSettleLoop` run live and resubmits its immutable current request after the existing `LayoutWorker.restart()` lifecycle operation succeeds. The tasks do not share production files and are reviewed independently.

**Tech Stack:** Java 8 source/bytecode compatibility, Java 21.0.8 Zulu build runtime, Gradle, JUnit 4, AssertJ, Mockito, immutable Graph Workspace values, Swing EDT and serialized lifecycle dispatcher.

## Global Constraints

- Use `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle ...` for every Gradle command; use `gradle`, never Maven or a Gradle wrapper.
- Java source and target compatibility remain 8, encoding remains UTF-8, indentation remains 4 spaces, and tests use JUnit 4/AssertJ/Mockito.
- The successor starts from committed parent HEAD `af034e5d9bd1c6a58be81ec245835fbff35e1ec8` in `.worktrees/graph-batch-f-successor`; the parent `FINAL_BLOCKED` run, its plan, reports, and worktree are immutable evidence and must not be edited or reopened.
- Carried final-review findings are canonical IDs `FINAL-F2` and `FINAL-F4`; original task findings `F-1`, `F-2`, and `F-3` are fixed evidence and must remain fixed.
- Change only the paths listed in the current task. Do not modify `freeplane_api`, GraphStream dependencies, `LayoutWorker.java`, translations, resources, launchers, or unrelated production code.
- Do not add public OSGi/API exports or new graph command types. Existing public command signatures and normal workspace `GraphWorkspaceStore.execute(WorkspaceCommand)` behavior remain compatible.
- Every cross-resource compensation operation must be atomic under its owning store/lifecycle lock, exact to the resource it published, retryable when incomplete, and fail closed when ownership cannot be proven.
- All layout worker operations remain on the existing serialized lifecycle dispatcher; all Swing/canvas publication remains on the EDT; stale, superseded, paused, and closed runs must not issue physical work.
- Before each task commit, assert an empty index, stage only that task's explicit allowlist, run `git diff --check`, and use an imperative subject beginning `2026-08-10-graph-workspace:`.
- Do not stage or commit the generated `.codegraph/` directory or any controller artifacts outside the task allowlists.

## Task 1: Exact workspace purge compensation and recovery ownership

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java:28-105,278-315`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java:9-69`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java:275-445`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`

**Interfaces:**

- Consumes the existing `WorkspaceCommand`, `WorkspaceTransition`, `GraphCommandResult`, `WorkspaceDocument`, `GraphWorkspaceStore.execute(WorkspaceCommand)`, `GraphWorkspaceStore.undo()`, `DefaultContributorDeletionHandler`, and `FreeplaneMapCommandExecutor.ContributorDeletionTransaction` contracts.
- Produces `public GraphWorkspaceStore.WorkspaceMutation executeWithCompensation(WorkspaceCommand command)`.
- Produces `public static final class GraphWorkspaceStore.WorkspaceMutation` with `GraphCommandResult result()` and `GraphCommandResult compensateIfCurrent()`. It owns no exposed mutable document or history collection. `compensateIfCurrent()` returns an applied result only when the exact captured history entry is still current; otherwise it returns a deterministic rejected conflict result and leaves the current document/history unchanged. Repeated successful calls are idempotent.
- Produces package-private `WorkspaceHistory.HistoryMutation executeWithToken(WorkspaceCommand command, WorkspaceDocument current)` and `WorkspaceTransition compensate(HistoryMutation mutation, WorkspaceDocument current)`. The token identifies entry identity, before/after documents, and the redo state that existed before publication. Existing `execute`, `undo`, `redo`, `canUndo`, `canRedo`, and `clear` behavior remains unchanged for normal callers.
- `DefaultContributorDeletionHandler` consumes the new mutation handle for the mixed `WorkspaceCommands.purgeRelationships(Set<RelationshipId>)` call. Its existing `PendingRecovery` retains unresolved native and workspace resources independently; workspace recovery invokes the exact mutation handle, native recovery invokes the transaction, and the handle is cleared only after both resources report complete.

**Step 1: Write falsifiable failing tests first.**

- Add a `WorkspaceHistoryShould` test that executes a purge-like command, retains its `HistoryMutation`, applies an unrelated second command, and proves `compensate` rejects without popping the unrelated command or changing the current document.
- Add a matching-token history test that compensates exactly once, removes only the target entry, restores the prior redo stack, and preserves the existing envelope rules used by undo/redo.
- Add a `GraphWorkspaceStoreShould` real-store test that uses `executeWithCompensation`, interposes an applied command, and proves `compensateIfCurrent()` returns a conflict while the interposed document remains current.
- Add a handler regression that makes native commit fail after the purge, interposes an unrelated workspace command before compensation, and asserts the handler returns `undo_incomplete`, retains the exact mutation handle, and does not consume the unrelated command on later recovery.
- Keep the existing successful mixed path and round-3 resource-aware retry tests as regression coverage.

**Step 2: Run RED and verify the failure mechanism.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull
```

The new tests must fail because no token-bound compensation exists or because the handler still calls generic global undo. Correct fixture/setup errors until the failure is behavioral and specifically demonstrates unrelated-history consumption or missing exact recovery ownership.

**Step 3: Implement token-bound history without changing normal undo/redo.**

- Replace document-only internal undo entries with immutable identity-bearing history entries while retaining the existing public normal-history methods.
- Implement `executeWithToken` so an applied command captures the before document, after document, prior redo stack, and entry identity before normal publication clears redo.
- Implement `compensate` to require the exact entry at the undo head and an equal current after document. On success, remove only that entry, restore the captured redo state and current document envelope, and return an applied compensation result. On mismatch, return a rejected conflict result without mutation.
- Keep no-op/rejected/read-only commands uncompensatable and preserve normal `undo()`/`redo()` result keys and envelope behavior.

**Step 4: Add the store-owned compensation handle and wire the handler.**

- Implement `GraphWorkspaceStore.executeWithCompensation` using the same monitor, read-only checks, transition installation, dirty/autosave/event semantics, and `WorkspaceHistory.executeWithToken` path as normal execution.
- Change only the mixed contributor deletion path to use the new mutation handle for its one purge command. Do not call generic `store.undo()` for compensation.
- Extend `PendingRecovery` to retain the mutation handle and independently track native/workspace unresolved resources. Retry the exact workspace mutation first, then native transaction recovery, and retain any resource still incomplete. Block new projection/native/store work while pending recovery remains.
- Return `graph_workspace.contributor.undo_incomplete` with current resource and dirty-map/editor metadata whenever exact compensation cannot be proven. Clear the handle only after exact compensation succeeds for every resource.

**Step 5: Run GREEN and compatibility verification.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*FreeplaneMapCommandExecutorShould' --tests '*GraphCommandRouterShould' --tests '*WorkspaceMapCoordinatorShould' --tests '*DefaultPurgeCommandHandlerShould' -PTestLoggingFull
```

Assert the real-store interposition test fails against the pre-fix parent and passes now, normal workspace undo/redo tests remain green, native Task 32 transaction tests remain green, and no generic compensation call remains in the handler.

**Step 6: Verify exact scope and commit.**

Assert the index is empty, stage exactly the six listed paths, compare sorted staged names to that allowlist, run `git diff --check`, and commit:

```bash
git commit -m "2026-08-10-graph-workspace: Make purge compensation conditional"
```

The report must include RED/GREEN evidence, exact-history conflict and successful-compensation evidence, resource-recovery behavior, scope, and commit SHA.

## Task 2: Restart failed layout runs through the command chain

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:165-190,297-349,530-611, Run state`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**

- Consumes the existing `LayoutSettleLoop.start`, `pause`, `restart`, `close`, `FrameStepper.restart`, `FrameStepper.submit`, `FrameStepper.step`, `CanvasState`, `OperationalStatus`, `LayoutFrame`, `GraphCommandRouter.execute(GraphCommand)`, and `GraphUpdateCoordinator.restartLayout()` contracts.
- Preserves the public `LayoutSettleLoop` API and produces no new command type. The internal `Run` gains only state needed to distinguish a failed-but-restartable current request from terminal/superseded/closed state.
- A restart of a failed current run calls `FrameStepper.restart()` once and then submits the retained immutable `LayoutRequest`; it never advances with `step()` until a replacement submit has succeeded.

**Step 1: Write the failed-frame restart regression first.**

- Add a deterministic `LayoutSettleLoopShould` fixture whose first submit returns a failed frame, whose second submit returns an idle frame, and whose lifecycle dispatcher runs synchronously under test control.
- Assert the first publication is `OperationalStatus.FAILED`, the settlement future completes without detaching the current run, `restart()` calls the worker restart and a second submit, and the recovered publication is the same accepted generation with `OperationalStatus.IDLE`.
- Add a command-path assertion in `GraphCommandRouterShould` that the Restart Layout command reaches the coordinator and returns the existing applied result while the loop recovers.

**Step 2: Run RED and verify the terminal-run cause.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphCommandRouterShould' -PTestLoggingFull
```

The new failed-frame -> restart test must fail because the current failed publication terminalizes the run and produces no second submit. Fix only test fixture errors before production edits.

**Step 3: Keep failed runs restartable and resubmit after worker restart.**

- Mark the current run failed/restartable before publishing its failed state. When the failed publication completes, complete the settlement future but do not call `terminalizeLocked` or detach `currentRun` solely because the failed frame is idle.
- Make `restart()` claim one recovery frame for a failed current run even though its prior settlement future is complete. Preserve all token/control-revision checks and release claims for stale/superseded/closed runs.
- In the lifecycle queue, call `FrameStepper.restart()` first. If the run has a failed or unsubmitted request, call `submitClaimed` with the retained request; otherwise preserve normal step behavior. Clear the failed marker only after a successful non-failed frame is handled.
- If restart or replacement submit fails, retain a restartable failed state without issuing a stale follow-up operation. Existing pause/reset/close and lifecycle serialization invariants remain unchanged.

**Step 4: Add lifecycle edge tests and mutation proof.**

- Test restart failure leaves the run failed and permits a later retry without duplicate submissions.
- Test reset or close superseding a failed-run restart prevents the obsolete submit and releases its claim.
- Mutate only the new failed-run restart branch to skip replacement submit; the direct regression test must fail. Restore the inverse byte-for-byte and verify the production hash.

**Step 5: Run focused and full verification.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphCommandRouterShould' -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutWorkerShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Confirm the full graph-plugin suite is fresh and green, the failed-run restart probe is mutation-sensitive, and `git diff --check` passes.

**Step 6: Verify exact scope and commit.**

Assert an empty index, stage exactly the three listed paths, compare sorted staged names to that allowlist, run `git diff --check`, and commit:

```bash
git commit -m "2026-08-10-graph-workspace: Recover failed layout restart"
```

The report must include RED/GREEN results, failed/recovered state evidence, lifecycle supersession evidence, mutation proof, exact scope, and commit SHA.

### Success Gate

After both tasks pass their independent review loops, the controller must dispatch a fresh Frontier final review over `9248c6e227bb82fab8e6139f46db37b62174309f..HEAD`, reconcile `FINAL-F2` and `FINAL-F4` as fixed, run the full graph-plugin suite from the successor HEAD, and require a clean tracked/index state. The parent `FINAL_BLOCKED` run is never reopened.

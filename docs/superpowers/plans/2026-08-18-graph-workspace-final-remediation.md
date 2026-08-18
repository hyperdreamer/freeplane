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
- Every cross-resource compensation operation must be atomic under its owning store/lifecycle lock, exact to the resource it published, retryable when incomplete, and fail closed when ownership cannot be proven; history tokens must reject ABA, save, autosave, save-as, and document-replacement interpositions and preserve verified persisted bytes/envelopes.
- Pending contributor recovery gates only new work initiated by `DefaultContributorDeletionHandler`; unrelated workspace commands remain allowed but invalidate the exact compensation token.
- All layout worker operations remain on the existing serialized lifecycle dispatcher; all Swing/canvas publication remains on the EDT; stale, superseded, paused, and closed runs must not issue physical work; failed-publication restart requests must be deferred and revision-checked.
- Before each task commit, assert an empty index, stage only that task's explicit allowlist, run `git diff --check`, and use an imperative subject beginning `2026-08-10-graph-workspace:`.
- Do not stage or commit the generated `.codegraph/` directory or any controller artifacts outside the task allowlists.

## Task 1: Exact workspace purge compensation and recovery ownership

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java:28-420`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java:9-100`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java:275-548`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`

**Interfaces:**

- Consumes the existing `WorkspaceCommand`, `WorkspaceTransition`, `GraphCommandResult`, `WorkspaceDocument`, `GraphWorkspaceStore.execute(WorkspaceCommand)`, `GraphWorkspaceStore.undo()`, `DefaultContributorDeletionHandler`, and `FreeplaneMapCommandExecutor.ContributorDeletionTransaction` contracts.
- Produces `public GraphWorkspaceStore.WorkspaceMutation executeWithCompensation(WorkspaceCommand command)`.
- Produces `public static final class GraphWorkspaceStore.WorkspaceMutation` with `GraphCommandResult result()` and `GraphCommandResult compensateIfCurrent()`. It owns no exposed mutable document or history collection. The opaque token includes entry identity, a monotonic history/store revision, redo-stack identity, workspace file identity, dirty/debounce/save generation, and before/after persisted-byte evidence. `compensateIfCurrent()` returns an applied result only when the full token matches; command -> undo/redo, save, save-as, autosave, and document replacement ABA states are rejected or safely restored with verified bytes. Repeated successful calls are idempotent and transient write failures remain retryable.
- Produces package-private `WorkspaceHistory.HistoryMutation executeWithToken(WorkspaceCommand command, WorkspaceDocument current)` and `WorkspaceTransition compensate(HistoryMutation mutation, WorkspaceDocument current)`. The history token identifies only entry identity, monotonic history revision, before/after documents, and redo-stack identity/contents; `GraphWorkspaceStore.WorkspaceMutation` adds file identity, persistence/save generation, dirty/debounce envelope, and persisted-byte evidence. Conditional compensation cannot be satisfied by equal documents alone. Existing `execute`, `undo`, `redo`, `canUndo`, `canRedo`, and `clear` behavior remains unchanged for normal callers.
- `DefaultContributorDeletionHandler` consumes the new mutation handle for the mixed `WorkspaceCommands.purgeRelationships(Set<RelationshipId>)` call. Its existing `PendingRecovery` retains unresolved native and workspace resources independently; workspace recovery invokes the exact mutation handle, native recovery invokes the transaction, and the handle is cleared only after both resources report complete.

**Step 1: Establish a parent-compiling behavioral RED test first.**

- Before referencing any new method, add a `WorkspaceHistoryShould.compensationMustNotUseGlobalUndoHead` test using only the existing `execute`, `undo`, and `redo` methods. Execute a purge-like command, execute an unrelated applied command, call generic `undo`, and assert the purge is restored while the unrelated command remains applied. The current implementation must fail this assertion because generic undo targets the global head. This is the required behavioral red evidence; a missing-symbol compilation failure is not acceptable.
- Add a second parent-compiling history test for command -> undo -> redo ABA ordering and record the exact observed failure.

Run this parent-compiling red test alone before adding any test reference to the new compensation API:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.WorkspaceHistoryShould.compensationMustNotUseGlobalUndoHead' -PTestLoggingFull --rerun-tasks
```

**Step 2: Add the desired contract tests and run RED.**

- Add a `WorkspaceHistoryShould` token test that applies an unrelated command, undo/redo interposition, and proves `compensate` rejects without popping the unrelated command or changing the current document.
- Add a matching-token history test that compensates exactly once, removes only the target entry, restores the prior redo stack, and preserves the existing envelope rules used by undo/redo.
- Add a `GraphWorkspaceStoreShould` real-store test for `executeWithCompensation`, clean/dirty autosave state, save-as/document replacement, persisted bytes, and interposed command conflict.
- Add a handler regression with a deterministic real-store/fake-transaction seam that makes native commit fail after purge, interposes an unrelated workspace command, and asserts exact compensation is retained without consuming the unrelated command. Include both initial workspace-first recovery ordering and later pending recovery ordering.
- Run the parent-compiling behavioral tests first and then the full desired focused set. The first command must show behavioral assertion failures, not missing-symbol errors; fixture errors must be corrected before production edits.

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
```

The parent-compiling tests must fail because generic global undo consumes the current history head and ABA/save interposition is not guarded. The new contract tests may initially require the new package-private seam, but the recorded red evidence must include the behavioral parent failure before implementation.

**Step 3: Implement token-bound history without changing normal undo/redo.**

- Replace document-only internal undo entries with immutable identity-bearing history entries while retaining the existing public normal-history methods.
- Implement `executeWithToken` so an applied command captures the before document, after document, prior redo stack identity/contents, entry identity, and monotonic history revision before normal publication clears redo.
- Implement `compensate` to require the exact history entry/revision, redo identity, and current after document. On success, remove only that entry, restore the captured redo state, and return an applied compensation transition. On command/undo/redo interposition or history mismatch, return a rejected conflict transition without mutation.
- In `GraphWorkspaceStore`, capture file identity, a monotonic persistence/save generation, dirty/debounce state, and before/after persisted bytes around the token execution. Advance the save generation in `saveDirtyLocked()` and identity-changing paths. `WorkspaceMutation.compensateIfCurrent()` must require both the history token and the persistence envelope; when current bytes equal the captured after bytes it may synchronously rewrite and verify captured before bytes, otherwise it returns conflict/incomplete without claiming recovery.
- Keep no-op/rejected/read-only commands uncompensatable and preserve normal `undo()`/`redo()` result keys and envelope behavior.

**Step 4: Add the store-owned compensation handle and wire the handler.**

- Implement `GraphWorkspaceStore.executeWithCompensation` using the same monitor, read-only checks, transition installation, dirty/autosave/event semantics, and `WorkspaceHistory.executeWithToken` path as normal execution.
- Change only the mixed contributor deletion path to use the new mutation handle for its one purge command. Do not call generic `store.undo()` for compensation.
- Extend `PendingRecovery` to retain the mutation handle and independently track native/workspace unresolved resources. On both initial failure handling and later pending retries, attempt exact workspace compensation first, then native transaction recovery, and retain any resource still incomplete. The handler-local pending gate blocks only this handler's new projection/native/store work; unrelated workspace commands remain allowed but make the exact token conflict.
- Return `graph_workspace.contributor.undo_incomplete` with current resource and dirty-map/editor metadata whenever exact compensation cannot be proven. Clear the handle only after exact compensation succeeds for every resource.

**Step 5: Run GREEN and compatibility verification.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*FreeplaneMapCommandExecutorShould' --tests '*GraphCommandRouterShould' --tests '*WorkspaceMapCoordinatorShould' --tests '*DefaultPurgeCommandHandlerShould' -PTestLoggingFull --rerun-tasks
```

failures=$(rg -o 'failures="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
errors=$(rg -o 'errors="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
test "$failures" -eq 0 && test "$errors" -eq 0
```

Assert the real-store interposition, command -> undo/redo ABA, clean/dirty persistence, and save-as tests fail against the pre-fix parent and pass now. Record JUnit XML failure/error totals, confirm normal workspace undo/redo and native Task 32 transaction tests remain green, and confirm no generic compensation call remains in the handler.

**Step 6: Run the named mutation and verify exact scope.**

Save production hashes before mutation, use `apply_patch` to replace only the history-token comparison with a weaker document-only comparison, run the ABA/interposition tests and require behavioral failure, apply the inverse immediately, compare both SHA-256 values exactly, and assert no mutant residue with `git diff`.

Assert the index is empty, stage exactly the six listed paths, and compare sorted staged names exactly with:

```text
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java
```

Run `git diff --cached --check`, `git diff --check`, inspect JUnit XML totals again, commit, and verify the resulting commit:

```bash
git commit -m "2026-08-10-graph-workspace: Make purge compensation conditional"
git show --format='%H%n%s' --name-only HEAD
```

The report must include RED/GREEN evidence, exact-history conflict and successful-compensation evidence, resource-recovery behavior, mutation hashes, scope, and commit SHA.

## Task 2: Restart failed layout runs through the command chain

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:165-190,297-349,530-611, Run state`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**

- Consumes the existing `LayoutSettleLoop.start`, `pause`, `restart`, `close`, `FrameStepper.restart`, `FrameStepper.submit`, `FrameStepper.step`, `CanvasState`, `OperationalStatus`, `LayoutFrame`, `GraphCommandRouter.execute(GraphCommand)`, and `GraphUpdateCoordinator.restartLayout()` contracts.
- Preserves the public `LayoutSettleLoop` API and produces no new command type. The internal `Run` gains only state needed to distinguish a failed-but-restartable current request from terminal/superseded/closed state.
- A restart of a failed current run calls `FrameStepper.restart()` once and then submits the retained immutable `LayoutRequest`; it never advances with `step()` until a replacement submit has succeeded. A restart invoked while failed publication is in flight records a deferred recovery claim and is handed off only after publication releases its claim.

**Step 1: Write the failed-frame restart regression first.**

- Add a deterministic `LayoutSettleLoopShould` fixture whose first submit returns a failed frame, whose second submit returns an idle frame, and whose lifecycle dispatcher runs synchronously under test control.
- Assert the first publication is `OperationalStatus.FAILED`, the settlement future completes without detaching the current run, `restart()` calls the worker restart and a second submit, and the recovered publication is the same accepted generation with `OperationalStatus.IDLE`.
- Add an EDT-listener regression that invokes a second restart during failed-state delivery, asserts the first claim is released, the newest revision owns exactly one replacement submit, and no stale operation runs.
- Add reset-wins and close-wins tests for a deferred failed-recovery claim. Add the live coordinator assertion in `GraphUpdateCoordinatorShould`; keep the mocked public delegation/result assertion in `GraphCommandRouterShould`.

**Step 2: Run RED and verify the terminal-run cause.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphCommandRouterShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
```

The new failed-frame -> restart and reentrant-listener tests must fail because the current failed publication terminalizes the run, holds the publication claim, and produces no second submit. Fix only test fixture errors before production edits.

**Step 3: Keep failed runs restartable and resubmit after worker restart.**

- Mark the current run failed/restartable before publishing its failed state. When the failed publication completes, complete the settlement future after releasing `monitor` but do not call `terminalizeLocked` or detach `currentRun` solely because the failed frame is idle.
- Make `restart()` claim one recovery frame for a failed current run even though its prior settlement future is complete. If publication is still in flight, set a deferred recovery flag and do not call the worker; publication completion consumes the newest control revision and claims the recovery frame exactly once. Preserve all token/control-revision checks and release claims for stale/superseded/closed runs.
- In the lifecycle queue, call `FrameStepper.restart()` first. If the run has a failed or unsubmitted request, call `submitClaimed` with the retained request; otherwise preserve normal step behavior. Clear the failed marker only after a successful non-failed frame is handled.
- If restart or replacement submit fails, retain a restartable failed state without issuing a stale follow-up operation. Reset, newer start, and close cancel deferred recovery and release claims. Existing pause/reset/close and lifecycle serialization invariants remain unchanged.

**Step 4: Add lifecycle edge tests and mutation proof.**

- Test restart failure leaves the run failed and permits a later retry without duplicate submissions.
- Test a second restart reentrant from the failed EDT listener, reset superseding deferred recovery, and close superseding deferred recovery; each must release stale claims and prevent obsolete submit.
- Mutate only the new failed-run restart branch to skip replacement submit; the direct regression test must fail. Restore the inverse byte-for-byte, verify the production hash, and retain the JUnit XML failure/error totals.

**Step 5: Run focused and full verification.**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphCommandRouterShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutWorkerShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Confirm the full graph-plugin suite is fresh and green, then run:

```bash
failures=$(rg -o 'failures="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
errors=$(rg -o 'errors="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
test "$failures" -eq 0 && test "$errors" -eq 0
```

The failed-run restart probe must be mutation-sensitive, and `git diff --check` must pass.

**Step 6: Verify exact scope and commit.**

Assert an empty index, stage exactly these four paths, and compare sorted staged names exactly with:

```text
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java
```

Run both `git diff --cached --check` and `git diff --check`, inspect JUnit XML failure/error totals, and commit/verify:

```bash
git commit -m "2026-08-10-graph-workspace: Recover failed layout restart"
git show --format='%H%n%s' --name-only HEAD
```

The report must include RED/GREEN results, failed/recovered state evidence, lifecycle supersession evidence, mutation proof, exact scope, and commit SHA.

### Success Gate

After both tasks pass their independent review loops, the controller must dispatch a fresh Frontier final review over `9248c6e227bb82fab8e6139f46db37b62174309f..HEAD`, reconcile `FINAL-F2` and `FINAL-F4` as fixed, run the full graph-plugin suite from the successor HEAD, and require a clean tracked/index state. The parent `FINAL_BLOCKED` run is never reopened.

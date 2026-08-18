# Graph Workspace Final Remediation V3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Freshly audit Batch F workspace compensation, repair persisted-byte
retry recovery, and complete failed layout restart recovery.

**Architecture:** Task 1 is a no-source-change audit and independent review of the
exact six-file workspace compensation commit. Task 2 repairs one verified
`GraphWorkspaceStore` retry state transition with a falsifiable write-before-throw
regression. Task 3 implements failed `LayoutSettleLoop` recovery through the live
coordinator/router test chain. A fresh Frontier final review covers the whole
branch from the original merge base.

**Tech Stack:** Java 8 source/bytecode compatibility, Java 21.0.8 Zulu runtime,
Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, and serialized lifecycle dispatch.

## Global Constraints

- Use `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle ...` for every Gradle command; use `gradle`, never Maven or a Gradle wrapper.
- Java source/target remain 8, source encoding is UTF-8, indentation is 4 spaces, and tests use JUnit 4, AssertJ, and Mockito.
- Work only in `.worktrees/graph-batch-f-successor-v3` on `2026-08-10-graph-workspace-batch-f-successor-v3`; preserve V2 and all older blocked run roots unchanged.
- Reports from terminal predecessor runs are inadmissible. Task 1 must establish fresh source, Git, test, and mutation evidence.
- Task 1 audit range is exactly `091e46581950fffeb42087e48d696d43d2158848^..091e46581950fffeb42087e48d696d43d2158848`; its six-path scope is fixed and audit-only.
- Task 2 may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`.
- Task 3 may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`.
- Do not modify `WorkspaceHistory.java`, `GraphUpdateCoordinator.java`, `GraphCommandRouter.java`, `LayoutWorker.java`, APIs, resources, translations, or unrelated code.
- All Gradle evidence uses `--rerun-tasks`. Record JUnit XML failure/error totals, `git diff --check`, exact staged names, `git diff --cached --check`, mutation restoration hashes, and committed changed-file lists.
- Do not stage `.codegraph/`, `.superpowers/`, build output, or controller artifacts. Do not use destructive Git reset or checkout operations.

## Task 1: Fresh audit of exact workspace compensation commit

**Implementer tier:** Capable

**Files:**

- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`

**Interfaces:**

- Consumes: direct commit parent `6c242168eba15725aa27988e39b2027f93949ec3`, commit `091e46581950fffeb42087e48d696d43d2158848`, and the `FINAL-F2` exact-history contract.
- Produces: one bounded audit report under the run root with exact six-path scope, focused compatibility evidence, a disposable ABA mutation, and no deliverable or source commit.
- Does not consume: any blocked predecessor report as evidence.

### Step 1: Verify exact scope and read-only baseline

- [ ] Require `git diff --name-only 091e46581950fffeb42087e48d696d43d2158848^ 091e46581950fffeb42087e48d696d43d2158848` to equal the six Files paths.
- [ ] Run `git diff --check` for that direct commit and confirm empty index/worktree tracked state.
- [ ] Inspect source and tests independently; predecessor reports may identify hypotheses only.

### Step 2: Audit compensation contracts

- [ ] Verify `WorkspaceHistory` captures entry identity, revision, redo identity/content, and current-document identity, rejecting command/undo/redo ABA without consuming later history.
- [ ] Verify `GraphWorkspaceStore.WorkspaceMutation` checks file identity, document identity, save/debounce state, and persisted before/after bytes, including save-as and transient failures.
- [ ] Verify contributor deletion retains the exact mutation handle, recovers workspace before native state on initial and pending paths, retains unresolved resources, and contains no generic `store.undo()` compensation.
- [ ] Verify original Task 32 native transaction, descriptor, owner-local undo, and compatibility behavior remain unchanged.

### Step 3: Fresh tests and falsifiable archive mutation

- [ ] Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
```

- [ ] Run adjacent compatibility tests:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*FreeplaneMapCommandExecutorShould' --tests '*GraphCommandRouterShould' --tests '*WorkspaceMapCoordinatorShould' --tests '*DefaultPurgeCommandHandlerShould' -PTestLoggingFull --rerun-tasks
```

- [ ] In a disposable archive only, weaken `WorkspaceHistory.compensate` identity/revision/redo/current checks to document equality and require `rejectsCompensationAfterCommandUndoRedoABA` to fail. Delete the archive and verify active production hashes are unchanged.

### Step 4: Audit report

- [ ] Write exactly one audit report under the run root, including direct parent/head, six paths, source observations, test totals, mutation result, and any independently found issue.
- [ ] Confirm no tracked/index changes and return `DONE` only after the report is present.

## Task 2: Repair write-before-throw persisted-byte compensation retry

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java:421-482, WorkspaceMutation state`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java:722-800`

**Interfaces:**

- Consumes: `GraphWorkspaceStore.WorkspaceMutation.compensateIfCurrent()`, `writeAndVerifyLocked(byte[])`, `AtomicWorkspaceWriter.write(Path, byte[])`, and `WorkspaceHistory.compensate(HistoryMutation, WorkspaceDocument)`.
- Produces: a retryable compensation path that reconciles a writer exception against actual persisted bytes while preserving file/document/history identity guards and monotonic save-generation semantics.
- Preserves: existing conflict rejection, ordinary transient failure retry, autosave compensation, save-as rejection, dirty-envelope restoration, and idempotent successful compensation.

### Step 1: Add a falsifiable red regression

- [ ] Extend the existing `RecordingWriter` test double with a mode that writes the supplied bytes to disk and then throws once.
- [ ] Add a test that executes a mutation, runs its autosave so the file contains `afterBytes`, arms write-after-persist failure, calls compensation once, and asserts `compensation_incomplete`, actual file `beforeBytes`, post-mutation document/history, and autosave-cleared dirty state.
- [ ] Retry the same mutation and assert it becomes `APPLIED`, restores the pre-mutation document and dirty envelope, leaves the file at `beforeBytes`, and does not decrement any monotonic save generation. Run the focused class before production edits and require the second assertion to fail under the current implementation.

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceStoreShould' -PTestLoggingFull --rerun-tasks
```

### Step 2: Implement explicit verified restore progress

- [ ] Add mutable mutation state recording that a restore attempt which threw has nevertheless been verified by a fresh read as exactly `beforeBytes`.
- [ ] In the restore catch path, read the target after the writer throws; set restore-progress only when the exact before bytes are present. If bytes are absent, after bytes, or unrelated, retain the existing incomplete/conflict behavior.
- [ ] On retry, allow the before-byte branch to proceed when restore-progress is verified, without requiring the pre-autosave dirty/save-generation envelope to equal its captured values. Continue requiring current file identity, current document identity, and the original history token; do not bypass interposition or save-as rejection.
- [ ] On successful history compensation after verified restore progress, keep save generations monotonic, invalidate stale debounce work, restore `dirtyBefore`, publish the normal document/saved events, and cache the successful result. If history compensation rejects, restore after bytes and leave the mutation retryable.
- [ ] Keep one active execution path; do not add a generic undo fallback or modify `WorkspaceHistory`.

### Step 3: Green, mutation proof, and compatibility

- [ ] Run the focused store tests and adjacent workspace tests with `--rerun-tasks`; require zero failures/errors.
- [ ] Mutate only the new restore-progress admission branch so the write-after-throw regression fails, restore the inverse, verify production SHA-256 restoration, and confirm no mutant residue in `git diff`.
- [ ] Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceStoreShould' --tests '*WorkspaceHistoryShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
```

### Step 4: Exact two-file commit

- [ ] Stage exactly `GraphWorkspaceStore.java` and `GraphWorkspaceStoreShould.java`, run staged/worktree diff checks, and verify JUnit totals.
- [ ] Commit with `git commit -m "2026-08-10-graph-workspace: Repair compensation retry recovery"` and verify `git show --name-only` lists exactly the two files.
- [ ] Report the red failure, green result, write-after-throw evidence, mutation proof, commit SHA, and exact scope.

## Task 3: Recover failed layout restart through the live command chain

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:165-190,297-349,419-607,874-897`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**

- Consumes: `LayoutSettleLoop.restart()`, `finishPublication(Run, boolean, Throwable)`, `FrameStepper.restart()`, `FrameStepper.submit(LayoutRequest)`, `GraphUpdateCoordinator.restartLayout()`, and `GraphCommandRouter.execute(GraphCommands.RestartLayout)`.
- Produces: a failed current run that remains attached and restartable, where the newest valid restart calls worker restart once and resubmits the retained request exactly once.
- Preserves: public APIs, router result keys, paused behavior, reset/close/newer-start cancellation, lifecycle serialization, and immutable request ownership.

### Step 1: Deterministic red regressions

- [ ] Add a failed-frame then recovered-idle `LayoutSettleLoopShould` test asserting failed publication, later restart, one worker restart, and a second submit rather than an initial step.
- [ ] Add reentrant double-restart, reset-wins, and close-wins tests using the existing manual lifecycle dispatcher and no sleeps.
- [ ] Add a real `GraphUpdateCoordinatorShould` failed-frame -> `restartLayout()` -> recovered-IDLE regression.
- [ ] Preserve `GraphCommandRouterShould` applied restart result and prove the live command chain delegates once.
- [ ] Run the three focused classes before production edits and require behavioral failure because failed publication currently terminalizes or does not resubmit.

### Step 2: Revision-bound failed-run recovery

- [ ] Extend only `LayoutSettleLoop.Run` with failed publication/recovery state guarded by `monitor`.
- [ ] Keep failed canvas publication attached to the current run; do not terminalize solely because the failed frame is idle.
- [ ] Make `restart()` advance `controlRevision`, clear pause, and either claim one recovery frame or defer while failed publication owns the claim.
- [ ] After publication releases, validate token/revision and call worker restart before submitting the retained request, never step first for failed recovery.
- [ ] Invalidate stale recovery on reentrant newer restart, reset, newer start, pause, or close. Leave the run retryable after restart/submit failure without autonomous retry loops.

### Step 3: Green, mutation proof, and full verification

- [ ] Run focused recovery/coordinator/router tests and require zero failures/errors.
- [ ] Mutate only the failed-run recovery submit branch, require its direct regression to fail, restore the inverse, and verify hash restoration.
- [ ] Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutWorkerShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

### Step 4: Exact four-file commit

- [ ] Stage exactly the four Task 3 Files, run diff checks, verify final test totals, and commit with `git commit -m "2026-08-10-graph-workspace: Recover failed layout restart"`.
- [ ] Verify the committed file list and report lifecycle ordering, mutation proof, red/green evidence, and JUnit totals.

## Success Gate

After Task 3 review and any bounded fix/re-review cycles, run a fresh Frontier final
review from `9248c6e227bb82fab8e6139f46db37b62174309f` through the V3 HEAD. Reconcile
`FINAL-F2` and `FINAL-F4`, run fresh graph-plugin verification, and require clean
tracked/index state. Never reopen or admit any terminal predecessor run.

# Graph Workspace Final Remediation V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Freshly certify the preserved exact workspace-compensation commit, then
make failed Graph Workspace layout runs restartable through the live command chain.

**Architecture:** Task 1 is a read-only, no-source-change audit of the direct
six-file delta in `091e465819`; it is followed by a fresh independent task review.
Task 2 keeps a failed `LayoutSettleLoop.Run` live until reset, superseding start,
or close, and uses a revision-bound recovery claim to restart the worker and
resubmit the retained immutable request. The controller then performs its normal
whole-branch Frontier final review.

**Tech Stack:** Java 8 source/bytecode compatibility, Java 21.0.8 Zulu runtime,
Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, and serialized lifecycle dispatch.

## Global Constraints

- Use `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle ...` for every Gradle command; use `gradle`, never Maven or a Gradle wrapper.
- Java source/target remain 8, source encoding is UTF-8, indentation is 4 spaces, and tests use JUnit 4, AssertJ, and Mockito.
- Work only in `.worktrees/graph-batch-f-successor-v2` on `2026-08-10-graph-workspace-batch-f-successor-v2`; never modify or reopen the terminal parent worktrees or SDD run roots.
- Parent blocked reports and reviewer verdicts are inadmissible. Task 1 must establish fresh evidence from source, Git, tests, and a disposable archive mutation only.
- The Task 1 source boundary is exactly direct commit delta `091e46581950fffeb42087e48d696d43d2158848^..091e46581950fffeb42087e48d696d43d2158848`; do not include predecessor plan or design paths in its allowlist.
- Carried findings are `FINAL-F2` exact workspace compensation and `FINAL-F4` failed-layout restart. Original Task 32 findings F-1/F-2/F-3 are fixed historical evidence only.
- Task 2 may modify only `LayoutSettleLoop.java`, `LayoutSettleLoopShould.java`, `GraphUpdateCoordinatorShould.java`, and `GraphCommandRouterShould.java`; do not modify `LayoutWorker.java`, `GraphUpdateCoordinator.java`, `GraphCommandRouter.java`, workspace persistence files, APIs, resources, or translations.
- All Gradle evidence uses `--rerun-tasks`. Record JUnit XML failure/error totals, `git diff --check`, exact staged names, `git diff --cached --check`, mutation restoration hashes, and the committed changed-file list.
- Do not stage `.codegraph/`, `.superpowers/`, build output, or controller artifacts. Do not use destructive Git reset or checkout operations.

## Task 1: Re-audit the exact workspace-compensation commit

**Implementer tier:** Capable

**Files:**

- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`

**Interfaces:**

- Consumes: direct parent `6c242168eba15725aa27988e39b2027f93949ec3`, implementation commit `091e46581950fffeb42087e48d696d43d2158848`, and the immutable `FINAL-F2` exact-history requirement.
- Produces: one no-source-change report under the new SDD run root stating `AUDIT: PASS` or `AUDIT: FINDINGS`, direct-commit scope evidence, fresh test totals, and archive-mutation evidence.
- Does not produce: source changes, index changes, a source commit, or an admitted result from any terminal predecessor run.

### Step 1: Verify the direct commit boundary before reading implementation details

- [ ] Run `git diff --name-only 091e46581950fffeb42087e48d696d43d2158848^ 091e46581950fffeb42087e48d696d43d2158848` and require exactly the six paths in this task's Files block.
- [ ] Run `git diff --check 091e46581950fffeb42087e48d696d43d2158848^ 091e46581950fffeb42087e48d696d43d2158848`.
- [ ] Confirm `git status --short` is empty apart from ignored build/controller output and `git diff --cached --quiet` succeeds.
- [ ] Do not read a blocked child report as proof; inspect source and tests independently.

### Step 2: Audit the `FINAL-F2` exact-history and recovery contract

- [ ] Inspect `WorkspaceHistory` and prove that a compensation token captures immutable entry identity, revision, redo identity/content, and current document identity; a command-undo-redo ABA sequence must reject compensation without consuming another entry.
- [ ] Inspect `GraphWorkspaceStore.WorkspaceMutation` and prove its compensation is conditional on file identity, save generation, persisted bytes, and dirty/debounce envelope. Verify save, autosave, save-as, stale identity, and transient restore behavior cannot silently undo a later command.
- [ ] Inspect `DefaultContributorDeletionHandler` and prove mixed deletion holds its exact workspace mutation handle, attempts workspace recovery before native recovery on both initial and pending paths, retains unresolved resources, and never uses `store.undo()` for purge compensation.
- [ ] Inspect the Task 32 compatibility path: one native transaction per touched map, reverse native recovery, owner-local undo, and no changed command descriptors.

### Step 3: Reproduce focused evidence and the falsifiable mutation

- [ ] Run the focused Task 1 suites:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
failures=$(rg -o 'failures="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
errors=$(rg -o 'errors="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
test "$failures" -eq 0 && test "$errors" -eq 0
```

- [ ] Run the adjacent Task 32 compatibility suites:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*FreeplaneMapCommandExecutorShould' --tests '*GraphCommandRouterShould' --tests '*WorkspaceMapCoordinatorShould' --tests '*DefaultPurgeCommandHandlerShould' -PTestLoggingFull --rerun-tasks
```

- [ ] Create a disposable `git archive` copy at the audited commit, weaken only the `WorkspaceHistory.compensate` predicate from its entry/revision/redo/current-identity check to document equality, and run `WorkspaceHistoryShould.rejectsCompensationAfterCommandUndoRedoABA` there. It must fail because compensation incorrectly applies.
- [ ] Delete the disposable copy and compare SHA-256 values of all three audited production files with their values before the archive mutation. Do not mutate the active worktree.

### Step 4: Write the audit report without a source commit

- [ ] Write exactly one report under the assigned run root, including direct commit parent/head IDs, exact six-file list, contract observations, JUnit totals, mutation outcome, and any grounded finding.
- [ ] Verify `git status --short`, `git diff --cached --quiet`, and `git diff --quiet` before returning `DONE`; do not stage or commit source.
- [ ] The independent task reviewer must review the same direct commit delta and must not treat this report or any blocked-run report as sufficient evidence.

## Task 2: Recover a failed layout run through Restart Layout

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:165-190,297-349,419-607,874-897`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**

- Consumes: `LayoutSettleLoop.restart()`, `finishPublication(Run, boolean, Throwable)`, `FrameStepper.restart()`, `FrameStepper.submit(LayoutRequest)`, `GraphUpdateCoordinator.restartLayout()`, and `GraphCommandRouter.execute(GraphCommands.RestartLayout)`.
- Produces: a failed current run that remains attached and restartable, where the newest valid restart calls `FrameStepper.restart()` once and then resubmits the retained `LayoutRequest` exactly once.
- Preserves: public APIs, existing router result keys, paused behavior, reset/close/newer-start cancellation, lifecycle serialization, and immutable `LayoutRequest` ownership.

### Step 1: Write deterministic failing regressions

- [ ] In `LayoutSettleLoopShould`, add a test whose initial `submit(request)` produces a failed frame and whose recovery `submit(request)` produces an idle frame. Assert failed publication, a later `restart()`, one worker restart for recovery, and a second `submit` of the same request rather than a first `step`.
- [ ] Add a reentrant listener test using the existing manual lifecycle dispatcher: when the FAILED state is published, invoke `restart()` twice, drain the dispatcher in order, and prove only the latest control revision restarts and submits.
- [ ] Add reset-wins and close-wins tests that request failed-run restart while publication is held, then reset or close before the deferred recovery can run. Prove neither path submits the stale request.
- [ ] In `GraphUpdateCoordinatorShould`, construct the real package-private coordinator/settle-loop test seam, publish a failed frame, call `restartLayout()`, and assert the retained projection reaches an IDLE canvas state after the second submission.
- [ ] In `GraphCommandRouterShould`, preserve the router's applied result and prove `GraphCommands.restartLayout()` reaches the coordinator once in the live command chain fixture.
- [ ] Run the three focused classes before production edits and require a behavioral failure caused by no replacement submit after a failed publication:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphUpdateCoordinatorShould' --tests '*GraphCommandRouterShould' -PTestLoggingFull --rerun-tasks
```

### Step 2: Implement revision-bound failed-run recovery

- [ ] Extend only `LayoutSettleLoop.Run` state needed to remember that the current published frame is failed and that a failed-run restart is deferred or claimed. Keep this state guarded by `monitor`.
- [ ] Change failed-frame handling so the FAILED canvas publication does not call `terminalizeLocked` merely because it is idle. It must clear publication ownership while retaining the current run and immutable request for a later restart.
- [ ] In `restart()`, advance `controlRevision`, clear pause intent, and either claim one recovery frame immediately or record a deferred recovery intent when failed publication owns the claim. A restart during ordinary healthy settlement must retain existing step behavior.
- [ ] After failed publication releases its claim, revalidate current token, non-closed state, and newest control revision on the lifecycle dispatcher. Call `worker.restart()` before `submitClaimed` for the retained request, forcing `submit` instead of `step` for failed recovery.
- [ ] A reentrant second restart supersedes the old revision. Reset, a newer `start`, pause, or close clears or invalidates deferred recovery before it can submit stale work.
- [ ] If recovery restart or submit throws, publish FAILED again without terminalizing the current run, so a later restart remains possible. Do not add a loop that autonomously retries.

### Step 3: Prove the mechanism and scope

- [ ] Run the focused recovery, coordinator, and router tests until they pass with zero JUnit failures/errors.
- [ ] After the green implementation, capture `sha256sum freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java` to `/tmp/layout-settle-loop.v2.before-mutation.sha256`.
- [ ] Mutate only the branch that chooses recovery `submit` after a failed run, run the direct failed-run regression, and require the regression to fail. Restore the inverse immediately, run `sha256sum -c /tmp/layout-settle-loop.v2.before-mutation.sha256`, and verify no mutant residue remains in `git diff`.
- [ ] Run compatibility and full plugin verification:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutWorkerShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

- [ ] If the full suite has a failure, record its exact class, method, failure/error totals, source relevance, and a focused rerun. Do not classify it as unrelated without that evidence.

### Step 4: Commit only the allowed files

- [ ] Confirm the index starts empty. Stage exactly the four paths in this task's Files block and compare the sorted staged names to that exact list.
- [ ] Run `git diff --check` and `git diff --cached --check`, collect JUnit XML totals from the final focused run, and inspect `git diff --cached --stat`.
- [ ] Commit with `git commit -m "2026-08-10-graph-workspace: Recover failed layout restart"`.
- [ ] Run `git show --format='%H%n%s' --name-only HEAD` and include the commit SHA, exact staged names, RED/GREEN evidence, lifecycle ordering, mutation proof, and test totals in the report.

## Success Gate

After both task-level reviews approve, run a fresh Frontier final review from
`9248c6e227bb82fab8e6139f46db37b62174309f` through the V2 head. Reconcile
`FINAL-F2` and `FINAL-F4`, run fresh graph-plugin verification, and require clean
tracked/index state. Never reopen the terminal parent runs.

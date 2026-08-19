# Graph Workspace Final Remediation Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Recover from the terminal dispatch mismatch by independently auditing the preserved exact workspace-compensation commit, then implement and verify failed-layout restart recovery.

**Architecture:** Task 1 is a no-source-change audit of the preserved Task 1 commit and its exact six-path range, followed by normal independent review. Task 2 modifies only `LayoutSettleLoop` and its three focused test seams to keep failed runs restartable and resubmit the current request after worker restart. The parent blocked runs remain immutable.

**Tech Stack:** Java 8 source/bytecode compatibility, Java 21.0.8 Zulu runtime, Gradle, JUnit 4, AssertJ, Mockito, immutable Graph Workspace values, Swing EDT, serialized lifecycle dispatcher.

## Global Constraints

- Use `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle ...` for every Gradle command; use `gradle`, never Maven or a Gradle wrapper.
- Java source/target remain 8, encoding UTF-8, indentation 4 spaces, and tests JUnit 4/AssertJ/Mockito.
- The clean recovery starts from preserved commit `091e46581950fffeb42087e48d696d43d2158848` in `.worktrees/graph-batch-f-successor-recovery`; the mismatched parent run/worktree and its report are immutable evidence and must not be admitted.
- Task 1 audit range is exactly `af034e5d9bd1c6a58be81ec245835fbff35e1ec8..091e46581950fffeb42087e48d696d43d2158848`; its six-path scope is fixed and audit-only.
- Carried findings are `FINAL-F2` exact workspace compensation and `FINAL-F4` failed-layout restart. Original Task 32 findings F-1/F-2/F-3 are fixed evidence.
- Task 2 may modify only the four listed control/test paths; no `LayoutWorker.java`, `GraphWorkspaceStore.java`, `WorkspaceHistory.java`, API, resources, translations, or unrelated code.
- All Gradle runs use `--rerun-tasks` for fresh evidence. Record JUnit XML failure/error totals, exact staged names, `git diff --cached --check`, production SHA-256 mutation restoration, and `git show --name-only` commit verification.
- Never stage `.codegraph/` or controller artifacts. Do not reopen any terminal SDD state.

## Task 1: Audit preserved exact workspace compensation commit

**Implementer tier:** Capable

**Files:**

- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java`
- Audit only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`
- Audit only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`

**Interfaces:**

- Consumes preserved commit `091e46581950fffeb42087e48d696d43d2158848`, parent `af034e5d9bd1c6a58be81ec245835fbff35e1ec8`, the exact six-path diff, the recovery spec, and the original Task 1 brief.
- Produces one bounded audit report under the run root with `AUDIT: PASS` or `AUDIT: FINDINGS`, exact range/scope evidence, fresh test evidence, mutation/archive evidence, and no deliverable changes.

**Step 1: Read-only audit and independent reproduction.**

- Inspect the exact range and confirm only the six permitted paths changed.
- Inspect history tokens for entry identity, monotonic revision, redo identity/content, ABA rejection, persistence generation, file identity, dirty/debounce state, save/autosave/save-as behavior, and persisted-byte verification.
- Inspect the handler for exact mutation-handle compensation, workspace-first/native-second initial and pending recovery, and no generic `store.undo()` compensation.
- Read the preserved reports only as claims; independently run the focused Task 1 suites from the clean recovery worktree with `--rerun-tasks` and record JUnit XML totals.
- Run the named occurrence/history mutation in a read-only copied source or detached archive only; never mutate the active recovery worktree. Prove the tests fail under the weakened history predicate if the implementation claims that proof.

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*WorkspaceHistoryShould' --tests '*GraphWorkspaceStoreShould' --tests '*ContributorDeletionPlanShould' -PTestLoggingFull --rerun-tasks
failures=$(rg -o 'failures="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
errors=$(rg -o 'errors="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
test "$failures" -eq 0 && test "$errors" -eq 0
```

**Step 2: Commit audit artifact only.**

Write only the audit report under the ignored run root. Assert the active worktree has no tracked source/index changes, run `git diff --check af034e5d9bd1c6a58be81ec245835fbff35e1ec8 091e46581950fffeb42087e48d696d43d2158848`, and do not create a source commit. The controller will independently review this exact range before advancing.

## Task 2: Recover failed layout restart through the live command chain

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:165-190,297-349,530-611, Run state`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**

- Consumes existing `LayoutSettleLoop.start/pause/restart/close`, package-private `FrameStepper`, lifecycle dispatcher, `CanvasState`, `OperationalStatus`, `LayoutFrame`, `GraphUpdateCoordinator.restartLayout`, and `GraphCommandRouter.execute` contracts.
- Preserves public APIs and produces no new command type. Failed current runs remain live/restartable; reentrant restart during failed publication is deferred to the newest control revision; successful worker restart is followed by exactly one submit of the retained request.

**Step 1: TDD RED failed-run and live coordinator tests.**

- Add a parent-compiling `LayoutSettleLoopShould` test whose first submit returns a failed frame and whose second submit returns an idle frame; assert current generation, failed publication, second restart, and second submit.
- Add an EDT-listener reentrant second-restart test, reset-wins test, and close-wins test with explicit lifecycle dispatcher ordering and no sleeps.
- Add a real `GraphUpdateCoordinatorShould` failed-frame -> `restartLayout()` -> recovered-IDLE command-chain regression using package-private coordinator/lifecycle/stepper seams. Keep existing router delegation/result assertions.
- Run the focused tests with `--rerun-tasks`; they must fail behaviorally because the current failed publication terminalizes or holds the run and no replacement submit occurs.

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphUpdateCoordinatorShould' --tests '*GraphCommandRouterShould' -PTestLoggingFull --rerun-tasks
```

**Step 2: Implement deferred failed-run recovery.**

- Keep failed current runs attached and restartable; complete settlement futures after releasing `monitor` without terminalizing the current run solely for failed idle publication.
- In `restart()`, clear pause intent and advance control revision. If failed publication is in flight, record a deferred recovery intent without a second claim. Otherwise claim one recovery frame.
- On the lifecycle dispatcher, revalidate newest token/revision after publication claim release, call `FrameStepper.restart()` once, then call `submit(currentRequest)` for failed/unsubmitted runs. Never call `step()` before replacement submit succeeds.
- Reset, newer start, and close cancel deferred recovery and release claims. A reentrant second restart supersedes the old revision and cannot strand the new request.
- A restart/submit failure leaves the run failed but restartable and emits no stale follow-up operation.

**Step 3: Mutation and fresh verification.**

Before mutation record:

```bash
sha256sum freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java > /tmp/layout-settle-loop.before.sha256
```

Mutate only the failed-run replacement-submit branch so the direct regression fails; restore the inverse immediately, require `sha256sum -c /tmp/layout-settle-loop.before.sha256`, and require `git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java` to be empty of mutant residue.

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutSettleLoopShould' --tests '*GraphUpdateCoordinatorShould' --tests '*GraphCommandRouterShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*LayoutWorkerShould' --tests '*GraphUpdateCoordinatorShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
failures=$(rg -o 'failures="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
errors=$(rg -o 'errors="[0-9]+"' freeplane_plugin_graph/build/test-results/test | awk -F'"' '{sum += $2} END {print sum + 0}')
test "$failures" -eq 0 && test "$errors" -eq 0
```

**Step 4: Exact scope and commit.**

Assert an empty index, stage exactly these four paths, compare sorted staged names exactly, run `git diff --cached --check` and `git diff --check`, verify JUnit XML totals, commit, and verify the result:

```bash
git commit -m "2026-08-10-graph-workspace: Recover failed layout restart"
git show --format='%H%n%s' --name-only HEAD
```

The report must include RED/GREEN, reentrant/reset/close lifecycle evidence, mutation hashes, scope, JUnit totals, and commit SHA.

## Success Gate

After Task 1 audit/review and Task 2 implementation/review complete, run a fresh Frontier final review from original merge base `9248c6e227bb82fab8e6139f46db37b62174309f` through recovery HEAD, reconcile `FINAL-F2` and `FINAL-F4`, run the full graph-plugin suite fresh, and require clean tracked/index state. Never reopen either parent terminal run.

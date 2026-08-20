# Graph Workspace Lifecycle Audit Reissue Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
>
> This fresh continuation follows terminal dispatch-mismatch runs, including
> `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-ui-continuation`.
> Those runs are immutable audit evidence only. The existing lifecycle commit
> `50d0c3bd1502158b19f3ea459cc6cfe5044525d0` remains an unreviewed
> carry-forward candidate and must receive new audit and review evidence.

**Goal:** Independently certify the failed save-close lifecycle correction before operational Graph Workspace UI work resumes.

**Architecture:** The only task is a no-source-change audit of the exact lifecycle correction. It verifies the commit range, behavior boundary, and focused/full test results. The controller then requires independent task review and final branch review before this audit run can complete.

**Tech Stack:** Java 8 source and bytecode, Gradle multi-project build, JUnit 4, AssertJ, Mockito, and the existing Graph Workspace control/status seam.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source compatibility, UTF-8 Java sources, four-space indentation, JUnit 4/AssertJ/Mockito tests, and `gradle`, never Maven or the Gradle wrapper.
- Every build command uses `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu`; do not substitute another JDK.
- This is an audit-only plan: do not alter source, tests, resources, build files, or the carried commit; do not create a source commit.
- Inspect only `DefaultGraphWorkspaceController.java`, `DefaultGraphWorkspaceControllerShould.java`, and Git range `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`.
- The terminal runs at `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-successor`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-post-task-2-mismatch`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-status-lifecycle-recovery`, and `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-ui-continuation` are immutable audit history only. Do not reopen, modify, or cite their child reports as approval evidence.
- Treat `50d0c3bd1502158b19f3ea459cc6cfe5044525d0` as an unreviewed carry-forward candidate. Do not amend, recreate, or revert it; this task and its independent review establish whether its exact range can be retained.
- Every dispatch must persist the renderer-produced prompt before spawn, record the returned session immediately, and compare the completed child transcript's initial user message byte-for-byte with the stored prompt before admitting the child report.

## Task 1: Audit the carried failed-close lifecycle correction

**Implementer tier:** Capable

**Files:**
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`
- Inspect only: Git range `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`

**Interfaces:**
- Consumes: `DefaultGraphWorkspaceController.closeSession`, `WorkspaceSessionStatusPublisher`, `WorkspaceCloseController.saveAndClose/retrySaveAndClose`, the captured store listener registration, and the exact committed range `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`.
- Produces: audit evidence only. It makes no source, test, resource, or build-file change and creates no commit.

- [ ] **Step 1: Establish exact carry-forward scope and source cleanliness**

Verify the exact range contains one commit with subject `2026-08-10-graph-workspace: Preserve status after failed close`, changes only the two listed paths, and has no whitespace errors:

```bash
git log --format='%H%n%s' 4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0
git diff --name-status 4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0
git diff --check 4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0
git status --short
```

The plan/run artifacts are ignored; any reported source change is a finding. Do not use reports from terminal runs as evidence.

- [ ] **Step 2: Inspect the failure and successful-close state transitions**

Read the exact range and verify that `closeSession()` leaves `WorkspaceSessionStatusPublisher` registered while `store.close()` or `discardAndClose()` can fail, calls `reopenAfterSaveFailureLocked()` on a store exception, and calls `closeSessionStatusPublisher(session)` only after a successful store operation. Verify that failure still returns false with the session active, while successful close still aggregates publisher-close failure before remaining teardown.

Verify the regression `keepsStatusLiveAfterFailedSaveCloseAndClosesPublisherAfterRetrySucceeds()` captures the production binding and store listener; after a failing save-close it observes ownership/view/status liveness and `SAVE_FAILED`; after a successful retry it proves exactly one registration close, session release, view close, and no later listener delivery. Verify the existing successful close-order test expects store close before publisher close, then updates, leases, scheduler, and view teardown.

- [ ] **Step 3: Run independent verification without mutating the candidate**

Run both fresh suites with the required JDK:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  -PTestLoggingFull --rerun-tasks
```

Report concrete results, the exact inspected range, and any finding. Do not alter the two source files or create a commit. The following fresh task-reviewer gate independently approves or rejects this exact range.

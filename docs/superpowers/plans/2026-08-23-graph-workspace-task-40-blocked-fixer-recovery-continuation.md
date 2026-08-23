# Graph Workspace Task 40 Blocked-Fixer Recovery Continuation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Complete and independently certify Task 40 command acceptance from the preserved dirty candidate in the existing worktree.

**Architecture:** One fresh capable task corrects the exact preserved candidate for carried findings F-1/F-2/F-3 and commits only the allowlisted acceptance source. A Frontier task review and mandatory Frontier final review independently inspect the resulting range.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito inline mocking, Swing/AWT, Freeplane native map APIs, Graph Workspace production command/router/store APIs.

## Global Constraints

- Use the existing worktree `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-j-task-40` and branch `2026-08-10-graph-workspace-task-40-command-acceptance`; do not create a worktree.
- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the wrapper.
- Use `TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` and `-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40`; host `/tmp` is unavailable for Mockito Byte Buddy attachment.
- Preserve terminal predecessor runs `.superpowers/sdd/batch-j-task-40-pointer-recovery`, `.superpowers/sdd/batch-j-task-40-blocked-fixer-recovery`, `.superpowers/sdd/batch-j-task-40-recovery`, and `.superpowers/sdd/batch-j-task-40`; do not edit their state/progress/reports or cite blocked child reports as approval evidence.
- Preserve the current dirty candidate exactly as successor input: current HEAD `a2a4bf59c641730cccc420b8867dd72b3b8ae731`; source SHA-256 `a72abf81851153d0b27d022a1e51005640e5343c08ff5b596099579b055d4ee8`; one dirty path `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`.
- Findings F-1, F-2, and F-3 remain load-bearing until independently corrected: native operations in scenarios 08/09/11, null native ID/normal save/retry in scenario 16, and sequential first-valid/second-invalid validation for purge and contributor deletion in scenario 22.
- Modify exactly the one Task 40 acceptance source. No production, build, dependency, translation, resource, shared-fixture, or other-test changes.
- Persist full renderer envelopes and exact no-newline pointers; compare pre-spawn and transcript bytes. Any mismatch is terminal.
- Test lanes are serialized with Task 41 lifecycle/resource verification. Do not run Gradle test commands concurrently with Task 41.
- Before the source commit require empty index, exactly the one allowlisted path staged, `git diff --check`, and imperative subject `2026-08-10-graph-workspace:`.
- Final Frontier review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Complete Preserved Command Acceptance Candidate

**Implementer tier:** Capable

**Files:**
- Modify only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only: `.superpowers/sdd/batch-j-task-40-blocked-fixer-recovery/fix-round-1-finding-package.md:1-end`
- Read-only: `.superpowers/sdd/batch-j-task-40-blocked-fixer-recovery/predecessor-fixer-failure.md:1-end`
- Read-only: production command/router/native map APIs referenced by the acceptance source
- Successor report: run-root `task-1-implementer-report.md`

**Interfaces:** Consumes the preserved dirty candidate and production APIs; produces one source commit and fresh red/green evidence for F-1/F-2/F-3.

- [ ] **Step 1: Verify candidate and findings**

Record current HEAD, dirty source hash, diff stat, whitespace, and exact one-path scope. Read the carried finding package and prior-failure context. Do not reset or discard the candidate. Ensure the data-backed temp directory exists.

- [ ] **Step 2: Correct F-1 with production-shaped native behavior**

Complete scenarios 08, 09, and 11 so they use real `FreeplaneMapCommandExecutor`, `MapModel`/`MMapModel`, `NodeModel` trees, map leases/views, native connector/link mutation, contributor deletion, `IUndoHandler`, and map reactivation. Assert same-map connector creation/dirty state/map undo, real cross-map rejection with FPG relationship storage, real contributor deletion and undo/reactivation. Prove the old mock-only boundary would not detect the regression.

- [ ] **Step 3: Correct F-2 with native null-ID atomicity and retry**

Use a null-ID target and transient source key. Assert rejection leaves native ID, connectors, undo, dirty/save, and workspace state unchanged. Perform ordinary native save/ID assignment, retry with the persisted ID, and assert the real connector and applied result. Preserve the existing candidate's valid work.

- [ ] **Step 4: Correct F-3 with falsifiable sequential validation**

For contributor deletion and purge, make the first validation valid and the second immediate validation change. Prove an archived handler with the second validation removed fails. Require rejection and zero store/native mutation for stale, pending, and changed requests, plus accepted purge/undo behavior.

- [ ] **Step 5: Run serial verification after the Task 41 test lane is clear**

Use bounded logs under the data-backed temp directory:

```bash
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull --rerun-tasks
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact XML totals and failures. Remove logs after inspection.

- [ ] **Step 6: Commit and report**

Verify empty index, exact one-path staging, `git diff --cached --check`, and commit with `2026-08-10-graph-workspace: Strengthen command acceptance boundaries`. Write exactly one implementer report with status, changes, red/green evidence, tests, exact commit SHA, and concerns. The source commit is the only accepted source deliverable.

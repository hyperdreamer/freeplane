# Graph Workspace Task 40 Blocked-Fixer Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Finish and independently certify Task 40 command acceptance after the first review-directed fixer was blocked by a full temporary filesystem.

**Architecture:** The successor preserves the partial one-file candidate as intentional dirty preflight input. A fresh capable implementer completes or replaces the candidate, commits exactly the acceptance test, and reports fresh red/green evidence. A Frontier task reviewer then independently reviews the new commit and the three carried findings.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito inline mocking, Swing/AWT, Freeplane map APIs, and existing Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Use `TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` and `-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` for every Gradle/JUnit invocation. Do not rely on `/tmp`, which is full.
- Preserve terminal predecessor `.superpowers/sdd/batch-j-task-40-pointer-recovery` and its `TASK_BLOCKED` state. Do not edit its state/progress/brief/finding package/report or cite its child report as approval evidence; the absent fixer report remains absent.
- The preserved candidate began at `d9858e1ae80eafbd44bff37271ba2a42ea02bd4c` and is exactly one dirty path: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`, SHA-256 `292fda8226b29a045a09de98097ba9ecdb79a408f88bece03f95cd95d4b30463`, 372 insertions/26 deletions, `git diff --check` clean.
- The original implementation commit was `b88081f4d15b36593048cdb5e6e297fc35dc9199`; the three carried findings are F-1, F-2, and F-3 from the Frontier report at the predecessor run root. Read the successor finding package for exact evidence and prior correction history.
- Modify exactly the one Task 40 acceptance test path. No production, build, dependency, translation, resource, shared-fixture, or other-test changes. Do not amend or rewrite prior commits.
- Persist full renderer role envelopes and dispatch short ASCII pointers. Compare candidate/pointer bytes before spawn and raw child-first-message/pointer bytes before admission. Any mismatch is terminal.
- Before commit require empty index, exactly the one allowlisted path staged, `git diff --check`, and subject `2026-08-10-graph-workspace:`.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Complete the preserved Task 40 acceptance candidate

**Implementer tier:** Capable

**Files:**
- Modify only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only predecessor evidence: `.superpowers/sdd/batch-j-task-40-pointer-recovery/fix-round-1-brief.md:1-end`, `.superpowers/sdd/batch-j-task-40-pointer-recovery/fix-round-1-finding-package.md:1-end`, `.superpowers/sdd/batch-j-task-40-pointer-recovery/task-1-review-report.md:1-end`, `.superpowers/sdd/batch-j-task-40-pointer-recovery/fixer-blocked.json:1-end`
- Initial successor report: run-root `task-1-implementer-report.md`

**Interfaces:** Consumes the preserved dirty candidate and production command/router/store/canvas/native map APIs; produces one new acceptance-test commit and fresh evidence for F-1/F-2/F-3.

- [ ] **Step 1: Verify and understand the preserved candidate**

Record `HEAD`, dirty path/hash, diff stat, and whitespace. Read the carried finding package and blocked event. Do not reset, checkout, clean, or discard the candidate. Ensure `/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` exists and is private.

- [ ] **Step 2: Prove F-1 red before correction**

For scenarios 08, 09, and 11, use the current candidate or a disposable archive to demonstrate that the old mocked native boundary cannot detect a real executor/native regression. Then retain or complete a production-shaped fixture using real `FreeplaneMapCommandExecutor`, `MapModel`/`MMapModel` and `NodeModel` trees, available map leases, `ViewMaterializationTracker`, native connector/link mutation, and `IUndoHandler`. Assert actual same-map connector creation/dirty state/map undo, real cross-map rejection while FPG relationship storage still works, and real contributor deletion plus map reactivation.

- [ ] **Step 3: Prove F-2 red before correction**

Use a target with a null native ID and a transient source key. Demonstrate that the old mock-only scenario can pass if the executor silently assigns an ID or mutates native state. Complete the real fixture so rejection leaves ID, connector, undo, dirty/save, and workspace state unchanged; perform the test’s normal map-save/ID-assignment action; reissue and assert the real connector and applied result.

- [ ] **Step 4: Prove F-3 red before correction**

Use sequential coordinator responses. The first projection/state/descriptor validation must be valid; the second immediate validation must change for purge and contributor deletion. Demonstrate the test fails against an archived handler with its second validation removed, then require rejection and zero store/native mutation in the active test.

- [ ] **Step 5: Run serial verification with data-backed temp storage**

Use bounded logs under the data-backed temp directory and remove them after inspection:

```bash
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull --rerun-tasks
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact XML totals and every relevant failure. Do not run commands concurrently.

- [ ] **Step 6: Commit the completed one-file correction**

After green tests, verify no index residue, stage exactly the acceptance test, run `git diff --cached --check`, and commit with `2026-08-10-graph-workspace: Strengthen command acceptance boundaries`. Write exactly one implementer report with `STATUS: DONE` or `DONE_WITH_CONCERNS`, test counts, candidate/finding evidence, exact commit SHA, and any real residual concern. The report is the only successor run artifact besides the source commit.

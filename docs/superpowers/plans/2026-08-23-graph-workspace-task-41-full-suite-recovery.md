# Graph Workspace Task 41 Full-Suite Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Resolve the reproducible Task 41 full-suite cold-reload failure and re-certify lifecycle/reload acceptance in the existing worktree.

**Architecture:** One capable implementer repairs only the Task 41 acceptance fixture's shared headless menu-state boundary, then runs serialized focused/full tests and a disposable mutant probe. Frontier task and final reviews inspect the resulting range.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Swing/AWT, Freeplane headless starter/controller/map loader, Graph Workspace production APIs.

## Global Constraints

- Use only `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-j-task-41` on branch `2026-08-10-graph-workspace-task-41-lifecycle-acceptance`; no new worktree.
- Use `/home/guest/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the wrapper.
- Use `TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41` and `-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41`; do not use host `/tmp`.
- Task 40's serial verification must be complete before any Gradle command in this run. Wait for `/data/home/guest/.tmp/freeplane-graph-batch-j-task-41/task40-verification-clear`.
- Preserve predecessor run roots `.superpowers/sdd/batch-j-task-41`, `.superpowers/sdd/batch-j-task-41-recovery`, and their reports. The current implementation range is immutable verification input; no predecessor report is approval evidence.
- Current input is clean HEAD `577074cf9f68e8fd59e61ff0a3e0e8452e51552c`; implementation commit `dfcd0f99010ad7dc04c167caecba002115a230f7`; source hashes: `GraphWorkspaceColdReloadShould.java` `ebf21666566ed9fbc660582b4e1fdbce3ba7b7f2f78c39775934331dc8d02320`, `GraphWorkspaceLifecycleShould.java` `e2a1f5ddfa5ab3e2cc1517567b3322e833c6a00646b5e88f168ac0a33df420d5`.
- Findings remain load-bearing until independently closed: reproducible full-suite cold reload null-menu failure; non-reproducible MapLeaseManager timing instability; callback fixed-sleep residual risk.
- Modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java` unless an independent review proves the second Task 41 acceptance file is necessary. No production, build, dependency, translation, resource, shared-fixture, or other-test changes.
- Persist full role envelopes and exact no-trailing-newline pointer prompts. Compare pre-spawn bytes and completed transcript bytes; mismatch is terminal.
- Before source commit require empty index, exact allowlisted staging, `git diff --check`, and subject beginning `2026-08-10-graph-workspace:`.
- Final Frontier review range: `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Repair and Verify Shared Headless Menu Fixture

**Implementer tier:** Capable

**Files:**
- Modify only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:534-988` unless evidence requires a tightly scoped companion change
- Read-only: `.superpowers/sdd/batch-j-task-41-recovery/task-1-audit-report.md:1-end`
- Read-only: `freeplane/src/main/java/org/freeplane/view/swing/ui/UserInputListenerFactory.java:95-143`
- Successor report: run-root `task-1-implementer-report.md`

**Interfaces:** Consumes the clean Task 41 acceptance implementation and shared headless controller state; produces one test-only fixture correction, focused/full test evidence, and one source commit.

- [ ] **Step 1: Reproduce and isolate the failure after Task 40 gate**

Before editing, record `git status`, implementation/source hashes, and the exact previous full-suite failure. After `/data/home/guest/.tmp/freeplane-graph-batch-j-task-41/task40-verification-clear` exists, run the focused cold-reload/lifecycle command and then the full `:freeplane_plugin_graph:test` serially with bounded logs. Confirm the focused test remains green and the full cold-reload failure is reproducible before changing the fixture.

- [ ] **Step 2: Correct only the fixture boundary**

Make every `FreeplaneScope` load start from a deterministic valid `genericMenuStructure` `Entry` regardless of whether a controller is reused or newly created. Preserve any preexisting shared state needed by later tests and restore it during close. Keep production `MapLoader.load(...).withView()` and all lifecycle/reload assertions unchanged. Do not repair this by weakening assertions, skipping the full test, changing production code, or globally serializing unrelated tests.

- [ ] **Step 3: Verify focused and full suites serially**

```bash
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41" gradle --no-daemon :freeplane_plugin_graph:test --tests '*GraphWorkspaceColdReloadShould' --tests '*GraphWorkspaceLifecycleShould' -PTestLoggingFull --rerun-tasks
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-41" gradle --no-daemon :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact XML totals, skipped count, failures, errors, and whether the full suite is green. A repeatable full-suite failure remains blocking.

- [ ] **Step 4: Run a falsifiable disposable mutant**

In an archive outside the worktree, remove the deterministic menu-root correction while preserving the rest of the fixture. Run the full-suite or a deterministic reproducer that includes the prior shared-controller setup and prove the cold-reload load path fails. Delete the archive/logs and verify active source hashes afterward.

- [ ] **Step 5: Commit and report**

Verify empty index, exactly the allowlisted acceptance path staged, `git diff --cached --check`, and commit with `2026-08-10-graph-workspace: Stabilize headless reload acceptance`. Write one report with status, source hashes, reproduction and green test totals, mutant red evidence, scope, commit SHA, and residual observations. Return exactly one status.

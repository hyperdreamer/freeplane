# Graph Workspace Task 40 Verification Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Verify and certify the preserved corrected Task 40 command acceptance candidate in the existing worktree.

**Architecture:** One capable verification implementer audits the dirty candidate, runs serialized focused/full graph-plugin tests and falsifiable disposable mutant probes, makes only evidence-driven acceptance-source corrections, and commits the one allowlisted source. Frontier task and final reviews independently inspect the complete range.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito inline mocking, Swing/AWT, Freeplane native map APIs, Graph Workspace command/router/store APIs.

## Global Constraints

- Use only `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-j-task-40` on branch `2026-08-10-graph-workspace-task-40-command-acceptance`; no new worktree.
- Use `/home/guest/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the wrapper.
- Use `TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` and `-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40`; do not use host `/tmp`.
- Task 41's serialized verification lane is complete; no Gradle test command may run concurrently with another verification command.
- Preserve all predecessor run roots and reports. The preserved source candidate is the exact intentional dirty input: HEAD `577961a7b7f86cf032cc68f1d6058f055dc27c07`, source SHA-256 `f2c8323918ed48bfc31aebff0478541d0c249426ce5a78da0b8bdba8cf62f164`.
- Carried findings F-1/F-2/F-3 remain load-bearing until evidence closes them: real native operations in scenarios 08/09/11, null native ID atomicity and ordinary-save retry in scenario 16, and sequential first-valid/second-changed validation for purge/contributor deletion in scenario 22.
- Modify exactly `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`. No production, build, dependency, translation, resource, shared-fixture, or other-test changes.
- Persist full role envelopes and exact no-trailing-newline pointers. Compare bytes before spawn and against the child transcript; mismatch is terminal.
- Before source commit require empty index, exact one-path staging, `git diff --check`, and subject beginning `2026-08-10-graph-workspace:`.
- Final Frontier review range: `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Verify and Certify Preserved Command Acceptance

**Implementer tier:** Capable

**Files:**
- Modify only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only: `.superpowers/sdd/batch-j-task-40-continuation/task-1-implementer-report.md:1-end`
- Read-only: `.superpowers/sdd/batch-j-task-40-blocked-fixer-recovery/fix-round-1-finding-package.md:1-end`
- Read-only: production APIs and prior acceptance tests
- Successor report: run-root `task-1-implementer-report.md`

**Interfaces:** Consumes the pinned dirty candidate and production APIs; produces one verified acceptance-source commit and exact test/mutant evidence.

- [ ] **Step 1: Pin candidate and inspect boundaries**

Record current HEAD, source SHA-256, one-path scope, diff check, and carried report evidence. Confirm all F-1/F-2/F-3 assertions are real and falsifiable before running tests.

- [ ] **Step 2: Run serial focused verification**

```bash
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull --rerun-tasks
```

Record exact test totals, failures, errors, skipped count, and build result.

- [ ] **Step 3: Run serial full verification**

```bash
TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40 JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" GRADLE_OPTS="-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40" gradle --no-daemon :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact XML totals and failures. A red full gate is a blocking concern, not an approval.

- [ ] **Step 4: Run falsifiable mutant probes**

In disposable archives outside the worktree, remove only the mechanism under test: real native boundary setup for F-1, the null-ID atomic/retry path for F-2, and the second changed validation read for F-3. Each focused test must turn red with exactly the relevant failure. Delete archives and logs afterward and verify active source hashes are unchanged.

- [ ] **Step 5: Correct evidence-backed gaps only**

If focused/full or mutant evidence reveals a gap, edit only the allowlisted acceptance source and repeat the serial verification. Do not weaken assertions to accommodate failures.

- [ ] **Step 6: Commit and report**

Before committing, require empty index, exactly one allowlisted source staged, `git diff --cached --check`, and an imperative `2026-08-10-graph-workspace:` subject. Write one report with status, exact candidate/source hashes, native boundary summary, red/green evidence, mutant evidence, test totals, commit SHA, and concerns. Return exactly one status.

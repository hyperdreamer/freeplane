# Graph Workspace Task 40 Pointer-Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the committed Task 40 command, security, and UI acceptance test using byte-stable pointer dispatch.

**Architecture:** The sole task is a read-only audit of the immutable Task 40 implementation. A fresh task review follows, and only an admitted finding may open a one-file fix round. Every child receives a full renderer envelope through a short persisted pointer.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, and existing Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal runs `.superpowers/sdd/batch-j-task-40` and `.superpowers/sdd/batch-j-task-40-recovery`; never edit or cite their child reports, prompts, transcripts, state, or audit projection as successor evidence.
- Audit immutable commit `b88081f4d15b36593048cdb5e6e297fc35dc9199`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, subject `2026-08-10-graph-workspace: Verify graph command acceptance`, changing exactly `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`.
- The immutable test-file SHA-256 is `c1fb2e2fd402be89b90f48e4a87d2cc6bde87a3b1b6cc457392cdc3c7d9cd3f7`.
- The initial audit is read-only. A controller-authorized fix round may modify only the one Task 40 test path; no production, build, translation, dependency, resource, shared fixture, or compatibility fallback is allowed.
- Persist the full renderer output as a role envelope and dispatch only a short ASCII pointer to it. Before spawn, compare pointer/candidate bytes and hashes. Before admission, extract the raw child first message and compare it byte-for-byte with the pointer. Any mismatch is terminal.
- Preserve command/router/store/canvas production boundaries, safe-label confidentiality, exact map identity, displayed generation and descriptor guards, map-owned undo, and workspace history.
- Before a fix commit, require empty index, exact one-file stage, `git diff --check`, and a `2026-08-10-graph-workspace:` subject.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Audit the committed Task 40 acceptance test

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only: production paths referenced by the test under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:1-end`
- Read-only: `docs/superpowers/specs/2026-08-22-graph-workspace-task-40-pointer-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Initial audit writes only its report and creates no Git commit.

**Interfaces:** Consumes immutable Task 40 commit `b88081f4d15b36593048cdb5e6e297fc35dc9199`; produces fresh identity, scenario, test, and confidentiality-mutant evidence without source changes.

- [ ] **Step 1: Verify immutable identity and scope**

Verify current `HEAD`, clean source/index, exact implementation parent/subject, one-file source range, `git diff --check`, known file hash, and both predecessor terminal states. Do not read or cite predecessor reports or transcripts.

- [ ] **Step 2: Audit behavior and falsifiability**

Inspect production boundaries and assertions for scenarios 08, 09, 11, 14, 16, 17, 20, 21, 22, 24, and 25; live-target Save As rejection; separate workspace histories; strict performance report consumption; safe search/tooltip confidentiality; stale/pending purge and contributor guards; map-view reuse; map-owned undo; and workspace undo. Distinguish real production assertions from mock-only tautologies and record concrete file:line defects.

- [ ] **Step 3: Run fresh focused and full verification**

Use bounded temporary logs and remove them after inspection:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact totals and report any load-bearing failure.

- [ ] **Step 4: Reproduce confidentiality mutant read-only**

Create a disposable archive from `b88081f4d15b36593048cdb5e6e297fc35dc9199`; mutate only archived `GraphSearchModel` so persistent source identity enters indexed safe text; run the locked-content acceptance test and require failure exposing `LOCKED_SECRET_SENTINEL`. Delete archive/logs and reconfirm active `HEAD`, status, and test SHA.

- [ ] **Step 5: Write the audit report**

Write exactly one report at the dispatched path. `DONE` requires all gates. Include `CHANGES: no source changes`, exact SHA/range/hash, fresh tests, mutant evidence, residual findings if any, unchanged `HEAD`, and confirmation that both predecessor child outputs were excluded from approval evidence.

- [ ] **Step 6: Preserve source**

Confirm no source/test/index changes, no new source commit, and no temporary residue. The report is the only run deliverable; a later fix round may change only the explicit test path after an admitted review finding.

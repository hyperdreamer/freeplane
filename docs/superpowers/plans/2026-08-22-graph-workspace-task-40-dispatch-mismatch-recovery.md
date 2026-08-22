# Graph Workspace Task 40 Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the already committed Task 40 command, security, and UI acceptance test after a terminal dispatch mismatch, then review the complete successor branch.

**Architecture:** The sole task is a read-only audit of the exact committed Task 40 test range. It does not admit the blocked child report or transcript and does not change source. A fresh task reviewer evaluates the audited commit. Only a reviewer-authorized fix round may modify the single Task 40 test path; the controller then performs a Frontier final review.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, and the existing Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu` for Gradle and JDK commands; use `gradle`, never Maven or the Gradle wrapper.
- Preserve the terminal predecessor run at `.superpowers/sdd/batch-j-task-40`; its state, reports, prompts, transcript, and audit projection are diagnostic history only and must not be edited or cited as successor evidence.
- The immutable implementation commit under audit is `b88081f4d15b36593048cdb5e6e297fc35dc9199`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, subject `2026-08-10-graph-workspace: Verify graph command acceptance`, changing exactly `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`.
- The immutable Task 40 test file at the successor baseline has SHA-256 `c1fb2e2fd402be89b90f48e4a87d2cc6bde87a3b1b6cc457392cdc3c7d9cd3f7`.
- The initial implementer task is read-only: do not edit, stage, amend, reset, checkout, or commit any source/test/build file. Write only the fresh report under the successor run root.
- A controller-authorized fix round for this task may modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`; no production code, build file, translation, shared fixture, dependency, or compatibility fallback is allowed.
- Every successor child receives controller-rendered prompt bytes persisted before spawn. Compare the completed child’s initial user message byte-for-byte with the stored prompt before admitting its report; a mismatch is terminal for that successor run.
- Use the production command/router/store/canvas boundaries. Preserve confidentiality, exact map identity, generation/descriptor rejection, map-owned undo, workspace history, and safe-label rules.
- Before any source-changing fix commit, require an empty index, stage exactly the one allowlisted test path, run `git diff --check`, and use a subject beginning `2026-08-10-graph-workspace:`.
- The final review must cover the complete successor range from `834d381f724c8606034a6bc5c878bb91d105cb63` through final `HEAD`.

## Task 1: Audit the committed Task 40 acceptance test

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-22-graph-workspace-task-40-dispatch-mismatch-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- No source deliverable or Git commit is permitted during the initial audit; write only the normal implementer report under the dispatched run root.

**Interfaces:**
- Consumes the immutable Task 40 commit `b88081f4d15b36593048cdb5e6e297fc35dc9199` and production APIs used by the acceptance test.
- Produces a bounded audit report with exact range/file/hash evidence, focused/full test results, security-mutant evidence, and residual findings.
- Must preserve `HEAD`, index, branch, all source/test bytes, and the terminal predecessor run.

- [ ] **Step 1: Establish immutable identity and scope**

Run `git rev-parse HEAD`, `git status --porcelain=v1 --untracked-files=all`, `git show -s --format='%H%n%P%n%s' b88081f4d15b36593048cdb5e6e297fc35dc9199`, `git diff --name-status 834d381f724c8606034a6bc5c878bb91d105cb63..b88081f4d15b36593048cdb5e6e297fc35dc9199`, `git diff --check 834d381f724c8606034a6bc5c878bb91d105cb63..b88081f4d15b36593048cdb5e6e297fc35dc9199`, and `sha256sum freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`. Require the exact parent, subject, one-path allowlist, known file hash, clean source/index boundary, and unchanged predecessor run.

- [ ] **Step 2: Audit coverage and falsifiability**

Read the acceptance test and its production call boundaries. Verify scenarios 08, 09, 11, 14, 16, 17, 20, 21, 22, 24, and 25; live-target Save As rejection; separate workspace histories; strict performance report consumption; safe-label/search confidentiality; stale/pending purge and contributor rejection; map-view reuse; and map-owned versus workspace undo routing. Confirm assertions exercise production behavior rather than only asserting mocks, and record concrete file:line evidence for any gap.

- [ ] **Step 3: Run fresh focused and full verification**

Run with output redirected to temporary logs and remove each log after inspection:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exit status and exact JUnit totals. A load-bearing failure is reported, not normalized away as environment noise.

- [ ] **Step 4: Reproduce the confidentiality mutant read-only**

Create a disposable archive under `/tmp` from `b88081f4d15b36593048cdb5e6e297fc35dc9199`, copy only the audited test file if needed, mutate only the archived `GraphSearchModel` to append projected persistent source identity to indexed safe text, and run the specific locked-content acceptance test. Require the mutant to fail by exposing `LOCKED_SECRET_SENTINEL`. Delete the archive/logs and re-confirm active `HEAD`, status, and file SHA unchanged.

- [ ] **Step 5: Write the audit report and preserve the branch**

Write exactly one report at the dispatched path with `STATUS: DONE` only if all audit gates pass. Include `CHANGES: no source changes`, exact commit/file/hash evidence, test results, mutant result, residual audit findings if any, current `HEAD`, and a statement that the blocked predecessor report/transcript were not used as evidence. Do not stage or commit.

- [ ] **Step 6: Verify no source commit was created**

Confirm the index is empty, `git status --porcelain=v1 --untracked-files=all` contains no source/test changes, and `git rev-parse HEAD` remains the committed recovery-plan object. The report is the only successor deliverable. A later controller-authorized fix round may change only the one explicit test path, with a fresh commit and re-review.

# Graph Workspace Task 39 Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the already committed Task 39 model acceptance test after a terminal dispatch mismatch.

**Architecture:** The sole task is a read-only audit of the immutable Task 39 commit. A fresh task reviewer evaluates the exact implementation range, and only a reviewer-authorized fix round may modify the single Task 39 test path. Every dispatch uses a persisted renderer envelope through a byte-stable short pointer.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, Freeplane map APIs, and existing Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal predecessor run `.superpowers/sdd/batch-j-task-39`; never edit or cite its report, prompt, transcript, state, or audit projection as successor evidence.
- Audit immutable commit `0ec5e71b9d585ca6e5ecacb836ab4cdcde562c43`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, subject `2026-08-10-graph-workspace: Verify graph model acceptance`, changing exactly `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`.
- The immutable test-file SHA-256 is `da75e13932703d7e302c5ce10379096ddbade991165c6c8a4b0baddd5a79c287`.
- The initial audit is read-only. A controller-authorized fix round may modify only the one Task 39 test path; no production, build, translation, resource, dependency, shared fixture, fallback, fingerprint, or provenance heuristic is allowed.
- Persist the full `sdd-state render-prompt` output as a role envelope. Persist and dispatch a short ASCII pointer requiring the child to read that envelope. Compare candidate/pointer bytes before spawn and raw child-first-message/pointer bytes before report admission. Any mismatch is terminal.
- Preserve EDT-safe source-map access, safe-label confidentiality, traversal identity, production persistence/projection/geometry/marker composition, and the recorded strict performance prerequisite.
- Before any fix commit, require an empty index, exact one-path stage, `git diff --check`, and a `2026-08-10-graph-workspace:` subject.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Audit the committed Task 39 acceptance test

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-end`
- Read-only: production paths referenced by that test under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:1-end`
- Read-only: `docs/superpowers/specs/2026-08-22-graph-workspace-task-39-dispatch-mismatch-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-end`
- Initial audit writes only its report under the successor run root and makes no Git commit.

**Interfaces:** Consumes immutable Task 39 commit `0ec5e71b9d585ca6e5ecacb836ab4cdcde562c43`; produces fresh identity, scenario, test, and falsifiability evidence without changing source.

- [ ] **Step 1: Verify immutable identity and scope**

Verify current `HEAD`, clean source/index boundary, exact commit parent/subject, one-path diff, `git diff --check`, and known test SHA. Confirm the predecessor run is terminal and unchanged, but do not read or cite its report or transcript.

- [ ] **Step 2: Audit scenario coverage**

Inspect the test and production call boundaries for scenarios 01, 02, 03, 04, 05, 06, 07, 10, 12, 13, 15, 18, 19, 23, 26, 27, 28, and 29. Verify persistence/reopen, moved relative paths, structural leaves/groups, hidden/collapsed behavior, marker nesting/stock-reader/cloud composition, connector direction/multiplicity, dormant/reactivated maps, pins/settling, boundary tiers, safe labels, and strict performance PASS consumption. Record concrete file:line residual defects.

- [ ] **Step 3: Run fresh verification**

Redirect verbose output to temporary logs and remove them after inspection:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceModelAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact test/failure/error/skip totals. Report any load-bearing failure.

- [ ] **Step 4: Verify confidentiality and performance prerequisites read-only**

Confirm no test assertion or helper exposes raw/unreachable content, calls forbidden ID manufacture/flat lookup, or duplicates production projection logic. Verify the performance scenario reads the committed report and requires its strict PASS/result contract rather than rerunning or weakening it. Use a disposable archive mutant only if needed to prove an assertion is falsifiable; remove all archive/log residue.

- [ ] **Step 5: Write the audit report**

Write exactly one report at the dispatched report path. Return `DONE` only if every gate passes. Include `CHANGES: no source changes`, exact SHA/range/hash, focused/full results, scenario evidence, residual audit findings if any, unchanged `HEAD`, and confirmation that predecessor child artifacts were not used as approval evidence.

- [ ] **Step 6: Preserve source**

Confirm no source/test/index changes, no new source commit, no temporary archive/log residue, and `HEAD` remains the committed recovery-plan object. A later SDD fix round may change only the one explicit test path after an admitted review finding.

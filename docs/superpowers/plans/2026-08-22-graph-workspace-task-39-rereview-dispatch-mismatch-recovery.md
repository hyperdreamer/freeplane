# Graph Workspace Task 39 Re-review Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the fixed Task 39 model acceptance test after the terminal re-review dispatch mismatch.

**Architecture:** The sole task is a fresh read-only audit of current HEAD. It verifies the four previously admitted findings and the complete model acceptance boundary. A fresh task reviewer then evaluates the audit and exact implementation range. Only an admitted finding may authorize a fix round limited to the single acceptance test path. Every child dispatch uses a persisted role envelope and a byte-stable pointer with no trailing newline.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, Freeplane map APIs, and Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal runs `.superpowers/sdd/batch-j-task-39` and `.superpowers/sdd/batch-j-task-39-recovery`; never edit or cite their reports, prompts, transcripts, state, or audit projections as successor approval evidence.
- Audit current fixed HEAD `935650a75f1ec093d3a90884dccfaffd1772606b`, whose parent is `35fcb32bd296e9339aae5195dc8d9c1bd03ee9f0`; the final review range remains `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.
- Current fixed source scope is exactly `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`.
- Initial audit is read-only. A controller-authorized fix round may modify only that acceptance test path; no production, build, translation, resource, dependency, shared fixture, fallback, fingerprint, or provenance heuristic changes.
- Persist the complete `sdd-state render-prompt` output as a role envelope. Persist and dispatch an ASCII pointer without a trailing newline. Compare candidate/pointer bytes before spawn and raw child-first-message/pointer bytes before report admission. Any mismatch is terminal.
- Use `/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-rereview` for `TMPDIR`, Java temporary files, Gradle logs, and disposable evidence. Verification commands run serially.
- Preserve EDT-safe source-map access, safe-label confidentiality, traversal identity, production persistence/projection/geometry/marker composition, and the strict recorded performance prerequisite.
- Before any authorized fix commit, require an empty index, exact one-path stage, `git diff --check`, and a `2026-08-10-graph-workspace:` subject.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Fresh Audit Of The Fixed Model Acceptance Test

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-end`
- Read-only: production paths referenced by that test under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:1-end`
- Read-only: `docs/superpowers/specs/2026-08-22-graph-workspace-task-39-rereview-dispatch-mismatch-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: the single Task 39 acceptance path
- Initial audit writes only its report under the successor run root and makes no Git commit.

**Interfaces:** Consumes current fixed HEAD `935650a75f1ec093d3a90884dccfaffd1772606b`; produces fresh identity, scenario, assertion-sensitivity, and test evidence without changing source.

- [ ] **Step 1: Verify current identity and scope**

Verify current HEAD, clean source/index boundary, exact parent/subject, one-path fixed range, `git diff --check`, and current test-file hash. Treat blocked predecessor reports and the inadmissible re-review report only as historical context; do not use them as approval evidence.

- [ ] **Step 2: Audit all named scenarios and prior findings**

Inspect scenarios 01, 02, 03, 04, 05, 06, 07, 10, 12, 13, 15, 18, 19, 23, 26, 27, 28, and 29. Verify scenario 15 parses the authoritative strict CSV row and limits; scenario 19 rejects MAP_TWO content for loading/missing states; scenario 23 captures the actual toggled clone map through `MapLease` and `MapSnapshotFactory`; scenario 27 crosses stock `MapReader`/`MapWriter` with Graph Group absent and production Graph Group reload enabled. Also verify safe labels, persistence/reopen, moved relative paths, structural leaves/groups, hidden/collapsed behavior, connector direction/multiplicity, dormant/reactivated maps, pins/settling, boundary tiers, and strict performance consumption.

- [ ] **Step 3: Run fresh serial verification**

Use data-backed temporary storage and remove logs after inspection:

```bash
TMP=/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-rereview
mkdir -p "$TMP"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test --tests '*GraphWorkspaceModelAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact JUnit XML totals and any failures/errors/skips.

- [ ] **Step 4: Verify falsifiability and confidentiality**

Confirm no raw/unreachable content is exposed, no forbidden ID manufacture or flat lookup is used, and helpers do not duplicate production projection/persistence logic. Check that the fixed assertions are sensitive to the four prior mechanisms. Use only disposable read-only mutants if needed and remove all residue.

- [ ] **Step 5: Write the audit report**

Write exactly one report at the dispatched report path. Include `STATUS`, current SHA/range/hash, exact focused/full totals, source-backed findings for all four prior corrections, any residual findings, unchanged source status, and confirmation that predecessor artifacts were not used as approval evidence.

- [ ] **Step 6: Preserve source**

Confirm no source/test/index changes, no temporary logs or archives remain, and HEAD remains `935650a75f1ec093d3a90884dccfaffd1772606b` unless a later authorized fix round changes only the one allowlisted test path.

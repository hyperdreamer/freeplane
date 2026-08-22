# Graph Workspace Task 39 Final Review Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Freshly certify the complete Task 39 acceptance range after the terminal final-review dispatch mismatch.

**Architecture:** One read-only audit task independently verifies current HEAD and the complete range. A fresh Frontier task review evaluates that audit. Once the task is approved, a separate mandatory Frontier final review evaluates the same complete range. No source changes are authorized unless a fresh review admits a finding.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, Freeplane map APIs, and Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal runs `.superpowers/sdd/batch-j-task-39`, `.superpowers/sdd/batch-j-task-39-recovery`, `.superpowers/sdd/batch-j-task-39-rereview-recovery`, and `.superpowers/sdd/batch-j-task-39-fixer-recovery`; never edit or cite their terminal final-review report, prompt, transcript, state, or audit projection as approval evidence.
- Audit source baseline `f2b16e5bf508108fa6a54cb8f0d8193d174e4bcb`; the mandatory final review covers the complete `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD` range, including successor recovery metadata commits.
- The current source deliverable is `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`; initial audit is read-only. Any authorized fix may change only this one acceptance path.
- Persist complete renderer envelopes and no-trailing-newline pointers. Construct spawn prompts by reading the persisted pointer bytes. Compare pre-spawn candidate/pointer and post-spawn first-user-message bytes. Any mismatch is terminal.
- Use `/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-final-recovery` for Java/Gradle temp state and logs. Verification is serial.
- Preserve Java 8 compatibility, safe labels/confidentiality, traversal identity, production persistence/projection/layout/marker composition, strict performance report consumption, and exact Git scope.
- Before any authorized source fix commit, require empty index, exact one-path stage, `git diff --check`, and an imperative `2026-08-10-graph-workspace:` subject.
- Final Frontier review must cover the exact merge-base-to-HEAD range above.

## Task 1: Fresh Full-Range Audit

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-end`
- Read-only: all production paths referenced by that test under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:1-end`
- Read-only: all recovery plans/designs in the exact range
- Modify only in a later controller-authorized fix round: the single acceptance test path
- Initial audit writes exactly one report under the successor run root and makes no source commit.

**Interfaces:** Consumes current HEAD and the exact full range; produces fresh identity, scope, scenario, test, and falsifiability evidence without source changes.

- [ ] **Step 1: Verify exact range and scope**

Verify current HEAD, merge-base, every commit in the range, source hash, one source-path implementation boundary, clean index/source status, `git diff --check`, and recovery-document coherence. Do not use any terminal final-review verdict as approval evidence.

- [ ] **Step 2: Audit model acceptance contracts**

Inspect scenarios 01, 02, 03, 04, 05, 06, 07, 10, 12, 13, 15, 18, 19, 23, 26, 27, 28, and 29. Verify strict performance CSV parsing, persistence/reopen and moved relative paths, structural leaves/groups and hidden/collapsed behavior, connector direction/multiplicity, dormant/reactivated maps, pins/settling, MAP_TWO enclosure and active/dormant pin behavior, native clone snapshots, stock Reader/Writer persistence, marker/cloud rendering, safe labels, confidentiality, and traversal identity. Confirm helpers do not duplicate production projection/persistence logic.

- [ ] **Step 3: Run fresh serial verification**

Use the data-backed temp directory and remove logs after inspection:

```bash
TMP=/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-final-recovery
mkdir -p "$TMP"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test --tests '*GraphWorkspaceModelAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact JUnit XML totals, failures, errors, and skips. A transient timing failure must be investigated and the complete command rerun before a success claim.

- [ ] **Step 4: Write the audit report**

Write exactly one report at the dispatched path with `STATUS`, `CHANGES: no source changes`, exact range/hash/scope, source-backed scenario evidence, fresh test totals, residual findings/concerns, and confirmation that terminal final-review artifacts were not used as approval evidence.

- [ ] **Step 5: Preserve source**

Confirm no source/index changes, no temporary residue, and current HEAD remains unchanged. The controller then performs fresh task review and final review.

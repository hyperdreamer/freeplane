# Graph Workspace Task 39 Fixer Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the preserved Scenario 19 F-1 fixer commit after a terminal dispatch mismatch.

**Architecture:** The sole task is a fresh read-only audit of the preserved candidate. A fresh task reviewer evaluates the exact candidate range. Only a fresh admitted finding may authorize a fix round limited to the Task 39 acceptance test. Every child uses a persisted renderer envelope and a no-trailing-newline pointer whose exact bytes are checked against the first child message.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, Freeplane map APIs, and Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal runs `.superpowers/sdd/batch-j-task-39`, `.superpowers/sdd/batch-j-task-39-recovery`, and `.superpowers/sdd/batch-j-task-39-rereview-recovery`; never edit or cite their reports, prompts, transcripts, state, or audit projections as approval evidence.
- Current candidate source commit is `e89f3b8ee87787cfa9c587d9ac2693a8edd82bc1`, parent `49de5ee70d93b6ef833fc40471ce5882f86359e1`; original merge base is `834d381f724c8606034a6bc5c878bb91d105cb63`.
- Current worktree may contain candidate commits that have not passed fresh review. Require explicit preflight approval before dispatch.
- The only source file that may change in an authorized fix round is `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`.
- Persist complete role envelopes. Persist and dispatch pointers without trailing newlines. Read the pointer file bytes to construct the spawn prompt; do not manually retype paths. Compare candidate/pointer and exact child-first-message bytes before report admission. Any mismatch is terminal.
- Use `/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-fixer-recovery` for Java/Gradle temp state and logs. Verification is serial.
- Preserve safe labels, confidentiality, traversal identity, production persistence/projection/layout/marker composition, and the strict recorded performance prerequisite.
- Before an authorized source fix commit, require empty index, exact one-path stage, `git diff --check`, and a `2026-08-10-graph-workspace:` subject.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Fresh Audit Of The Preserved Scenario 19 Fix

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-end`
- Read-only: production paths referenced by Scenario 19 under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: current strict performance report and recovery design files
- Modify only in a later controller-authorized fix round: the one Task 39 acceptance test path
- Initial audit writes exactly one report under the successor run root and makes no source commit.

**Interfaces:** Consumes preserved candidate `e89f3b8ee87787cfa9c587d9ac2693a8edd82bc1`; produces fresh source, test, and falsifiability evidence.

- [ ] **Step 1: Verify candidate identity and preflight boundary**

Verify current HEAD, exact candidate parent/subject, one-path candidate range, source hash, clean index/source status, and that the candidate is intentionally unreviewed carry-forward. Do not cite terminal predecessor artifacts as approval evidence.

- [ ] **Step 2: Audit Scenario 19 and retained Task 39 contracts**

Verify the candidate makes MAP_TWO a real enclosure with a persisted child and a real MAP_TWO pin; active MAP_TWO projection contains the child, enclosure, and exact active pin; loading/missing contain no visible MAP_TWO nodes/enclosures/edges/endpoints/contributors/prominence or active MAP_TWO pin while retaining exactly one dormant MAP_TWO pin. Confirm earlier F-1 through F-4 changes remain source-backed and no direct regression exists.

- [ ] **Step 3: Run fresh serial verification**

```bash
TMP=/data/home/guest/.tmp/freeplane-graph-batch-j-task-39-fixer-recovery
mkdir -p "$TMP"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test --tests '*GraphWorkspaceModelAcceptanceShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" TMPDIR="$TMP" gradle --no-daemon -Djava.io.tmpdir="$TMP" :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact XML totals, inspect logs, and remove logs afterward.

- [ ] **Step 4: Write the audit report**

Write exactly one report at the dispatched path with `STATUS`, candidate identity/hash/range, focused/full totals, source evidence for Scenario 19 and retained boundaries, residual findings if any, and confirmation that predecessor artifacts were not used as approval evidence.

- [ ] **Step 5: Preserve source**

Confirm no source/index changes and no temporary residue. The candidate commit must remain unchanged until a fresh review admits it.

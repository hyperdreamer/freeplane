# Graph Workspace Group-Only Boundaries Continuation 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the group-only-boundaries canvas scope (three test files the canvas task could not stage under its allowlist) and independently audit the carried-forward canvas commit `ed1c324883`, then finish with the mandatory final review.

**Architecture:** The canvas task of the continuation run reported `DONE_WITH_CONCERNS` with a scope concern: `GraphWorkspaceModelAcceptanceShould`, `GraphWorkspaceCommandAcceptanceShould`, and `GraphWorkspacePerformanceDiagnostic` needed group-only-model updates but were outside the task's Files allowlist, so the implementer left them unstaged and the committed tree alone does not compile the module's test sources. That run is digest-frozen (its plan file cannot change under a pinned digest); it is preserved byte-for-byte and its reports are not evidence. This run's single task stages exactly those three files (their content is already prepared in the worktree), commits them, and audits the carried-forward canvas range `3d364e1768..ed1c324883` against the continuation plan's Task 2 requirements, reproducing the canvas mutant in a scratch clone.

**Tech Stack:** Java 8 source/bytecode, Gradle, Knopflerfish OSGi, JUnit 4, AssertJ, GraphStream gs-core 1.3, AWT.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries` on branch `graph-workspace-group-only-boundaries`, currently at commit `ed1c324883` with exactly three unstaged test files (`GraphWorkspaceModelAcceptanceShould.java`, `GraphWorkspaceCommandAcceptanceShould.java`, `GraphWorkspacePerformanceDiagnostic.java`). `main` is at `6d4dd3c204`.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation; use `gradle`, never Maven or the Gradle wrapper.
- Commit subjects start `2026-08-27-graph-workspace-group-only-boundaries:` with an imperative subject.
- The requirement source for the carried-forward canvas range is the continuation plan `docs/superpowers/plans/2026-08-27-graph-workspace-group-only-boundaries-continuation.md` Task 2 section (read-only; digest `f5bf87bd8081e3eed307a33d3304ee09ea775f89ad784035324925592e932175`). The two earlier run roots (`.superpowers/sdd/2026-08-27-graph-workspace-group-only-boundaries` and `-continuation`) are preserved byte-for-byte; **their reports and transcripts are NOT admissible as evidence**. All verification must be reproduced independently.
- The group-only rule, retained-dormant policy, pin override, and preserved layout invariants carry over from the original design (`docs/superpowers/specs/2026-08-27-graph-workspace-group-only-boundaries-design.md`).
- TDD: this task's only code is the staging of three already-prepared test files; the audit verifies the carried-forward canvas commit's red/green and mutant in a scratch clone.
- Staging rule: before `git add`, assert the index is empty; stage only the three Files-listed paths; run `git diff --cached --check`; staged paths must equal the Files list exactly. If any OTHER file must change, stop, do not stage it, and report it to the controller.
- Full module suite must be `BUILD SUCCESSFUL` before the task's commit.

## Task 1: Complete the canvas test scope and audit the carried-forward canvas range

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`

No other source or test files change in this task. The audit portions produce only the report at `task-1-implementer-report.md`.

**Interfaces:**

- Consumes (read-only): the continuation plan `docs/superpowers/plans/2026-08-27-graph-workspace-group-only-boundaries-continuation.md` Task 2 section (the canvas requirement source), the commit range `3d364e1768f402022b0c150302f911e1a0fc20cf..ed1c324883` (the canvas commit), and the module test suites.
- Produces: commit `2026-08-27-graph-workspace-group-only-boundaries: Complete group boundary canvas test scope` staging exactly the three Files paths, plus an independent review-style verdict for the carried-forward canvas commit: SPEC PASS/FAIL against every continuation-plan Task 2 requirement, QUALITY APPROVED/REJECTED, findings ledger with severities, and measured probe values.

- [ ] **Step 1: Inspect the three prepared test files**

Run `git diff` on the three unstaged files. Verify each change is a group-only-model update and nothing else: acceptance scenarios mark the nodes that must appear in the graph as group nodes; the performance diagnostic asserts boundary counts for its generated maps (the adapter still walks the full map; the projection emits enclosures only). If any change touches production code or unrelated behavior, or any hunk looks incorrect (e.g., weakened assertions), record it as a finding; do not stage until Step 4. Also verify the three files' changes are mutually consistent with the committed group-only projection (`ProjectionEngine` at `ed1c324883`).

- [ ] **Step 2: Verify the carried-forward canvas range**

Run:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries
git log --oneline 3d364e1768f402022b0c150302f911e1a0fc20cf..ed1c324883
git diff --stat 3d364e1768f402022b0c150302f911e1a0fc20cf ed1c324883
git show --check ed1c324883
```

Expected: exactly one commit (`ed1c324883`, subject `2026-08-27-graph-workspace-group-only-boundaries: Render group boundaries from labels`) touching exactly the 19 files of the continuation plan's Task 2 Files list (9 production: `GraphGeometryEngine`, `HullGeometry`, `HullIntersection`, `GraphPainter`, `GraphHitIndex`, `AccessibleGraphCanvas`, `GraphSearchModel`, `LayoutSettleLoop`, `LayoutWorker`; 10 test files); `git show --check` clean. Any deviation is a load-bearing finding.

- [ ] **Step 3: Verify the canvas requirements against the code**

Read the continuation plan's Task 2 section and the current production files. Verify each requirement with exact values; record pass/fail per requirement:

1. `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)`; cache key unchanged; `computeHull` threads `metrics`.
2. Empty enclosures (no direct nodes, no child hulls) get an octagon from the measured largest label + `BOUNDARY_PADDING` (8.0), centered on the anchor; `BOUNDARY_PADDING` equals `GraphStreamLayoutEngine.BoundarySizes.BOUNDARY_PADDING` (cross-package invariant noted in a comment). Note: the implementation must match the test window (`measuredWidth + 2*BOUNDARY_PADDING ± 1`), not the plan sketch's clearance line, if they differ.
3. `GraphPainter`: no node circles, node labels, or node highlights; non-root hulls painted coral `#DF625D` (fill and stroke), root frames keep `theme.hullFill/hullStroke`; `paintPins` uses the hull lookup; fill/stroke ordering keeps the correct colors.
4. `GraphHitIndex`: hull-only endpoint exposure — the node loop removed except any node geometry the class still needs for edge hit-testing (verify the observational concern: node centers retained solely for edge hit tests because extreme-span edge fixtures cannot use hull endpoints; confirm by reading the edge hit-test path).
5. `GraphSearchModel`: enclosure labels only.
6. `AccessibleGraphCanvas`: enclosure endpoints only.
7. `HullIntersection.siblingOverlap(first, second)`: strict interior intersection; containment and touching are not overlap.
8. `LayoutSettleLoop` and `LayoutWorker` pass metrics into `computeHulls(...)`.

- [ ] **Step 4: Stage and commit the three prepared test files**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries
test -z "$(git diff --cached --name-only)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-group-only-boundaries: Complete group boundary canvas test scope"
```

Expected staged names: exactly the three Files paths, nothing else.

- [ ] **Step 5: Run the full module suite at the completed head**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, 0 failures; record the exact test/error/skip counts from the JUnit XMLs (the 2 skipped tests must be the pre-existing `WorkspaceUriResolverShould` Windows assumptions). If a headless display is required, use `xvfb-run` as the previous runs did.

- [ ] **Step 6: Reproduce red/green and the canvas mutant in a scratch clone**

Create a disposable scratch clone and verify, WITHOUT touching the worktree:

```bash
cd /tmp && rm -rf gw-continuation2-probe
git clone -q --no-checkout --no-local /data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries gw-continuation2-probe
cd gw-continuation2-probe && git checkout -q <completed-head-sha>
```

Then, with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`:

1. Green at the completed head: `gradle :freeplane_plugin_graph:test --tests '*HullGeometryShould' --tests '*HullIntersectionShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull` passes.
2. Canvas mutant: in the clone, mutate `GraphGeometryEngine.computeHull`'s empty branch back to the anchor-point-only supports (drop the label sizing); confirm `emptyEnclosuresSizeTheirOctagonFromTheLabel` fails; restore byte-exact and verify by SHA-256; rerun green.
3. Red phase at the canvas base: check out `3d364e1768f402022b0c150302f911e1a0fc20cf` in the clone and run the same three suites; record which of the new regressions fail and the measured values.
4. Verify `HullIntersection.siblingOverlap` containment/touching behavior with the two-octagon fixture at the completed head (containment false, intersection true).

Record every measured value and SHA-256 in the report. Delete the scratch clone afterwards.

- [ ] **Step 7: Write the verdict**

Write the audit verdict to `task-1-implementer-report.md`: SPEC PASS/FAIL (per requirement from Step 3, with evidence), QUALITY APPROVED/REJECTED, and a findings ledger (id, severity, loadBearing, location, evidence, impact, correction). Assess the canvas implementer's two concerns independently from the code and measurements (out-of-list test files; `GraphHitIndex` node centers for edge hit-testing) — they must be re-derived, not quoted. If the verdict is SPEC FAIL or QUALITY REJECTED with load-bearing findings, still commit nothing further; report the findings and wait for the controller.

- [ ] **Step 8: Confirm the final state**

Run `git status --short` and `git log --oneline -2` in the worktree. Expected: clean worktree at the scope-completion commit on top of `ed1c324883`.

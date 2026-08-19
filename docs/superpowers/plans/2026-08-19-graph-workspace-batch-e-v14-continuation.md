# Graph Workspace Batch E V14 Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Freshly certify the committed endpoint-visibility remediation and complete the remaining accessibility hierarchy and stale-arrow behavior fixes after V13's terminal procedural blocker.

**Architecture:** V14 audits the exact endpoint-visibility source range `302ad25d130b11f04f8b8a5223bbebe06f81f0f2..ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c` while allowing the recovery branch's clean documentation-only descendants above `ec8ed4dd6e`. Projection visibility remains pure and geometry-independent; canvas surfaces add current geometry checks. Accessibility resolves the live Swing hierarchy and keyboard handling falls through to ordinary panning for stale selections.

**Tech Stack:** Java 8 source compatibility, Zulu 21.0.8 runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT accessibility, immutable Graph Workspace projection and geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create, switch, or remove another Git worktree.
- Preserve V13's terminal run root, every earlier Batch E run root, `.codegraph/`, ignored artifacts, and unrelated user state byte-for-byte; never reset, rewrite, clean, checkout, stash, revert, or discard them.
- V14 starts from the clean documentation descendant `a6f928802a13900bd94c75b6f93d1ce3bff3a71c`. The immutable Task 1 source audit range is `302ad25d130b11f04f8b8a5223bbebe06f81f0f2..ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c`; commits after `ec8ed4dd6e` are permitted only when their changed paths are documentation or controller evidence, and the source projection must remain unchanged until Task 2.
- V11 findings F-1 through F-4 may be carried only where current source and fresh tests confirm them; F-5, F-6, and F-7 require fresh source inspection, tests, and review. V13's `BLOCKED` report and all stopped reviewer artifacts are not approval evidence.
- Suppressed, missing, or geometry-less endpoints are never painted, hit, traversed, or exposed through accessibility. Valid finite `LayoutPoint` coordinates, including values near `Double.MAX_VALUE`, must not be rejected or clamped.
- Preserve selected valid unmodified-arrow traversal, no-selection and stale-selection arrow panning, Shift acceleration, Tab and Shift+Tab cycling, Enter validation, Escape preview ordering, immutable ownership boundaries, and the existing nine concrete public `GraphIntent` nested types.
- Projection code remains independent of Swing and `GraphGeometry`; canvas code does not access `MapModel`, `NodeModel`, transformers, files, map mutation, or GraphStream.
- Every Gradle command uses bounded temporary logs; inspect concise tails or JUnit XML counts and remove disposable logs and probes afterward.
- Every child receives a renderer-produced full role envelope through a persisted byte-stable pointer prompt. Compare the completed transcript's first user message byte-for-byte with the stored rendered prompt before admitting its report; a mismatch or missing required report is terminal for this run.
- Before every source commit require an empty index, `git diff --check`, an exact staged allowlist, and a subject beginning `2026-08-10-graph-workspace:`. Do not stage plans, specs, run roots, or unrelated files in task commits.
- The final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, reconciles V11 findings F-1 through F-7, runs `gradle :freeplane_plugin_graph:test -PTestLoggingFull`, and requires a clean worktree and index.

## Task 1: Audit And Freshly Certify Committed Endpoint Visibility

**Implementer tier:** Capable

**Files:**

- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibility.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProminenceCalculator.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`

**Interfaces:**

- Consumes: immutable source range `302ad25d130b11f04f8b8a5223bbebe06f81f0f2..ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c`, the current clean documentation-only descendant, V13's report as an untrusted claim, the V11 F-5 report as historical evidence, `ProjectedEndpointVisibility.visibleEndpoints(...)`, `ProminenceCalculator`, `GraphPainter`, `GraphHitIndex`, `GraphTraversalOrder`, `CanvasState`, and current `GraphGeometry` contracts.
- Produces: a read-only audit report proving or disproving that projection visibility excludes suppressed enclosure endpoints, prominence ignores absent targets, painting/hit testing/traversal require visible current finite geometry, and exact finite-coordinate arithmetic remains unchanged.
- Preserves: all nine audited source/test paths, `GraphProjection.projected(...)` signatures, `NodeProminence` scaling, edge-hit comparisons and tie-breaking, non-suppressed node/hull behavior, and the current committed source. This task must not modify source files, the index, or `HEAD`; a concrete defect is reported for review and may be handled only through the normal fix loop.

### Step 1: Establish the exact source audit baseline without requiring an exact working-tree HEAD

- [ ] Confirm branch `2026-08-10-graph-workspace-batch-e-recovery`, clean status, empty index, and `git diff --check`. Record the observed current `HEAD` and prove it is a descendant of `ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c` whose post-`ec8ed4dd6e` changed paths are documentation or controller artifacts only. Do not reset the checkout to the historical source tip.
- [ ] Confirm `git diff --name-only 302ad25d130b11f04f8b8a5223bbebe06f81f0f2..ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c` is exactly the nine files listed above. Treat that immutable range, rather than the current documentation descendant's exact `HEAD`, as the source under audit. Do not cite V13's stopped implementer or reviewer as approval.
- [ ] Read the complete current implementations and tests directly. Verify the shared set is projection-only and unmodifiable, includes projected nodes and non-`SUPPRESSED` enclosure endpoints in iteration order, and rejects null lists and entries. Verify prominence filters target membership before node/hull deduplication.
- [ ] Verify `GraphPainter` skips edges before attachment when either endpoint is ineligible and does not use layout-only node positions as a fallback. Verify `GraphHitIndex` and `GraphTraversalOrder` apply the same visibility set plus current geometry and preserve the exact finite arithmetic.

### Step 2: Run fresh falsifiable visibility and regression checks

- [ ] Run the focused projection command under Zulu 21 with output redirected to a temporary log, then inspect XML counts for `ProjectedEndpointVisibilityShould` and `ProminenceCalculatorShould`:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.projection.ProjectedEndpointVisibilityShould' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' -PTestLoggingFull
```

- [ ] Run the focused canvas command and inspect XML counts for `GraphCanvasPaintShould` and `GraphInteractionControllerShould`:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' -PTestLoggingFull
```

- [ ] Run the broad projection and canvas `*Should` command and inspect every matching XML suite. Confirm the suppressed-edge image comparison, suppressed-edge hit absence, geometry-less node-edge absence, visible enclosure control edge, finite-coordinate hit tests, and traversal tests are all falsifiable and pass. Delete temporary logs and probes after inspection.

### Step 3: Report the audit without source mutation

- [ ] Recheck `git status --porcelain=v1 --untracked-files=all`, empty index, `git diff --check`, and the observed `git rev-parse HEAD` after tests. If any source, index, or history changed, report `BLOCKED` with the exact discrepancy rather than repairing it.
- [ ] Write exactly one implementer report with `STATUS: DONE` only when the audit and fresh commands pass. State that no source files were changed and no source commit was created, list exact test counts and the nine-path immutable range, identify the documentation-only descendant observed at audit time, and identify any concrete concern without treating V13 artifacts as evidence. Return `DONE` only after the report exists.

## Task 2: Repair Accessibility Hierarchy And Stale Arrow Panning

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-end`

**Interfaces:**

- Consumes: the admitted Task 1 audit report, the committed visibility source range, `ProjectedEndpointVisibility.visibleEndpoints(List<ProjectedNode>, List<ProjectedEnclosure>)`, `GraphTraversalOrder.tabOrder(CanvasState)`, `GraphCanvas.getParent()`, `AccessibleContext` parent/child APIs, and the existing controller methods `setSelectionVisual(...)`, `selectEndpoint(...)`, and `panForArrow(...)`.
- Produces: dynamic `AccessibleGraphCanvas.getAccessibleParent()` and `getAccessibleIndexInParent()` resolution; virtual endpoint availability that requires current projection visibility and current geometry; and arrow handling that treats a stale paint selection as no selection and executes normal panning without emitting traversal or open intents.
- Preserves: virtual endpoint parent/index behavior, current safe accessibility text/actions and viewport bounds, valid selected-arrow traversal, no-selection and Shift pan behavior, Tab and Shift+Tab cycling, Enter validation, Escape preview ordering, and no new Swing child components or `GraphIntent` types.

### Step 1: Establish the clean Task 2 baseline on the documentation descendant

- [ ] Confirm Task 1's audit report was admitted by review, the index is empty, status is clean, and the current `HEAD` is the same clean documentation-descendant lineage used by the audit. Do not require it to equal `ec8ed4dd6e`; verify the immutable visibility source range remains unchanged and only the three Task 2 paths may be modified.
- [ ] Read the current accessibility and controller implementations and independently confirm V11 F-6's hard-coded root index/parent behavior and F-7's consumed stale-arrow branch. Preserve the confirmed F-1 through F-5 behavior and all source paths outside this task's allowlist.

### Step 2: Write root and stale-selection regressions first and verify them fail

- [ ] Add a container-backed test to `AccessibleGraphCanvasShould`: create a `JPanel`, add a `JButton` followed by a `GraphCanvas`, obtain the canvas root context, assert its accessible parent is the panel, enumerate the panel's accessible children to compute the canvas index, and assert the root index equals that index and is not `-1`. Also assert an unattached canvas returns `null` and `-1`.
- [ ] Add stale-arrow coverage for three cases using the existing fixture and installed controller: select an endpoint, replace the canvas state with `emptyState()` for a removed endpoint; select the suppressed endpoint with the original state current; and select the geometry-less endpoint with the original state current. Dispatch an unmodified Right key for each case, assert the viewport center changes through the normal pan path, assert the listener emits no `ChangeSelection` or `OpenSourceNode`, and assert the local paint selection is cleared. Keep the existing valid selected-arrow traversal test as the control.
- [ ] Add an accessibility availability assertion that a virtual child for a suppressed or geometry-less endpoint is absent or unavailable even when the old endpoint object is retained, while current visible endpoints retain safe names, descriptions, bounds, and actions.
- [ ] Run only `AccessibleGraphCanvasShould` under Zulu 21 with a bounded temporary log and confirm the new parent/index and stale-arrow assertions fail for their intended pre-fix behavior. Do not weaken an assertion to make the red phase pass.

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

### Step 3: Implement live hierarchy resolution and stale-arrow fallback

- [ ] Override `AccessibleGraphCanvas.getAccessibleParent()` to inspect `canvas.getParent()` on every call and return that object only when it implements `Accessible`; return `null` for an unattached canvas or a non-accessible parent. Do not cache the result.
- [ ] Override `getAccessibleIndexInParent()` to resolve the live accessible parent, obtain its accessible context, enumerate `getAccessibleChildrenCount()`, and return the index whose child is the canvas or whose child accessible context is this root context. Return `-1` when there is no parent, no context, or no matching child. Do not disturb virtual endpoint children, whose parent remains the canvas.
- [ ] In each virtual endpoint's current-state resolver, require membership in `ProjectedEndpointVisibility.visibleEndpoints(...)` before returning node or enclosure information, then retain the existing current `NodeGeometry`/`HullGeometry` checks and safe-text rules. A missing or suppressed endpoint must remain unavailable for names, descriptions, bounds, actions, and accessibility state.
- [ ] In `GraphInteractionController.handleKeyPressed(...)`, preserve the unconditional Shift-arrow accelerated pan path. For an unmodified arrow with a paint selection, compute the current traversal order first. If the selection is in that order and `nearest(...)` returns a candidate, retain the existing selection intent and consume the key. If the selection is absent from the current order or the canvas state is null, clear only the stale visual selection and fall through to the existing normal pan calculation; do not emit a traversal or open intent. A valid selection with no directional candidate retains its existing consumed behavior.

### Step 4: Run focused, canvas, and complete plugin verification

- [ ] Rerun `AccessibleGraphCanvasShould` and inspect XML counts for all parent/index, virtual availability, stale-arrow, valid traversal, Tab, Enter, Escape, and pan cases.
- [ ] Run the complete canvas `*Should` set under Zulu 21 and inspect all XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

- [ ] Run the complete graph-plugin suite under Zulu 21 and inspect the concise tail and aggregate XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

- [ ] Remove temporary logs and disposable probes. Before committing, run `git diff --check`, inspect the complete three-file diff, and verify no other source or test path changed.

### Step 5: Commit the accessibility and keyboard deliverable

- [ ] Require an empty index, stage exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
```

- [ ] Compare `git diff --cached --name-only` byte-for-byte with the three-path allowlist, run `git diff --cached --check`, and commit exactly:

```bash
git commit -m "2026-08-10-graph-workspace: Repair graph accessibility fallback"
```

- [ ] Write exactly one implementer report with `STATUS: DONE`, the full commit SHA, exact focused/canvas/full-suite counts, staged-path evidence, and any concrete concern. Return `DONE` only after both commit and report exist.

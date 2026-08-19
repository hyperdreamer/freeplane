# Graph Workspace Batch E V12 Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Repair the V11 final-review findings so suppressed endpoints, accessibility hierarchy, and keyboard fallback remain consistent across the Graph Workspace.

**Architecture:** A pure projection-level visible-endpoint set defines which projected nodes and non-suppressed enclosure endpoints are eligible. Projection prominence consumes that set; canvas painting, hit testing, traversal, and accessibility consume the same set and additionally require current geometry. The accessibility root dynamically resolves its Swing parent, and keyboard traversal falls through to ordinary panning when the paint selection is absent from the current traversal order.

**Tech Stack:** Java 8 source target, Zulu 21.0.8 runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT accessibility, immutable Graph Workspace projection and geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create another Git worktree.
- Preserve V11 terminal state and all earlier Batch E run roots byte-for-byte; never reset, rewrite, clean, checkout, stash, revert, or discard existing commits, ignored artifacts, `.codegraph/`, or unrelated user state.
- V12 starts from committed `HEAD` `fbdab24a6e23b6ba0ee29a30fc2f663743021245`; V11 findings F-1 through F-4 are carried as fixed evidence and F-5 through F-7 must be cleared with fresh tests and review.
- Keep source changes inside each task's explicit allowlist. Do not modify `freeplane_api`, add dependencies, change resources/translations, change persistence/XML codecs, access `MapModel`/`NodeModel` from canvas code, or add GraphIntent nested types.
- Projection code remains independent of Swing and `GraphGeometry`; prominence is calculated when `GraphProjection` is created, before layout geometry exists.
- Suppressed or geometry-less endpoints are never painted, hit, traversed, or exposed through accessibility. Do not reject, clamp, or otherwise alter valid finite `LayoutPoint` coordinates.
- Preserve existing Graph Workspace behavior: selected valid unmodified arrows traverse, no-selection and stale-selection arrows pan, Shift arrows accelerate pan, Tab and Shift+Tab cycle visible endpoints, Enter opens current selection, and Escape cancels preview before clearing selection.
- Preserve the existing exact finite-coordinate `GraphHitIndex` arithmetic and the nine concrete public nested `GraphIntent` types.
- Every Gradle command uses temporary bounded logs; inspect concise tails or JUnit XML results and remove disposable logs and probes afterward.
- Before each source commit require an empty index, `git diff --check`, an exact staged allowlist, and a subject beginning `2026-08-10-graph-workspace:`. Do not stage plan, design, run-root, or unrelated files in task commits.
- Every child receives a renderer-produced envelope through a persisted pointer prompt, with exact first-message byte verification before its report is admitted. A prompt mismatch is terminal and must not be reissued.

## Task 1: Unify Suppressed And Geometry-Backed Endpoint Visibility

**Implementer tier:** Capable

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibility.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProminenceCalculator.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`

**Interfaces:**

- Consumes: `ProjectedNode`, `ProjectedEnclosure`, `ProjectedEndpointKey`, `BoundaryTier`, `CanvasState`, `GraphGeometry`, and the existing `ProminenceCalculator`, `GraphPainter`, `GraphHitIndex`, and `GraphTraversalOrder` contracts.
- Produces: public `ProjectedEndpointVisibility.visibleEndpoints(List<ProjectedNode>, List<ProjectedEnclosure>)`, returning an unmodifiable deterministic `Set<ProjectedEndpointKey>` containing every projected node endpoint and every endpoint key belonging to a non-`SUPPRESSED` projected enclosure; the set must reject null inputs and preserve projected-node/enclosure iteration order.
- Produces: prominence counts that ignore suppressed enclosure targets and targets absent from the projected visible-endpoint set; canvas edges that render and hit-test only when both endpoints are projection-visible and have current finite geometry; traversal positions that use the same projection-visible set plus existing geometry checks.
- Preserves: `GraphProjection.projected(...)` signatures, `NodeProminence` scaling, exact edge-hit comparisons, existing node and non-suppressed enclosure painting, and current finite-coordinate behavior.

### Step 1: Establish the clean V12 baseline and inspect the cross-surface contract

- [ ] Confirm the current branch is `2026-08-10-graph-workspace-batch-e-recovery`, `HEAD` is `fbdab24a6e23b6ba0ee29a30fc2f663743021245`, the index is empty, and the only source changes absent are the task paths listed above. Record `git status --porcelain=v1 --untracked-files=all`, `git diff --check`, and the relevant V11 report without modifying terminal run artifacts.
- [ ] Read `GraphProjection`, `ProminenceCalculator`, `GraphPainter`, `GraphHitIndex`, `GraphTraversalOrder`, `GraphGeometry`, and the existing projection/canvas tests. Confirm projection is constructed before geometry and that the current painter can resolve a suppressed hull endpoint while the hit index and traversal omit it.

### Step 2: Write the projection visibility regression first and verify it fails

- [ ] Create `ProjectedEndpointVisibilityShould` with a fixture containing one projected node, one `SUBTLE` enclosure endpoint, and one `SUPPRESSED` enclosure endpoint. Assert `visibleEndpoints(...)` contains the node and subtle endpoint in source iteration order, excludes the suppressed endpoint, returns an unmodifiable set, and rejects null lists.
- [ ] Run only the new test under Zulu 21:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.projection.ProjectedEndpointVisibilityShould' -PTestLoggingFull
```

- [ ] Confirm the test fails because `ProjectedEndpointVisibility` does not yet exist, not because of a fixture or compilation error unrelated to the requested behavior.

### Step 3: Implement the minimal shared projection rule and make prominence consume it

- [ ] Implement `ProjectedEndpointVisibility` as a final utility with no Swing or geometry imports. Build a `LinkedHashSet<ProjectedEndpointKey>` by adding each node endpoint and each endpoint of every enclosure whose `boundaryTier()` is not `SUPPRESSED`; return `Collections.unmodifiableSet(...)`. Require both lists and every entry to be non-null through the existing project style.
- [ ] Update `ProminenceCalculator.calculate(...)` to build the shared visible set, index hulls only for non-suppressed enclosure endpoints, and register a target only when the target is in that set. Preserve source-node handling, deduplication by projected node/hull, and the existing returned key order.
- [ ] Add a `ProminenceCalculatorShould` regression with a projected node source, a single suppressed enclosure target, and a directed edge to that target. Assert the source's `visibleOutgoingTargets()` is zero. Keep the existing subtle/collapsed-hull tests unchanged and green.
- [ ] Run the focused projection tests and confirm the new shared-rule and suppressed-prominence tests pass:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.projection.ProjectedEndpointVisibilityShould' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' -PTestLoggingFull
```

### Step 4: Apply the shared rule and current-geometry requirement to canvas surfaces

- [ ] Update `GraphPainter.paintEdges(...)` to obtain the shared visible-endpoint set and skip an edge before attachment calculation unless both endpoints are present. Change node edge-anchor resolution so a missing current `NodeGeometry` does not fall back to `LayoutPositions.nodes()`; retain hull geometry plus its existing layout anchor preference for enclosure endpoints. Keep all attachment and finite-coordinate guards intact.
- [ ] Update `GraphHitIndex.from(...)` to use the shared visible set for node and enclosure entries and for edge construction. Continue requiring current node/hull geometry and finite attachment endpoints; do not alter `ExactValue`, `DistanceValue`, tolerance validation, or tie-breaking.
- [ ] Update `GraphTraversalOrder.positions(...)` to use the shared visible set and current node/hull geometry, retaining center/label-anchor positions and exact squared-distance ordering.
- [ ] Add `GraphCanvasPaintShould` coverage that constructs a directed edge from an existing visible node to the existing suppressed hull endpoint. Paint the state with that edge and a copy with no edge and assert the images are identical; call `GraphHitIndex.from(state).edgeAt(...)` around the would-be segment and assert empty; assert the source prominence count is zero. Add a second assertion that an edge whose node endpoint has layout position but no current `NodeGeometry` is neither painted nor hit-testable.
- [ ] Add a `GraphInteractionControllerShould` regression that builds a visible node-to-suppressed-enclosure edge with current geometry for both endpoints, queries around the would-be segment, and asserts `GraphHitIndex.from(state).edgeAt(...)` is empty while the existing non-suppressed enclosure-edge control remains hittable.

### Step 5: Run the red-green canvas verification and inspect regressions

- [ ] Before any additional production change, run the new paint/hit tests against the implementation and correct only failures caused by the visibility change. Use bounded output:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' -PTestLoggingFull
```

- [ ] Run all projection tests and all canvas `*Should` tests. Inspect the XML result counts and ensure no existing finite-coordinate, label, or traversal behavior regresses:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.projection.*Should' --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

### Step 6: Commit the visibility deliverable and report evidence

- [ ] Run `git diff --check`, verify the index is empty, stage exactly the five production paths and four test paths in this task, and compare `git diff --cached --name-only` byte-for-byte with the Files allowlist.
- [ ] Commit exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibility.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProminenceCalculator.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "2026-08-10-graph-workspace: Unify visible graph endpoints"
```

- [ ] Write the implementer report with `STATUS: DONE`, the full commit SHA, exact focused and broad test counts, the staged-path check, and any concrete concern. Return `DONE` only after the commit and report exist.

## Task 2: Repair Accessibility Root Hierarchy And Stale Arrow Fallback

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-end`

**Interfaces:**

- Consumes: Task 1's `ProjectedEndpointVisibility` behavior, `GraphTraversalOrder.tabOrder(CanvasState)`, `GraphCanvas.getParent()`, `AccessibleContext` parent/child APIs, and the existing `GraphInteractionController` keyboard and pan methods.
- Produces: `AccessibleGraphCanvas.getAccessibleParent()` resolving the actual accessible Swing container or `null`, `getAccessibleIndexInParent()` returning that container's enumerated child index or `-1`, and arrow handling that treats a paint selection absent from the current traversal order as no selection and executes the existing normal pan path.
- Preserves: virtual endpoint parent/index behavior, current accessibility text/actions, valid selected-arrow traversal, Shift pan acceleration, Tab focus handling, Enter validation, Escape preview ordering, and no new Swing child components.

### Step 1: Establish Task 1's committed baseline and inspect the inherited fixes

- [ ] Confirm Task 1 is committed and admitted, `HEAD` is its reported commit, the index is empty, and only the three Task 2 paths are candidates for modification. Read the Task 1 report and current `AccessibleGraphCanvas`/`GraphInteractionController` implementations before editing.
- [ ] Verify F-6's current root methods still return a hard-coded `-1`/null parent and F-7's arrow branch consumes a stale selection after `GraphTraversalOrder.nearest(...)` returns empty. Preserve the already fixed F-1 through F-4 behavior.

### Step 2: Write root-parent and stale-selection regressions first and verify them fail

- [ ] Add a container-backed test to `AccessibleGraphCanvasShould`: create a `JPanel`, add a `JButton` followed by a `GraphCanvas`, obtain the canvas root context, assert its accessible parent is the panel, and compute the expected index by enumerating the panel's accessible children. Assert the root index equals that enumerated index and is not `-1`.
- [ ] Add one stale-selection arrow test covering three current-state situations: a removed endpoint after replacing the canvas state with `emptyState()`, a suppressed endpoint while the original state is current, and a geometry-less endpoint while the original state is current. For each case, set the paint selection, dispatch an unmodified Right key through the installed controller, assert the viewport center changes, assert no traversal/open intent is emitted, and assert the local paint selection is cleared. Keep the existing valid selected-arrow assertion as the control case.
- [ ] Run only `AccessibleGraphCanvasShould` and confirm the new tests fail for the expected missing parent/index and stale-arrow behavior:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

### Step 3: Implement live root hierarchy resolution and stale-arrow fallback

- [ ] Override `AccessibleGraphCanvas.getAccessibleParent()` to inspect `canvas.getParent()` and return it only when the parent implements `Accessible`; return `null` for an unattached canvas or a non-accessible container.
- [ ] Override `getAccessibleIndexInParent()` to resolve the live accessible parent, enumerate `parent.getAccessibleContext().getAccessibleChildrenCount()`, and return the index whose child object is the canvas or whose accessible context is this root context. Return `-1` when no parent or matching child exists. Do not cache either value because Swing hierarchy can change.
- [ ] Update virtual endpoint availability to require membership in `ProjectedEndpointVisibility.visibleEndpoints(...)` before exposing node or enclosure details, while retaining the current geometry checks and safe-text rules.
- [ ] In `GraphInteractionController.handleKeyPressed(...)`, when an unmodified arrow has a paint selection, use the current traversal order to find a candidate. If a candidate exists, retain the current selection intent and consume the event. If the selection is absent from the current order or the state is null, clear only the stale visual selection and fall through to the existing normal pan calculation; do not emit `OpenSourceNode` or a traversal intent for the stale endpoint. Leave Shift arrows on the unconditional accelerated-pan path.

### Step 4: Run focused and full canvas verification

- [ ] Rerun `AccessibleGraphCanvasShould` and confirm the root hierarchy and all three stale-selection cases pass.
- [ ] Run the full canvas test set and inspect exact results:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

- [ ] Run the complete graph-plugin suite before committing:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

### Step 5: Commit the accessibility and keyboard deliverable and report evidence

- [ ] Run `git diff --check`, verify the index is empty, stage exactly the three Task 2 paths, and compare the staged path list against the allowlist.
- [ ] Commit exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
git commit -m "2026-08-10-graph-workspace: Repair graph accessibility fallback"
```

- [ ] Write the implementer report with `STATUS: DONE`, the full commit SHA, exact test counts for the focused, canvas, and complete graph-plugin runs, the staged-path check, and any concrete concern. Return `DONE` only after the commit and report exist.

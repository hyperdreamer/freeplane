# Graph Workspace Group-Only Boundaries Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover the group-only-boundaries work after the Task 2 review dispatch died without a verdict: audit the carried-forward Task 2 commit `3d364e1768` (no source changes), then render group boundaries from labels, and finish with the mandatory final review.

**Architecture:** The blocked run root is preserved byte-for-byte and its reports are not evidence. Git commits are authority: Task 1 (`4fdefc93c7`, reviewed) and Task 2 (`3d364e1768`, unreviewed) are committed in the worktree. The continuation's first task is a no-source-change audit that independently verifies the exact range `4fdefc93c7..3d364e1768` against the original plan's Task 2 requirements, reproducing red/green and all three falsifiability probes in a scratch clone. The second task is the original plan's Task 3 (canvas boundary rendering), unchanged.

**Tech Stack:** Java 8 source/bytecode, Gradle, Knopflerfish OSGi, JUnit 4, AssertJ, GraphStream gs-core 1.3, AWT (EDT-only text metrics).

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries` on branch `graph-workspace-group-only-boundaries`, currently at commit `3d364e1768` (clean). `main` is at `7beb2bf4ce`.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation; use `gradle`, never Maven or the Gradle wrapper.
- Commit subjects start `2026-08-27-graph-workspace-group-only-boundaries:` with an imperative subject.
- The requirement source for the carried-forward range is the original plan `docs/superpowers/plans/2026-08-27-graph-workspace-group-only-boundaries.md` (read-only; digest `c8cea37b8b039f3c4374da6a7920a26c36856d3b973b27e5611deaeff44fcc4d`). The blocked run root `.superpowers/sdd/2026-08-27-graph-workspace-group-only-boundaries` (the run that terminated in `DISPATCH_MISMATCH_BLOCKED`) is preserved byte-for-byte; **its reports and transcripts are NOT admissible as evidence** in this continuation. All verification must be reproduced independently.
- The group-only rule: a node appears in the graph iff it carries the Graph Group marker. Group-marked nodes are projected as enclosures; their non-group descendants are never rendered; group-marked descendants nest. Each map's root stays as a root frame with existing tiering. The map root never chains; a group chains only with a single source child.
- Preserve: SHA-256 identity encoding, deterministic ordering and seeds, particle ordering, pin handling, availability semantics, hidden/summary exclusion, safe labels, boundary tiering, solver quality `0.10`, cross-map aggregate cap `0.005`, the `chooseNodePosition` no-op, and `LayoutCalibration` unchanged.
- **Retained-dormant policy:** `ProjectedNode`, `NodeProminence`, `GraphProjection.nodes()`, `LayoutPositions.nodes()`, and prominence maps stay in the public APIs with empty lists/maps; do not delete them.
- Pins are the one documented override: a pinned boundary keeps its forced position and may collide with siblings.
- TDD: new regressions must fail against the pre-change code before the production change is applied.
- Staging rule per task: before `git add`, assert the index is empty; stage only paths listed in the task's Files block; run `git diff --cached --check`; staged paths must be a subset of the listed Files. If a file OUTSIDE the list must change, stop, do not stage it, and report it to the controller.
- Full module suite must be `BUILD SUCCESSFUL` before each task's commit.

## Task 1: Audit the carried-forward boundary separation range

**Implementer tier:** Capable

**Files:**

- No source or test files change. This task produces only the audit report at `task-1-implementer-report.md`; it must not stage or commit anything.

**Interfaces:**

- Consumes (read-only): the original plan `docs/superpowers/plans/2026-08-27-graph-workspace-group-only-boundaries.md` Task 2 section (the requirement source), the commit range `4fdefc93c7005636de0d66544a9b87d2af610770..3d364e1768f402022b0c150302f911e1a0fc20cf`, and the module test suites.
- Produces: an independent review-style verdict for the carried-forward Task 2 commit: SPEC PASS/FAIL against every original Task 2 requirement, QUALITY APPROVED/REJECTED, a findings ledger with severities, and measured probe values.

- [ ] **Step 1: Verify the range and scope**

Run:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries
git log --oneline 4fdefc93c7005636de0d66544a9b87d2af610770..3d364e1768f402022b0c150302f911e1a0fc20cf
git diff --stat 4fdefc93c7005636de0d66544a9b87d2af610770 3d364e1768f402022b0c150302f911e1a0fc20cf
git show --check 3d364e1768f402022b0c150302f911e1a0fc20cf
git status --short
```

Expected: exactly one commit (`3d364e1768`, subject `2026-08-27-graph-workspace-group-only-boundaries: Separate sibling boundaries by construction`) touching exactly the 7 files `GraphStreamLayoutEngine.java`, `TypedSpringBox.java`, `TypedNodeParticle.java`, `PerceptualIdlePolicy.java`, `BoundarySeparationShould.java`, `PerceptualIdlePolicyShould.java`, `TypedForcesShould.java`; `git show --check` clean; worktree clean. Any deviation is a load-bearing finding.

- [ ] **Step 2: Verify the production code against the original Task 2 requirements**

Read the original plan's Task 2 section and the current `GraphStreamLayoutEngine.java`, `TypedSpringBox.java`, `TypedNodeParticle.java`, `PerceptualIdlePolicy.java`. Verify each requirement with exact values, and record pass/fail per requirement in the report:

1. Anchor-only layout: no node particles, no node rings, no containment links; `frame()` keeps an always-empty nodes map; `LayoutPositions.of` API unchanged.
2. `BoundarySizes` constants `CHAR_WIDTH_UPPER_BOUND = 16.0`, `CHAR_HEIGHT_UPPER_BOUND = 24.0`, `BOUNDARY_PADDING = 8.0`, `SIBLING_GAP = 8.0`, `FRAME_CLEARANCE = 16.0`; leaf sizes from label lengths; frame sizes from `ringRadius + reachOf(children)`; `boundaryRadius = hypot(w,h)/2`.
3. Ring packing: `ringRadius = hypot(maxW + GAP, maxH + GAP) / (2 sin(pi/N))`, N <= 1 -> 0.0; root frames on the top ring around the origin (N <= 1 -> origin); sibling angle `2 pi index / count` from the ordered `directEnclosures()` list; no randomness in seeding; `workspaceBytes` kept for the solver `Random`.
4. Pin binding: `pinsBySource` keyed by `SourceNodeKey.persisted(pin.source())`; `pinFor` matches the anchor's `endpointKeys()`.
5. Boundary repulsion: `BOUNDARY_REPULSION_FACTOR = 0.5`; `addBoundaryRepulsion` iterates `typedParticles`, penetrates at `boundaryRadius_i + boundaryRadius_j + SIBLING_GAP`, pushes along the center line; wired through `disp` in `TypedNodeParticle.repulsionN2` and `repulsionNLogN` (not through the `displacement` parameter, which `attraction`'s `fill(0.0)` wipes); NOT added to `rawBudgetedRepulsion`.
6. Hierarchy springs: rest lengths `GROUP_SPACING = 100.0` / `SUB_GROUP_SPACING = 60.0` by child depth via `hierarchyRestLength`.
7. Idle policy: `SPIKE_CONSECUTIVE = 8`, `SPIKE_RMS = 0.05`, `SPIKE_MAX = 0.10` in `PerceptualIdlePolicy`, with `PerceptualIdlePolicyShould` updated to the new values.

- [ ] **Step 3: Run the full module suite at the carried-forward head**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, 0 failures. Record the exact test/error/skip counts from the JUnit XMLs (including that the 2 skipped tests are the pre-existing `WorkspaceUriResolverShould` Windows assumptions).

- [ ] **Step 4: Reproduce the red/green and the three falsifiability probes in a scratch clone**

Create a disposable scratch clone and verify, WITHOUT touching the worktree:

```bash
cd /tmp && rm -rf gw-continuation-probe
git clone -q --no-checkout --no-local /data/home/guest/Development/freeplane/.worktrees/graph-workspace-group-only-boundaries gw-continuation-probe
cd gw-continuation-probe && git checkout -q 3d364e1768f402022b0c150302f911e1a0fc20cf
```

Then, with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`:

1. Green at head: `gradle :freeplane_plugin_graph:test --tests '*BoundarySeparationShould' --tests '*TypedForcesShould' -PTestLoggingFull` passes (6 + 12 tests).
2. Probe 1 (ring radius): in the clone, change `BoundarySizes.ringRadius` to use the mean child width instead of `Math.max`; confirm `siblingBoundariesSeedWithoutOverlap` (and the wide-sibling cases of `TypedForcesShould`) fail; restore byte-exact and verify by SHA-256.
3. Probe 2 (repulsion): delete the `typedBox.addBoundaryRepulsion(this, displacement)` calls; confirm `settledSiblingBoundariesRemainSeparated` fails; restore byte-exact and verify by SHA-256.
4. Probe 3 (top ring): make `topRingPosition` return `(0.0, 0.0)` for every root; confirm `rootFramesSeparateOnTheTopRing` fails; restore byte-exact and verify by SHA-256.
5. Red phase at the pre-Task-2 base: check out `4fdefc93c7005636de0d66544a9b87d2af610770` in the clone and run `--tests '*BoundarySeparationShould'`; record which of the 6 tests fail and the measured values.

Record every measured value and SHA-256 in the report. Delete the scratch clone afterwards.

- [ ] **Step 5: Write the verdict**

Write the audit verdict to `task-1-implementer-report.md`: SPEC PASS/FAIL (per requirement from Step 2, with evidence), QUALITY APPROVED/REJECTED, and a findings ledger (id, severity, loadBearing, location, evidence, impact, correction). Assess the two implementer-reported concerns independently from the code and measurements (movement bound vs penetration spikes; mixed-width fixture falsifiability) — they must be re-derived, not quoted.

- [ ] **Step 6: Confirm no changes and no commit**

Run `git status --short` in the worktree: expected empty. Do NOT stage or commit anything for this task. If the verdict is SPEC FAIL or QUALITY REJECTED with load-bearing findings, still commit nothing; report the findings and wait for the controller.

## Task 2: Render group boundaries from labels

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/LabelPlacementShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**

- Consumes: `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)`, `GeometryTextMetrics.measure(String, BoundaryTier)`, `AwtGeometryTextMetrics(Font, FontRenderContext)`, `LayoutSettleLoop.defaultMetrics()`, `HullGeometry.of(...)`, `HullIntersection`, `LabelPlacementEngine`, `GraphTheme`, `GraphPaintState`, `CanvasState`, `ProjectedEnclosure.mapRoot()`, and `GraphStreamLayoutEngine.BoundarySizes.BOUNDARY_PADDING` (constant equality).
- Produces: `computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)` where empty enclosures (no direct nodes, no child hulls) get a label-sized octagon centered on the anchor (measured label bounds + `BOUNDARY_PADDING`, octagonalized by the existing 8-normal clip), root frames keep the convex-closure path; the painter draws non-root hulls in coral (`#DF625D`) and stops painting node circles/labels/highlights; the hit index, search model, and accessibility surface expose boundaries only; `HullIntersection` gains a sibling-overlap predicate for tests.

- [ ] **Step 1: Add the failing boundary-rendering regressions**

In `HullGeometryShould.java` (or a new `EmptyHullGeometryShould.java` in the geometry package) add:

```java
    @Test
    public void emptyEnclosuresSizeTheirOctagonFromTheLabel() {
        // Build an enclosure with one label "Wide group label" and no children;
        // compute hulls via GraphGeometryEngine.computeHulls(projection, positions, metrics)
        // with a fixed AwtGeometryTextMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12),
        // new FontRenderContext(null, true, true));
        // Assert: hull.exactPolygon()'s bounding box width is within
        // [measuredWidth + 2*BOUNDARY_PADDING - 1, measuredWidth + 2*BOUNDARY_PADDING + 1]
        // (measure the label with the same metrics), and the polygon is centered on the anchor.
    }
```

In `HullIntersectionShould.java` add:

```java
    @Test
    public void siblingOverlapPredicateRejectsNestedContainment() {
        // Two octagons: one inside the other -> siblingOverlap(outer, inner) is false
        // (containment is not sibling overlap); two intersecting octagons -> true.
    }
```

In `GraphCanvasPaintShould.java` add a test that a projection containing only enclosures (no nodes) paints hull shapes and never paints node circles (assert via the painted image that no node-fill-colored circle exists, or via the existing image-assertion helper of that suite).

- [ ] **Step 2: Run the geometry/canvas tests and confirm the new regressions fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*HullGeometryShould' --tests '*HullIntersectionShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull
```

Expected: the new tests fail (empty hulls are the fixed 16-unit octagons; no sibling predicate exists; node circles are still painted).

- [ ] **Step 3: Apply the boundary rendering**

In `GraphGeometryEngine.java`:

1. Change `computeHulls(GraphProjection, LayoutPositions)` to `computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)` (add the parameter; keep the cache key unchanged).
2. In `computeHull`, replace the `empty` branch so an empty enclosure's supports come from its measured label bounds instead of the anchor point:

```java
            final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
            for (int index = 0; index < 8; index++) {
                final double nx = NORMALS[index][0];
                final double ny = NORMALS[index][1];
                double maxSupport = Double.NEGATIVE_INFINITY;
                if (empty) {
                    final LayoutPoint anchor = positions.anchors().get(hullKey);
                    final Dimension2D label = labelSize(enclosure, metrics);
                    final double halfWidth = label.getWidth() * 0.5 + BOUNDARY_PADDING;
                    final double halfHeight = label.getHeight() * 0.5 + BOUNDARY_PADDING;
                    maxSupport = nx * anchor.x() + ny * anchor.y()
                        + Math.max(Math.abs(nx) * halfWidth, Math.abs(ny) * halfHeight);
                }
                else {
                    // unchanged child/nodes path
                }
                supports[index] = maxSupport + HULL_CLEARANCE;
            }
```

with `private static final double BOUNDARY_PADDING = 8.0;` and:

```java
    private static Dimension2D labelSize(final ProjectedEnclosure enclosure, final GeometryTextMetrics metrics) {
        Dimension2D largest = null;
        for (final SafeNodeLabel label : enclosure.labels()) {
            final Dimension2D measured = metrics.measure(label.displayText(), enclosure.boundaryTier());
            if (largest == null || measured.getWidth() * measured.getHeight() > largest.getWidth()
                    * largest.getHeight()) {
                largest = measured;
            }
        }
        if (largest == null) {
            throw new IllegalArgumentException("Enclosures must carry at least one label");
        }
        return largest;
    }
```

3. Thread `metrics` through `computeHull` (parameter). Keep the empty label anchor at the anchor position (the octagon is centered on the anchor). `BOUNDARY_PADDING` must equal `GraphStreamLayoutEngine.BoundarySizes.BOUNDARY_PADDING`; add a comment noting the cross-package invariant.

In `GraphPainter.java`:

1. Remove `paintNodes(...)` and its call; remove the node label loop in `paintLabels(...)`; remove the node loop in `paintHighlights(...)`; remove `paintPins`' node-geometry skip condition and use the hull lookup instead:

```java
    private static void paintPins(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme, final boolean dimUnrelated,
            final Set<ProjectedEndpointKey> visibleEndpoints) {
        for (final PinProjection pin : state.projection().pins()) {
            if (!pin.active() || !pin.projectedNode().isPresent()) {
                continue;
            }
            final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofNode(pin.projectedNode().get());
            if (!visibleEndpoints.contains(endpoint) || hullOf(state, endpoint) == null) {
                continue;
            }
            // unchanged cross drawing at pin.x()/pin.y()
        }
    }

    private static HullGeometry hullOf(final CanvasState state, final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            return null;
        }
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) {
                return state.geometry().hulls().get(enclosure.hullKey());
            }
        }
        return null;
    }
```

2. Paint non-root boundaries in coral: in `paintHulls`, replace the fill/stroke colors for non-root enclosures:

```java
            if (enclosure.mapRoot()) {
                graphics.setColor(theme.hullFill(enclosure.mapReferenceId(), enclosure.boundaryTier()));
                graphics.setColor(theme.hullStroke(enclosure.mapReferenceId(), enclosure.boundaryTier()));
            }
            else {
                graphics.setColor(GROUP_BOUNDARY_COLOR);
                graphics.setColor(GROUP_BOUNDARY_COLOR);
            }
```

with `private static final Color GROUP_BOUNDARY_COLOR = new Color(0xDF, 0x62, 0x5D);` (fixed coral, matching the map-view marker).

3. Remove now-unused imports (ProjectedNode, NodeGeometry, Ellipse2D where unused).

In `GraphHitIndex.java`: remove the `EndpointEntry.forNode(...)` node entries and the `geometry.nodes()` loop; hull entries only (the class already builds hull entries; the node loop and its imports go).

In `GraphSearchModel.java`: search enclosure labels instead of node labels — the node loop becomes an enclosure loop over `state.projection().enclosures()` using each `endpointKeys()` entry and `labels()` text; keep the `SafeText`/`SearchMatch` shape and the existing API.

In `AccessibleGraphCanvas.java`: the node accessibility branch (lines around 470, reading `projection.prominence()`) is replaced by enclosure endpoints: each visible boundary contributes its label text and hull position; keep the same accessibility API.

In `HullIntersection.java`: add

```java
    public static boolean siblingOverlap(final HullGeometry first, final HullGeometry second) {
        // Returns true iff the two convex hull polygons intersect in more than a
        // shared boundary point (strict interior intersection). Uses the existing
        // polygon intersection logic of this class; containment is NOT overlap.
    }
```

Implement it with the class's existing polygon-separation machinery (edge-normal separation): two convex polygons are disjoint iff a separating axis exists; containment is detected when one polygon lies entirely inside the other (no separating axis but no edge intersection) and returns false.

In `LayoutSettleLoop.java` and `LayoutWorker.java`: pass the metrics into `computeHulls(...)` at the call sites (`geometryEngine.computeHulls(projection, positions, metrics)` — `LayoutSettleLoop` already holds `metrics`; `LayoutWorker` uses the same `defaultMetrics()` as `LayoutSettleLoop` — add the identical private helper or pass the loop's metrics through).

- [ ] **Step 4: Run the geometry/canvas suites and the full module suite**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*geometry*' --tests '*canvas*' --tests '*LayoutSettleLoopShould' --tests '*LayoutWorkerShould' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Rework the affected suites: hull tests that assert the fixed 16-unit empty octagon now assert the label-sized octagon; paint tests that draw node circles now assert boundary-only painting; search/accessibility tests assert enclosure search results; `LabelPlacementShould` keeps its interior placement expectations (labels now fit inside the label-sized octagons). If any file OUTSIDE this task's Files list needs a change, do not stage it; report it to the controller.

- [ ] **Step 5: Falsifiability probe (disposable, no worktree residue)**

Record SHA-256 of `GraphGeometryEngine.java`; mutate the empty branch to keep the old anchor-point supports (drop the label sizing); confirm `emptyEnclosuresSizeTheirOctagonFromTheLabel` fails; restore byte-exact; verify SHA-256; rerun green.

- [ ] **Step 6: Verify scope and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add <every changed path from this task's Files list>
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-group-only-boundaries: Render group boundaries from labels"
```

Expected staged names: a nonempty subset of this task's Files list, nothing else.

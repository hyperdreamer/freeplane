# Graph Workspace Node Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

**Goal:** Enforce the I1 disc-separation invariant in a new layout projection stage that publishes a real residual, replace the paint-time label path with the pinned screen-space placement engine and its degradation ladder, delete the old label engine, and close the specification's acceptance contract.

**Architecture:** Two file-disjoint lanes plus a sequential integration tail. Lane `projection-layout` (Tasks 1-4) adds `layout/NodeSeparationProjection` and `NodeSeparationResult`, threads the residual through `LayoutFrame` and every publication path, aligns relationship/containment rest lengths and seeds with the disc invariant, and pins the three-map correction characterization. Lane `screen-placement` (Tasks 5-7) adds `canvas/LabelPlacementRequest`, `canvas/PlacedLabel`, `canvas/LabelFonts`, `canvas/ScreenLabelPlacement`, `canvas/ScreenLabelPlacementCache` and the `ScreenLabelPlacementShould` fixtures. Lane `integration` (Tasks 8-11) rewires `GraphPainter`/`GraphCanvas`/`GraphTheme`, deletes `LabelPlacementEngine`/`LabelPlacement`/the `GraphGeometry` label surface, moves the worker-side `label` performance stage to a paint-path `placement` stage plus a `separation` stage, and runs the acceptance sweep.

**Tech Stack:** Java 8 language level (class major version 52), Gradle plugin project `freeplane_plugin_graph`, JUnit 4 with AssertJ, AWT `Font`/`FontRenderContext`/`BufferedImage` under `java.awt.headless=true`, GraphStream 1.3 confined to `layout/graphstream`.

**Implementation contract:** `docs/superpowers/specs/2026-09-12-graph-node-separation-spec.md` (1265 lines, revision `a97550744c`), derived from the approved design `docs/superpowers/specs/2026-09-12-graph-node-separation-design.md` (revision 5.2). The committed oracle for every pinned fixture is `docs/superpowers/specs/mockups/2026-09-12-node-separation/FixtureProbe.java` (self-checking, 689 checks, exits non-zero on mismatch). `NodeSeparationMockups.java` is a design-time reference that implements pre-O4 rules and is **not** an oracle.

### Requirement coverage (specification section -> task)

- §0 conventions -> all tasks. §1 constants: C1-C6 -> Task 1; C7 -> Task 10; C8-C14 -> Tasks 5 and 6; C15-C16 -> Task 10; C17 -> Task 7.
- §2.1, §2.2 -> Task 1; §2.3 -> Task 2; §2.4, §2.5, §2.6 -> Task 5; §2.7 -> Tasks 5 and 6; §2.8 -> Task 7; §2.9 -> Task 3; §2.10 -> Task 6.
- §3 contracts: I1 -> Tasks 1, 2; I2 -> Tasks 5, 6, 8; I3 -> Tasks 5, 6, 7; I4 -> Task 6.
- §4.1, §4.2, §4.3 -> Task 2; §4.4, §4.5 -> Tasks 1, 2; §4.6 -> Task 10.
- §5.1, §5.2 -> Task 1; §5.3 -> Task 4; §5.4 -> Task 5; §5.5 -> Task 5; §5.6 -> Task 6; §5.7 -> Task 6; §5.8 -> Task 7; §5.9 -> Tasks 5 and 7; §5.10 -> Tasks 5, 6, 8.
- §6 removal/migration -> Task 1 (public layout types), Task 3 (springs, seeds), Task 8 (painter, canvas, theme), Task 9 (deleted types and test migration), Task 10 (performance, fixture hashes).
- §7 acceptance: 1 -> Tasks 1, 2, 4; 2 -> Task 2; 3 -> Tasks 5, 6, 7, 8; 4 -> Task 10; 5 -> Task 9; 6 -> Task 3; 7 -> Task 10; 8 -> Task 11.
- §8 resolutions: O1, O2, O3 -> Task 1; O4 -> Tasks 5, 6; O5 -> Task 10; O6 -> Task 3; O7, O8, O9 -> Tasks 5, 8; O10 -> Task 7; O11 -> the file:line values in every Files block; O12, O14 -> Task 5; O13 -> Task 10.
- Appendix A fixtures: A.1-A.2 -> Tasks 1, 5; A.3 -> Task 6; A.4 -> Task 4; A.5 -> Task 1; A.6 -> Tasks 5, 6, 7; A.7 -> Task 7.

### Specification gaps this plan resolves deterministically

The specification is under-pinned in seven places. Each resolution below is minimal, is restated in Global Constraints, and must not be widened by a task:

- **G1 — viewport centre.** §2.7 step 1 needs the viewport centre (`centerX`, `centerY`) to map world to screen, but the §2.4 `LabelPlacementRequest.of` parameter list omits it. The plan appends `double centerX, double centerY` after `double zoom` and adds `centerX()`, `centerY()` accessors. Fixtures pass `0.0, 0.0`, matching the world-centred convention of §5 after the documented `(+W/2,+H/2)` translation.
- **G2 — positions identity.** §2.10 keys the cache on `LayoutPositions` identity and §2.7 step 3 ties the label order to `LayoutPositions.nodes()` iteration order, but the §2.4 request carries no positions. The plan appends `LayoutPositions positions` after `geometry` and adds `positions()`.
- **G3 — `PlacedLabel.rung()` and terminal modes for non-ladder labels.** §2.5 does not define `rung()` for enclosure labels or for node `HOVER_ONLY` labels, and §2.8 does not name a `Mode` for the emphatic anchor terminal. Pin: enclosure labels carry `Rung.FULL_NEAR` (`HOVER_ONLY` terminal carries `Rung.HOVER_ONLY`); a node `HOVER_ONLY` label carries `Rung.HOVER_ONLY`; the emphatic anchor terminal carries `Mode.INTERIOR` with `emphaticAtAnchor = true`; the subtle terminal carries `Mode.HOVER_ONLY`.
- **G4 — `HOVER_ONLY` node anchor.** §2.7 step 10 does not pin the anchor of a node `HOVER_ONLY` label. Pin: the `ABOVE` base slot at `SLOT_GAP` with the full font and measured size; the label is never an obstacle and is not painted except when forced.
- **G5 — enclosure rule coordinate space.** §2.8 states the enclosure rule over `HullGeometry` but the label heights are screen pixels. Pin: the enclosure rule runs in screen space on the hull polygon mapped by `request`'s world-to-screen mapping; `HullGeometry` objects are never mutated, and `geometry.hulls()` is bit-identical before and after placement.
- **G6 — placement text.** §2.7 says "full text" without naming the accessor. Pin: the placement text is `ProjectedNode.label().displayText()` or `ProjectedEnclosure.labels().get(i).displayText()`, and `fullTextSlotWasFree` compares that same string.
- **G7 — C7 projection stage name.** C7 requires a recorded baseline for the new projection stage but names no `PerformanceMeasurements.Stage`. Pin: add `SEPARATION("separation")` immediately after `CORRECTION("correction")`, recorded in the direct probe; thresholds stay `-1` (diagnostic-only) until a baseline is recorded, exactly as C7/C15 require.

## Global Constraints

- Java 8 language level (class major version 52); do not use APIs newer than Java 8. Build and run with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`.
- Use the repository `gradle` binary (never `gradlew`, never Maven) from the repository root; add `-PTestLoggingFull` for verbose failures. The plugin module is `:freeplane_plugin_graph`; all tests run with `java.awt.headless=true`.
- Pinned constants, copied verbatim: `NodeSeparationProjection.MIN_GAP = 6.0` (C1); `MAX_PASSES = 64` (C2); `RELAXATION = 0.5` displacement per movable node and full penetration when the partner is pinned (C3); `SPATIAL_CELL = 34.0`, 3x3 cell query, ascending `j` (C4); node radius `8.0 * prominence.scale()`, default scale `1.0`, `NODE_RADIUS = 8.0`, `NodeProminence.MAX_SCALE = 1.75` (C5); residual counts unordered pairs with strict `hypot < r_i + r_j + MIN_GAP`, pinned-pinned pairs included, anchors excluded (C6); `SLOT_GAP = 6.0` (C8); `DISPLACED_OFFSET = 30.0` (C9); slot width caps vertical `200.0`, horizontal `130.0`, diagonal `150.0` (C10); ladder and slot order of §2.7 step 5 (C11); leader for every slot except `ABOVE`/`BELOW` (C12); measurement `font.getStringBounds(text, new FontRenderContext(null, true, true))` on the base theme font at base size (C13); no ink-containment assertion, per-side overhang measured at 0.2151-0.7578 px (C14); `ARC_GAP = 1.0`, `EXTERNAL_GAP = 4.0`, `SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8` (C17); line heights `h(12)=16.344114`, `h(9)=12.258085`, `h(10)=13.619987`, `h(15 bold)=20.430143` px.
- Pinned fixture values that must appear literally in tests: red-phase `(-5,0)`/`(29,0)` at distance `34.0` in 2 passes; sandwich `m=(18,0)`, 64 passes, residual 1; pinned-pinned `m=(42,0)`, 2 passes, residual 1; solvable `(-1,0)`/`(21,0)`, 2 passes, residual 0; coincident `(-11,0)`/`(11,0)`, 3 passes; three-map correction `(0,0),(50,0),(-50,0)` with overlap 10; §5.4/§5.5/§5.6/§5.8/§5.9 anchors, sizes, histograms, leader/support maxima and ink measurements as tabulated in each task below.
- Production files allowed to change, and no others: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java` (new), `.../layout/NodeSeparationResult.java` (new), `.../layout/LayoutFrame.java`, `.../layout/LayoutWorker.java`, `.../control/LayoutSettleLoop.java`, `.../control/GraphUpdateCoordinator.java`, `.../layout/graphstream/GraphStreamLayoutEngine.java`, `.../layout/graphstream/TypedSpringBox.java`, `.../canvas/LabelPlacementRequest.java` (new), `.../canvas/PlacedLabel.java` (new), `.../canvas/LabelFonts.java` (new), `.../canvas/ScreenLabelPlacement.java` (new), `.../canvas/ScreenLabelPlacementCache.java` (new), `.../canvas/GraphPainter.java`, `.../canvas/GraphCanvas.java`, `.../canvas/GraphTheme.java`, `.../geometry/GraphGeometry.java`; `.../geometry/LabelPlacementEngine.java` and `.../geometry/LabelPlacement.java` are deleted.
- Test files allowed to change, and no others: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java` (new), `.../canvas/ScreenLabelPlacementShould.java` (new), `.../layout/GraphStreamBoundaryShould.java`, `.../layout/LayoutWorkerShould.java`, `.../layout/MapTierCorrectionShould.java`, `.../layout/TypedForcesShould.java`, `.../layout/ReferenceRepulsionFixture.java`, `.../layout/BoundarySeparationShould.java` (only if the spring change moves it), `.../projection/GroupOnlyProjectionShould.java`, `.../projection/ProjectionDeterminismShould.java`, `.../projection/StructuralProjectionShould.java`, `.../control/LayoutSettleLoopShould.java`, `.../control/GraphUpdateCoordinatorShould.java`, `.../control/GraphWorkspaceCommandAcceptanceShould.java`, `.../canvas/AccessibleGraphCanvasShould.java`, `.../canvas/GraphCanvasPaintShould.java`, `.../canvas/GraphInteractionControllerShould.java`, `.../canvas/GraphSearchModelShould.java`, `.../command/ContributorDeletionPlanShould.java`, `.../window/GraphWorkspaceWindowModelShould.java`, `.../window/WorkspaceDialogsShould.java`, `.../integration/GraphWorkspaceModelAcceptanceShould.java`, `.../smoke/GraphWorkspaceUiEvidence.java`, `.../performance/PerformanceMeasurements.java`, `.../performance/PerformanceTripwiresShould.java`, `.../performance/GraphWorkspacePerformanceDiagnostic.java`; `.../geometry/LabelPlacementShould.java` is deleted.
- Documentation and build artifacts that must stay byte-identical: `docs/superpowers/specs/2026-09-12-graph-node-separation-spec.md`, `docs/superpowers/specs/2026-09-12-graph-node-separation-design.md`, every file under `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (including `FixtureProbe.java` and `NodeSeparationMockups.java`), every file under `docs/superpowers/specs/images/`, `freeplane_plugin_graph/build.gradle`, every `Resources_*.properties`, and every other plan under `docs/superpowers/plans/`. `gradle :freeplane_plugin_graph:graphUiEvidence` is not run by this plan.
- No persisted-format change: no workspace XML, pin, position, viewport or fixture writer changes. Fixture bytes are produced by the existing `GeneratedWorkspace.writeFixtures` inside `PerformanceTripwiresShould`; do not add or run a fixture-regeneration task, and do not hand-edit any `.fpg` file.
- G1-G7 resolutions: `LabelPlacementRequest` carries `LayoutPositions positions` (G2) and `double centerX, double centerY` (G1) in addition to the §2.4 list, in the order `(projection, geometry, positions, zoom, centerX, centerY, placementArea, forced, renderingLevel)`; `PlacedLabel` carries a package-private slot token used only for retention; enclosure labels carry `Rung.FULL_NEAR`/`Rung.HOVER_ONLY` (G3); node `HOVER_ONLY` anchors at the `ABOVE` base slot (G4); the enclosure rule maps the hull polygon to screen space without mutating `HullGeometry` (G5); placement uses `displayText()` as its full text (G6); `PerformanceMeasurements.Stage.SEPARATION("separation")` is added after `CORRECTION` (G7).
- Accepted residuals: C7 and C15 are not pinnable (no pre-change baseline exists); both new performance stages are diagnostic-only (`-1`) and the plan records baselines without inventing thresholds. §2.3's `GraphUpdateCoordinator.java:561` republish needs no code change. Churn values (settled positions, idle frame counts, golden fixture SHA-256) are recorded empirically in Tasks 3 and 10, never guessed.
- Existing test methods are never deleted or weakened except the exact migrations named in Tasks 8 and 9; when an expected value moves because the projection or the springs legitimately moved, report it in the task report and update the literal, never the assertion.

## Task 1: Add the enforcing projection stage and its residual result

**Implementer tier:** Advanced
**Lane:** projection-layout
**Depends on:** none

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationResult.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java:84-87`

**Interfaces:**
- Consumes: `LayoutPositions.of(Map<ProjectedNodeKey, LayoutPoint> nodes, Map<EnclosureHullKey, LayoutPoint> anchors)`, `LayoutPositions.nodes()`, `LayoutPositions.anchors()` (immutable `LinkedHashMap` copies); `GraphProjection.nodes()`, `GraphProjection.enclosures()`, `GraphProjection.edges()`, `GraphProjection.prominence()`; `NodeProminence.scale()`; `ProjectedNode.of(ProjectedNodeKey, SafeNodeLabel, String, boolean)`; `ProjectedNodeKey.of(SourceNodeKey)`; `SourceNodeKey.persisted(NodeReference)`; `NodeReference.of(MapReferenceId, PersistedNodeId)`; `PersistedNodeId.of(String)`; `MapReferenceId.of(String)`; `SafeNodeLabel.of(String full, String display)`; `LayoutPoint.of(double, double)`, `LayoutPoint.x()`, `LayoutPoint.y()`; `GraphProjection.projected(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>, List<RelationshipResolution>, List<PinProjection>)`; `ProjectedEdge.of(ProjectedEdgeKey, List<EdgeContributor>)`; `ProjectedEdgeKey.of(ProjectedEndpointKey, ProjectedEndpointKey)`; `ProjectedEndpointKey.ofNode(ProjectedNodeKey)`; `EdgeContributor.nativeConnector(ConnectorSnapshot, ProjectedEndpointKey, ProjectedEndpointKey)`; `ConnectorSnapshot.of(int, ConnectorDescriptor)`; `ConnectorDescriptor.of(SourceNodeKey, NodeReference, boolean, boolean, String, String, String)`.
- Produces: `public final class NodeSeparationProjection` with `public static final double MIN_GAP = 6.0`, package-private `static final int MAX_PASSES = 64`, `static final double RELAXATION = 0.5`, `static final double SPATIAL_CELL = 34.0`, and `public NodeSeparationResult project(GraphProjection projection, LayoutPositions positions, Set<ProjectedNodeKey> pinned)`; `public final class NodeSeparationResult` with `public static NodeSeparationResult of(LayoutPositions, int residualViolations, int passes)`, `positions()`, `residualViolations()`, `passes()`; the new tests of `NodeSeparationProjectionShould`; the `publicLayoutTypes()` extension in `GraphStreamBoundaryShould`.

- [ ] **Step 1: Write the failing test**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class NodeSeparationProjectionShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final int PROMINENT_TARGETS = 14;

    @Test
    public void separatesTwoProminentNodesAndClearsTheResidual() {
        GraphProjection projection = redPhaseProjection();
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "p1", "p2" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(24.0, 0.0) }));

        assertThat(violationCount(projection, raw)).isEqualTo(1);

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().nodes().get(key("p1"))).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(key("p2"))).isEqualTo(LayoutPoint.of(29.0, 0.0));
        assertThat(distance(result.positions().nodes().get(key("p1")),
            result.positions().nodes().get(key("p2")))).isEqualTo(34.0);
        assertThat(result.residualViolations()).isZero();
        assertThat(result.passes()).isEqualTo(2);
        assertThat(violationCount(projection, result.positions())).isZero();
    }

    @Test
    public void returnsAlreadySatisfiedProminentPositionsUnchanged() {
        GraphProjection projection = redPhaseProjection();
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "p1", "p2" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(40.0, 0.0) }));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().nodes()).isEqualTo(raw.nodes());
        assertThat(result.residualViolations()).isZero();
        assertThat(result.passes()).isEqualTo(1);
    }

    @Test
    public void reportsTheNonConvergingSandwichAtThePassCap() {
        GraphProjection projection = projection("a", "b", "m");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b", "m" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(40.0, 0.0),
                LayoutPoint.of(20.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = pinned("a", "b");

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.passes()).isEqualTo(64);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(40.0, 0.0));
        assertThat(result.positions().nodes().get(key("m"))).isEqualTo(LayoutPoint.of(18.0, 0.0));
        assertThat(result.residualViolations()).isEqualTo(1);
        assertFinite(result.positions());
    }

    @Test
    public void leavesPinnedPinnedOverlapUntouchedAndCounted() {
        GraphProjection projection = projection("a", "b", "m");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b", "m" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0),
                LayoutPoint.of(10.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = pinned("a", "b");

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.passes()).isEqualTo(2);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(20.0, 0.0));
        assertThat(result.positions().nodes().get(key("m"))).isEqualTo(LayoutPoint.of(42.0, 0.0));
        assertThat(result.residualViolations()).isEqualTo(1);
    }

    @Test
    public void convergesASolvablePairToTheMinimumDistance() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) }));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.passes()).isEqualTo(2);
        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(21.0, 0.0));
        assertThat(result.residualViolations()).isZero();
        assertThat(distance(result.positions().nodes().get(key("a")),
            result.positions().nodes().get(key("b")))).isEqualTo(22.0);
    }

    @Test
    public void separatesCoincidentParticlesDeterministically() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(0.0, 0.0) }));

        NodeSeparationResult first = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());
        NodeSeparationResult second = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(first.passes()).isEqualTo(3);
        assertThat(first.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-11.0, 0.0));
        assertThat(first.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(11.0, 0.0));
        assertThat(first.residualViolations()).isZero();
        assertThat(second.positions().nodes()).isEqualTo(first.positions().nodes());
        assertFinite(first.positions());
    }

    @Test
    public void copiesAnchorsThroughUnchanged() {
        GraphProjection projection = projection("a", "b");
        EnclosureHullKey hull = EnclosureHullKey.of(Collections.<org.freeplane.plugin.graph.projection.EnclosureKey>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) });
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hull, LayoutPoint.of(7.5, -3.25));
        LayoutPositions raw = LayoutPositions.of(nodes, anchors);

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet());

        assertThat(result.positions().anchors()).isEqualTo(raw.anchors());
        assertThat(result.positions().anchors().get(hull)).isEqualTo(LayoutPoint.of(7.5, -3.25));
    }

    @Test
    public void ignoresPinnedKeysThatAreNotInThePositions() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(0.0, 0.0), LayoutPoint.of(20.0, 0.0) }));
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        pinned.add(key("missing"));

        NodeSeparationResult result = new NodeSeparationProjection().project(projection, raw, pinned);

        assertThat(result.positions().nodes().get(key("a"))).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(result.positions().nodes().get(key("b"))).isEqualTo(LayoutPoint.of(21.0, 0.0));
        assertThat(result.residualViolations()).isZero();
    }

    @Test
    public void rejectsNonFiniteInputCoordinates() {
        GraphProjection projection = projection("a", "b");
        LayoutPositions raw = positions(projection, orderedPoints(new String[] { "a", "b" },
            new LayoutPoint[] { LayoutPoint.of(Double.NaN, 0.0), LayoutPoint.of(20.0, 0.0) }));

        assertThatThrownBy(() -> new NodeSeparationProjection().project(projection, raw,
            Collections.<ProjectedNodeKey>emptySet())).isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphProjection redPhaseProjection() {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node("p1"));
        nodes.add(node("p2"));
        for (int index = 0; index < 2 * PROMINENT_TARGETS; index++) {
            nodes.add(node("t" + index));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int index = 0; index < PROMINENT_TARGETS; index++) {
            edges.add(directedEdge("p1", "t" + index));
            edges.add(directedEdge("p2", "t" + (PROMINENT_TARGETS + index)));
        }
        return GraphProjection.projected(1L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static GraphProjection projection(String... ids) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (String id : ids) {
            nodes.add(node(id));
        }
        return GraphProjection.structure(1L, nodes, Collections.<ProjectedEnclosure>emptyList());
    }

    private static ProjectedNode node(String id) {
        return ProjectedNode.of(key(id), SafeNodeLabel.of(id, id), "Map", false);
    }

    private static ProjectedEdge directedEdge(String from, String to) {
        SourceNodeKey source = source(from);
        NodeReference target = reference(to);
        ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofNode(key(from));
        ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(key(to));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, target, false, true,
            "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(0, descriptor), sourceEndpoint, targetEndpoint);
        return ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(contributor));
    }

    private static LayoutPositions positions(GraphProjection projection, Map<ProjectedNodeKey, LayoutPoint> nodes) {
        return LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    }

    private static Map<ProjectedNodeKey, LayoutPoint> orderedPoints(String[] ids, LayoutPoint[] points) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < ids.length; index++) {
            nodes.put(key(ids[index]), points[index]);
        }
        return nodes;
    }

    private static Set<ProjectedNodeKey> pinned(String... ids) {
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (String id : ids) {
            pinned.add(key(id));
        }
        return pinned;
    }

    private static int violationCount(GraphProjection projection, LayoutPositions positions) {
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        int violations = 0;
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                double need = radius(projection, keys.get(first)) + radius(projection, keys.get(second)) + 6.0;
                if (distance(positions.nodes().get(keys.get(first)), positions.nodes().get(keys.get(second))) < need) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double radius(GraphProjection projection, ProjectedNodeKey key) {
        NodeProminence prominence = projection.prominence().get(key);
        return 8.0 * (prominence == null ? 1.0 : prominence.scale());
    }

    private static double distance(LayoutPoint first, LayoutPoint second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private static void assertFinite(LayoutPositions positions) {
        for (LayoutPoint point : positions.nodes().values()) {
            assertThat(Double.isFinite(point.x()) && Double.isFinite(point.y())).isTrue();
        }
    }

    private static ProjectedNodeKey key(String id) {
        return ProjectedNodeKey.of(source(id));
    }

    private static SourceNodeKey source(String id) {
        return SourceNodeKey.persisted(reference(id));
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }
}
```

Extend `GraphStreamBoundaryShould.publicLayoutTypes()` (current lines 84-87) to:

```java
    private static List<Class<?>> publicLayoutTypes() {
        return Arrays.<Class<?>>asList(LayoutEngine.class, LayoutCalibration.class, LayoutRequest.class,
            LayoutFrame.class, NodeSeparationProjection.class, NodeSeparationResult.class,
            GraphStreamLayoutFactory.class);
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" -PTestLoggingFull
```

Expected: FAIL with compilation errors `cannot find symbol: class NodeSeparationProjection` and `cannot find symbol: class NodeSeparationResult`.

- [ ] **Step 3: Write `NodeSeparationResult`**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationResult.java` with exactly this content:

```java
package org.freeplane.plugin.graph.layout;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class NodeSeparationResult {
    private final LayoutPositions positions;
    private final int residualViolations;
    private final int passes;

    private NodeSeparationResult(final LayoutPositions positions, final int residualViolations,
            final int passes) {
        this.positions = Objects.requireNonNull(positions, "positions");
        if (residualViolations < 0) {
            throw new IllegalArgumentException("Residual violations must be nonnegative");
        }
        if (passes < 1 || passes > NodeSeparationProjection.MAX_PASSES) {
            throw new IllegalArgumentException("Pass count must be within the pass budget");
        }
        this.residualViolations = residualViolations;
        this.passes = passes;
    }

    public static NodeSeparationResult of(final LayoutPositions positions, final int residualViolations,
            final int passes) {
        return new NodeSeparationResult(positions, residualViolations, passes);
    }

    public LayoutPositions positions() {
        return positions;
    }

    public int residualViolations() {
        return residualViolations;
    }

    public int passes() {
        return passes;
    }
}
```

- [ ] **Step 4: Write `NodeSeparationProjection`**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java` with exactly this content:

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class NodeSeparationProjection {
    public static final double MIN_GAP = 6.0;
    static final int MAX_PASSES = 64;
    static final double RELAXATION = 0.5;
    static final double SPATIAL_CELL = 34.0;
    private static final double NODE_RADIUS = 8.0;
    private static final double DEFAULT_SCALE = 1.0;
    private static final double COINCIDENT_AXIS_X = 1.0;
    private static final double COINCIDENT_AXIS_Y = 0.0;
    private static final double COINCIDENT_DISTANCE = 1.0;

    /** Pure. Never mutates the arguments. */
    public NodeSeparationResult project(final GraphProjection projection, final LayoutPositions positions,
            final Set<ProjectedNodeKey> pinned) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(pinned, "pinned");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");

        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        final int count = keys.size();
        final double[] x = new double[count];
        final double[] y = new double[count];
        final double[] radius = new double[count];
        final boolean[] isPinned = new boolean[count];
        for (int index = 0; index < count; index++) {
            final ProjectedNodeKey key = keys.get(index);
            final LayoutPoint point = positions.nodes().get(key);
            x[index] = point.x();
            y[index] = point.y();
            radius[index] = NODE_RADIUS * prominenceScale(projection, key);
            isPinned[index] = pinned.contains(key);
        }

        int passes = 0;
        for (int pass = 1; pass <= MAX_PASSES; pass++) {
            passes = pass;
            final Map<Long, List<Integer>> cells = new HashMap<Long, List<Integer>>();
            for (int index = 0; index < count; index++) {
                insert(cells, index, x[index], y[index]);
            }
            boolean moved = false;
            for (int first = 0; first < count; first++) {
                final List<Integer> candidates = candidates(cells, x[first], y[first]);
                Collections.sort(candidates);
                for (final Integer candidate : candidates) {
                    final int second = candidate.intValue();
                    if (second <= first) {
                        continue;
                    }
                    final double need = radius[first] + radius[second] + MIN_GAP;
                    double dx = x[second] - x[first];
                    double dy = y[second] - y[first];
                    double distance = Math.hypot(dx, dy);
                    if (distance == 0.0) {
                        dx = COINCIDENT_AXIS_X;
                        dy = COINCIDENT_AXIS_Y;
                        distance = COINCIDENT_DISTANCE;
                    }
                    if (distance >= need || isPinned[first] && isPinned[second]) {
                        continue;
                    }
                    final double penetration = need - distance;
                    final double unitX = dx / distance;
                    final double unitY = dy / distance;
                    if (isPinned[first]) {
                        move(cells, second, x, y, penetration * unitX, penetration * unitY);
                    }
                    else if (isPinned[second]) {
                        move(cells, first, x, y, -penetration * unitX, -penetration * unitY);
                    }
                    else {
                        final double half = RELAXATION * penetration;
                        move(cells, first, x, y, -half * unitX, -half * unitY);
                        move(cells, second, x, y, half * unitX, half * unitY);
                    }
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }

        final Map<ProjectedNodeKey, LayoutPoint> projected =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < count; index++) {
            if (!Double.isFinite(x[index]) || !Double.isFinite(y[index])) {
                throw new IllegalArgumentException("Projected coordinates must be finite");
            }
            projected.put(keys.get(index), LayoutPoint.of(x[index], y[index]));
        }
        final LayoutPositions result = LayoutPositions.of(projected, positions.anchors());
        return NodeSeparationResult.of(result, residualViolations(x, y, radius, count), passes);
    }

    private static void move(final Map<Long, List<Integer>> cells, final int index, final double[] x,
            final double[] y, final double deltaX, final double deltaY) {
        remove(cells, index, x[index], y[index]);
        x[index] += deltaX;
        y[index] += deltaY;
        insert(cells, index, x[index], y[index]);
    }

    private static void insert(final Map<Long, List<Integer>> cells, final int index, final double x,
            final double y) {
        final Long key = Long.valueOf(cellKey(cell(x), cell(y)));
        List<Integer> bucket = cells.get(key);
        if (bucket == null) {
            bucket = new ArrayList<Integer>();
            cells.put(key, bucket);
        }
        bucket.add(Integer.valueOf(index));
    }

    private static void remove(final Map<Long, List<Integer>> cells, final int index, final double x,
            final double y) {
        final List<Integer> bucket = cells.get(Long.valueOf(cellKey(cell(x), cell(y))));
        if (bucket != null) {
            bucket.remove(Integer.valueOf(index));
        }
    }

    private static List<Integer> candidates(final Map<Long, List<Integer>> cells, final double x,
            final double y) {
        final int cellX = cell(x);
        final int cellY = cell(y);
        final List<Integer> result = new ArrayList<Integer>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                final List<Integer> bucket = cells.get(Long.valueOf(cellKey(cellX + dx, cellY + dy)));
                if (bucket != null) {
                    result.addAll(bucket);
                }
            }
        }
        return result;
    }

    private static long cellKey(final int cellX, final int cellY) {
        return (long) cellX << 32 ^ (long) cellY & 0xffffffffL;
    }

    private static int cell(final double value) {
        return (int) Math.floor(value / SPATIAL_CELL);
    }

    private static int residualViolations(final double[] x, final double[] y, final double[] radius,
            final int count) {
        int violations = 0;
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                final double need = radius[first] + radius[second] + MIN_GAP;
                if (Math.hypot(x[second] - x[first], y[second] - y[first]) < need) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double prominenceScale(final GraphProjection projection, final ProjectedNodeKey key) {
        final NodeProminence prominence = projection.prominence().get(key);
        return prominence == null ? DEFAULT_SCALE : prominence.scale();
    }

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final LayoutPoint point : values.values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout positions must be finite");
            }
        }
    }
}
```

The processing order of §2.1 step 1 is the `positions.nodes()` iteration order (the immutable `LinkedHashMap` copy of `LayoutPositions.java:34`); the spatial grid is only a completeness-preserving prefilter, and the residual is recomputed over all pairs on the returned positions. Do not reorder keys, do not sort by prominence, and do not add anchor movement.

- [ ] **Step 5: Run the tests and confirm they pass**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.NodeSeparationProjectionShould" --tests "org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould" -PTestLoggingFull
```

Expected: PASS. `NodeSeparationProjectionShould` runs 9 tests with 0 failures; `GraphStreamBoundaryShould` is unchanged except for the allowlist and stays green. If any pinned coordinate differs, stop and report the mismatch instead of adjusting the assertion.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationProjection.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/NodeSeparationResult.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/NodeSeparationProjectionShould.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java
git commit -m "Add the node separation projection stage with its pinned residual fixtures [2026-09-12-graph-node-separation]"
```

## Task 2: Carry the residual through LayoutFrame and every publication path

**Implementer tier:** Advanced
**Lane:** projection-layout
**Depends on:** Task 1

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java:12-62`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java:34-38`, `:276-299`, `:340-348`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:57`, `:112`, `:732-748`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java:132-138`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java` (new tests plus the `rawPositions` fixture spacing and every `LayoutFrame.of` call site)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java` (new test plus 2 `LayoutFrame.of` call sites at `:1144`, `:1351`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java` (new test plus 3 call sites)
- Test: the remaining 10 of the 13 `LayoutFrame.of`-constructing files listed in spec §6: `canvas/AccessibleGraphCanvasShould.java`, `canvas/GraphCanvasPaintShould.java`, `canvas/GraphInteractionControllerShould.java`, `canvas/GraphSearchModelShould.java`, `command/ContributorDeletionPlanShould.java`, `control/GraphWorkspaceCommandAcceptanceShould.java`, `performance/PerformanceTripwiresShould.java`, `smoke/GraphWorkspaceUiEvidence.java`, `window/GraphWorkspaceWindowModelShould.java`, `window/WorkspaceDialogsShould.java`

**Interfaces:**
- Consumes: `NodeSeparationResult.of(LayoutPositions, int, int)`, `NodeSeparationResult.positions()`, `NodeSeparationResult.residualViolations()`, `new NodeSeparationProjection().project(GraphProjection, LayoutPositions, Set<ProjectedNodeKey>)` from Task 1; `LayoutRequest.pins()` returning `List<PinProjection>`; `PinProjection.active()` and `PinProjection.projectedNode()` returning `Optional<ProjectedNodeKey>`; `LayoutWorker.lastValidFrame()`; `LayoutSettleLoop`'s private `Run` fields `projection` and `request`, and `covers(GraphProjection, LayoutPositions)`; `MapTierCorrection.CorrectionResult.positions()`.
- Produces: `LayoutFrame.UNVERIFIED = -1`; `LayoutFrame.of(long, LayoutPositions, boolean, int)`; `LayoutFrame.residualViolations()`; `LayoutFrame.verified()`; the `accept` order `MapTierCorrection -> NodeSeparationProjection -> PerceptualIdlePolicy.observe -> LayoutFrame`; a real residual on `LayoutWorker.accept`, `LayoutWorker.failedFrame`, `LayoutSettleLoop.failedFrame` and the initial empty frame; new tests `LayoutWorkerShould.projectsAcceptedFramesAndPublishesTheRecomputedResidual`, `LayoutWorkerShould.carriesTheRetainedResidualIntoAFailedFrame`, `LayoutWorkerShould.startsFromAnEmptyFailedFrameWithAZeroResidual`, and `LayoutSettleLoopShould.publishesAFallbackFrameWithARecomputedResidual`.

- [ ] **Step 1: Replace `LayoutFrame` with the residual-carrying class**

Replace the whole body of `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java` (current lines 12-81) with exactly this content, keeping the file's existing package and imports:

```java
public final class LayoutFrame {
    public static final int UNVERIFIED = -1;

    private final long stepIndex;
    private final LayoutPositions positions;
    private final boolean failed;
    private final int residualViolations;
    private final List<LayoutConflict> conflicts;
    private final PerceptualIdlePolicy.IdleMeasurement idle;

    private LayoutFrame(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations, final List<LayoutConflict> conflicts,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("Layout frame index must be nonnegative");
        }
        if (residualViolations < UNVERIFIED) {
            throw new IllegalArgumentException("Residual violations must be >= UNVERIFIED");
        }
        this.stepIndex = stepIndex;
        this.positions = Objects.requireNonNull(positions, "positions");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");
        this.failed = failed;
        this.residualViolations = residualViolations;
        this.conflicts = copyConflicts(conflicts);
        this.idle = Objects.requireNonNull(idle, "idle");
    }

    /** Engine-internal frames only: the residual is unknown and `LayoutWorker.accept` re-wraps them. */
    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed) {
        return of(stepIndex, positions, failed, UNVERIFIED);
    }

    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations) {
        return new LayoutFrame(stepIndex, positions, failed, residualViolations,
            Collections.<LayoutConflict>emptyList(), PerceptualIdlePolicy.IdleMeasurement.initial());
    }

    public static LayoutFrame withDiagnostics(final LayoutFrame raw, final List<LayoutConflict> conflicts,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        final LayoutFrame value = Objects.requireNonNull(raw, "raw");
        return new LayoutFrame(value.stepIndex, value.positions, value.failed, value.residualViolations,
            conflicts, idle);
    }

    public long stepIndex() {
        return stepIndex;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public boolean failed() {
        return failed;
    }

    public int residualViolations() {
        return residualViolations;
    }

    public boolean verified() {
        return residualViolations >= 0;
    }

    public List<LayoutConflict> conflicts() {
        return conflicts;
    }

    public PerceptualIdlePolicy.IdleMeasurement idle() {
        return idle;
    }

    private static List<LayoutConflict> copyConflicts(final List<LayoutConflict> values) {
        Objects.requireNonNull(values, "conflicts");
        final List<LayoutConflict> copy = new ArrayList<LayoutConflict>(values.size());
        for (final LayoutConflict value : values) {
            copy.add(Objects.requireNonNull(value, "conflicts entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final Map.Entry<K, LayoutPoint> entry : values.entrySet()) {
            final LayoutPoint point = Objects.requireNonNull(entry.getValue(), kind + " position");
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout frame coordinates must be finite");
            }
        }
    }
}
```

- [ ] **Step 2: Project in `LayoutWorker.accept` and carry the residual in the failure paths**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`, add imports `java.util.LinkedHashSet` and `org.freeplane.plugin.graph.projection.PinProjection`, then:

Replace `EMPTY_FAILED_FRAME` (current lines 34-38) with:

```java
    private static final LayoutFrame EMPTY_FAILED_FRAME = LayoutFrame.withDiagnostics(
        LayoutFrame.of(0L, LayoutPositions.of(
            Collections.<ProjectedNodeKey, org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap()), true, 0),
        Collections.<LayoutConflict>emptyList(), PerceptualIdlePolicy.IdleMeasurement.initial());
```

Replace the body of `accept` from `final MapTierCorrection.CorrectionResult correction` through `return decorated;` (current lines 287-298) with:

```java
        final MapTierCorrection.CorrectionResult correction = mapCorrection.apply(request.projection(),
            raw.positions(), geometry, request.pins());
        final NodeSeparationResult separation = new NodeSeparationProjection().project(request.projection(),
            correction.positions(), pinnedNodes(request.pins()));
        final LayoutPositions corrected = separation.positions();
        final LayoutPositions before = previousCorrectedPositions == null ? corrected : previousCorrectedPositions;
        final PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
        final LayoutFrame decorated = LayoutFrame.withDiagnostics(
            LayoutFrame.of(raw.stepIndex(), corrected, false, separation.residualViolations()),
            correction.conflicts(), idle);
        currentRequest = request;
        hasRequest = true;
        previousCorrectedPositions = corrected;
        lastValidFrame = decorated;
        return decorated;
```

Add this private helper next to `validateCoverage`:

```java
    private static Set<ProjectedNodeKey> pinnedNodes(final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active() && pin.projectedNode().isPresent()) {
                pinned.add(pin.projectedNode().get());
            }
        }
        return pinned;
    }
```

Replace `failedFrame` (current lines 340-348) with:

```java
    private LayoutFrame failedFrame(final long requestedIndex) {
        final LayoutFrame retained = lastValidFrame;
        if (retained == EMPTY_FAILED_FRAME) {
            return EMPTY_FAILED_FRAME;
        }
        final long index = requestedIndex >= 0L ? requestedIndex : retained.stepIndex();
        return LayoutFrame.withDiagnostics(
            LayoutFrame.of(index, retained.positions(), true, retained.residualViolations()),
            retained.conflicts(), retained.idle());
    }
```

Order is normative (§4.1): `MapTierCorrection` first, then `NodeSeparationProjection`, then `PerceptualIdlePolicy.observe`, then frame construction. Do not move the projection before the correction and do not add anything that moves positions after it. `GraphStreamLayoutEngine.frame` (current `GraphStreamLayoutEngine.java:346`) keeps the 3-argument `LayoutFrame.of`; engine frames are never published because `accept` re-wraps them.

- [ ] **Step 3: Route the settle-loop fallback through the projection**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`, add imports `org.freeplane.plugin.graph.layout.NodeSeparationProjection`, `org.freeplane.plugin.graph.layout.NodeSeparationResult`, `org.freeplane.plugin.graph.projection.PinProjection`, and `java.util.LinkedHashSet`, then replace the method `failedFrame(final Run run, final LayoutFrame source)` (current lines 732-748) with:

```java
    private LayoutFrame failedFrame(final Run run, final LayoutFrame source) {
        LayoutFrame retained = null;
        try {
            retained = worker.lastValidFrame();
        }
        catch (RuntimeException ignored) {
            // A failed worker may not have a readable retained frame.
        }
        final boolean retainedUsable = retained != null && covers(run.projection, retained.positions());
        final LayoutPositions positions;
        final int residual;
        if (retainedUsable) {
            positions = retained.positions();
            residual = retained.residualViolations();
        }
        else {
            final NodeSeparationResult separation = new NodeSeparationProjection().project(run.projection,
                fallbackPositions(run.projection), pinnedNodes(run.request.pins()));
            positions = separation.positions();
            residual = separation.residualViolations();
        }
        final long index = source != null ? source.stepIndex() : retained == null ? 0L : retained.stepIndex();
        if (retained == null) {
            return LayoutFrame.of(index, positions, true, residual);
        }
        final List<LayoutConflict> conflicts = retained.conflicts();
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, true, residual),
            conflicts, retained.idle());
    }

    private static Set<ProjectedNodeKey> pinnedNodes(final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active() && pin.projectedNode().isPresent()) {
                pinned.add(pin.projectedNode().get());
            }
        }
        return pinned;
    }
```

`Set` is already imported in that file; if it is not, add `java.util.Set`. The retained frame's residual is used only when the retained frame covers the projection; otherwise the fallback positions are projected and their residual is used. Leave the `labels.place` calls and `LabelAssembler` alone in this task; Task 9 removes them.

- [ ] **Step 4: Give the initial empty frame a real residual**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java`, replace lines 134-135 with:

```java
        final LayoutFrame initialLayout = LayoutFrame.of(0L,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false, 0);
```

`publishFailure` (current lines 551-566) needs no change: it republishes `state.layout()` (or `state.withStatus(FAILED)`) and therefore preserves the residual it already carries.

- [ ] **Step 5: Migrate every test `LayoutFrame.of` call site to an explicit residual**

Run:

```bash
grep -rn "LayoutFrame.of(" freeplane_plugin_graph/src/test/java
```

Expected at revision `a97550744c`: 39 call sites across exactly these 13 files (counts in parentheses): `window/GraphWorkspaceWindowModelShould.java` (9), `canvas/GraphCanvasPaintShould.java` (7), `canvas/GraphInteractionControllerShould.java` (4), `window/WorkspaceDialogsShould.java` (3), `control/GraphUpdateCoordinatorShould.java` (3), `performance/PerformanceTripwiresShould.java` (2), `layout/LayoutWorkerShould.java` (2), `integration/GraphWorkspaceCommandAcceptanceShould.java` (2), `control/LayoutSettleLoopShould.java` (2), `canvas/AccessibleGraphCanvasShould.java` (2), `smoke/GraphWorkspaceUiEvidence.java` (1), `command/ContributorDeletionPlanShould.java` (1), `canvas/GraphSearchModelShould.java` (1). Rewrite every 3-argument call `LayoutFrame.of(index, positions, failed)` to the 4-argument form `LayoutFrame.of(index, positions, failed, 0)`. Where a test asserts a published frame whose positions violate the disc invariant, use that test's own recomputed violation count instead of `0` and add a comment naming the fixture; do not use `-1` in a test that passes the frame to `CanvasState`, and do not leave a 3-argument call in test sources.

- [ ] **Step 6: Fix the `LayoutWorkerShould` raw-position fixture and add the residual tests**

The existing `rawPositions` helper in `LayoutWorkerShould.java` places the two nodes of each map 1.0 world unit apart, so inserting the projection would move them and break `assertUniformDelta` (which asserts that `MapTierCorrection` translates each map rigidly). Replace the `x`/`y` selection inside `rawPositions` with positions that are already separated, and keep the two maps close enough for the correction to fire:

```java
            final double x = MAP_ONE.equals(map) ? 0.0 : 40.0;
            final double y = node.key().equals(NODE_ONE_OTHER) || node.key().equals(NODE_TWO_OTHER) ? 40.0 : 0.0;
```

Every within-map and cross-map pair is then at distance 40 (need is 22), so the projection is a no-op for the existing tests and their assertions stay exactly as written. Add these three tests inside `LayoutWorkerShould`, before the private `request()` helper:

```java
    @Test
    public void projectsAcceptedFramesAndPublishesTheRecomputedResidual() throws Exception {
        ProjectedNodeKey first = nodeKey(MAP_ONE, "sep-one");
        ProjectedNodeKey second = nodeKey(MAP_ONE, "sep-two");
        ProjectedNode firstNode = ProjectedNode.of(first, SafeNodeLabel.of("one", "one"), "Map", false);
        ProjectedNode secondNode = ProjectedNode.of(second, SafeNodeLabel.of("two", "two"), "Map", false);
        GraphProjection projection = GraphProjection.structure(1L, Arrays.asList(firstNode, secondNode),
            Collections.<ProjectedEnclosure>emptyList());
        LayoutRequest request = LayoutRequest.of(WORKSPACE, projection,
            ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new SeparatedPairEngine(first, second)),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame frame = await(worker.submit(request));

            assertThat(frame.failed()).isFalse();
            assertThat(frame.verified()).isTrue();
            assertThat(frame.residualViolations()).isZero();
            assertThat(frame.positions().nodes().get(first)).isEqualTo(LayoutPoint.of(-1.0, 0.0));
            assertThat(frame.positions().nodes().get(second)).isEqualTo(LayoutPoint.of(21.0, 0.0));
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void carriesTheRetainedResidualIntoAFailedFrame() throws Exception {
        CountingEngine engine = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame valid = await(worker.submit(request()));
            FailingAfterValidEngine failing = new FailingAfterValidEngine();
            LayoutWorker second = new LayoutWorker(new FixedEngineSupplier(failing),
                new PerceptualIdlePolicy(2, 0.1, 0.1));
            try {
                await(second.submit(request()));
                LayoutFrame failed = await(second.step());

                assertThat(valid.verified()).isTrue();
                assertThat(failed.failed()).isTrue();
                assertThat(failed.residualViolations()).isEqualTo(valid.residualViolations());
                assertThat(failed.positions()).isEqualTo(valid.positions());
            }
            finally {
                second.close();
            }
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void startsFromAnEmptyFailedFrameWithAZeroResidual() {
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new CountingEngine(new AtomicInteger(),
            new AtomicInteger())), PerceptualIdlePolicy.spikeDefaults());
        try {
            LayoutFrame sentinel = worker.lastValidFrame();

            assertThat(sentinel.failed()).isTrue();
            assertThat(sentinel.residualViolations()).isZero();
            assertThat(sentinel.verified()).isTrue();
        }
        finally {
            worker.close();
        }
    }
```

Add these two helper engine classes next to `FailingEngine`:

```java
    private static final class SeparatedPairEngine implements LayoutEngine {
        private final ProjectedNodeKey first;
        private final ProjectedNodeKey second;

        SeparatedPairEngine(ProjectedNodeKey first, ProjectedNodeKey second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public LayoutFrame apply(LayoutRequest request) {
            return frame();
        }

        @Override
        public LayoutFrame step() {
            return frame();
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }

        private LayoutFrame frame() {
            Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            nodes.put(first, LayoutPoint.of(0.0, 0.0));
            nodes.put(second, LayoutPoint.of(20.0, 0.0));
            return LayoutFrame.of(1L, LayoutPositions.of(nodes,
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false);
        }
    }

    private static final class FailingAfterValidEngine extends CountingEngine {
        FailingAfterValidEngine() {
            super(new AtomicInteger(), new AtomicInteger());
        }

        @Override
        public LayoutFrame step() {
            throw new IllegalStateException("step failed");
        }
    }
```

- [ ] **Step 7: Add the settle-loop fallback residual test**

In `LayoutSettleLoopShould.java`, add this test next to the existing `publishesAFailedCurrentGenerationWhenItsInitialFrameFails` test (the test that already drives `fallbackPositions` with a populated projection and an empty failed retained frame):

```java
    @Test
    public void publishesAFallbackFrameWithARecomputedResidual() throws Exception {
        // Reuse the fixture of publishesAFailedCurrentGenerationWhenItsInitialFrameFails and add:
        final CanvasState failed = lastPublishedState();
        final LayoutPositions positions = failed.layout().positions();
        assertThat(failed.layout().failed()).isTrue();
        assertThat(failed.layout().verified()).isTrue();
        assertThat(failed.layout().residualViolations())
            .isEqualTo(independentlyCountViolations(failed.projection(), positions));
    }

    private static int independentlyCountViolations(final GraphProjection projection,
            final LayoutPositions positions) {
        final java.util.List<ProjectedNodeKey> keys =
            new java.util.ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        int violations = 0;
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                final double firstRadius = radius(projection, keys.get(first));
                final double secondRadius = radius(projection, keys.get(second));
                final LayoutPoint firstPoint = positions.nodes().get(keys.get(first));
                final LayoutPoint secondPoint = positions.nodes().get(keys.get(second));
                if (Math.hypot(firstPoint.x() - secondPoint.x(), firstPoint.y() - secondPoint.y())
                        < firstRadius + secondRadius + 6.0) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double radius(final GraphProjection projection, final ProjectedNodeKey key) {
        final org.freeplane.plugin.graph.projection.NodeProminence prominence =
            projection.prominence().get(key);
        return 8.0 * (prominence == null ? 1.0 : prominence.scale());
    }
```

Use that test's existing `lastPublishedState()`/canvas-listener accessor if one exists; if it exposes the state through a captured `CanvasState` variable, call `independentlyCountViolations` on that value directly. The two lines at `:1144` and `:1351` that call `LayoutFrame.of` must be migrated to the 4-argument form in Step 5.

- [ ] **Step 8: Add the initial-frame residual test**

In `GraphUpdateCoordinatorShould.java`, add:

```java
    @Test
    public void loadsAnEmptyInitialFrameWithAZeroResidual() {
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator();
        try {
            CanvasState state = coordinator.state();
            assertThat(state.layout().failed()).isFalse();
            assertThat(state.layout().verified()).isTrue();
            assertThat(state.layout().residualViolations()).isZero();
        }
        finally {
            coordinator.close();
        }
    }
```

Use the file's existing construction and close helpers; if the coordinator's field accessor is named differently, use the accessor the file already uses. `LayoutFrame.UNVERIFIED` must never be observable from a published `CanvasState`. In the existing failure test `retainsFailedStateAndSuppressesDelayedCanvasFromAnOlderAcceptedGeneration` (current `:524-545`), add assertions that the failed state's layout keeps its residual and is verified:

```java
            assertThat(failed.layout().verified()).isTrue();
            assertThat(failed.layout().residualViolations())
                .isEqualTo(expected.layout().residualViolations());
```

where `expected` is the state the fixture published before the failure. This covers the §2.3 republish row (`GraphUpdateCoordinator.java:561`) without changing `publishFailure`.

- [ ] **Step 9: Prove the omitted-fix guard is falsifiable**

Temporarily comment out the two lines that assign `corrected` from the projection in `LayoutWorker.accept` and instead use `final LayoutPositions corrected = correction.positions();` with `LayoutFrame.of(raw.stepIndex(), corrected, false, 0)`, then run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.LayoutWorkerShould" -PTestLoggingFull
```

Expected: FAIL in `projectsAcceptedFramesAndPublishesTheRecomputedResidual`, because the published positions are the unprojected `(0,0)`/`(20,0)` pair. Restore the projection lines exactly and confirm `git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java` reports no diff against the Step 2 state. Do not commit the probe state.

- [ ] **Step 10: Run the layout and control suites**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.*" --tests "org.freeplane.plugin.graph.control.*" -PTestLoggingFull
```

Expected: PASS. `LayoutWorkerShould` gains 3 tests, `LayoutSettleLoopShould` gains 1, `GraphUpdateCoordinatorShould` gains 1; every other test in those packages keeps its existing count and stays green. If an existing assertion moves because the projection legitimately displaced a fixture, report the observed value in the task report and update only that literal.

- [ ] **Step 11: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph
git commit -m "Publish the recomputed separation residual on every frame path [2026-09-12-graph-node-separation]"
```

## Task 3: Align relationship and containment rest lengths with the disc invariant

**Implementer tier:** Advanced
**Lane:** projection-layout
**Depends on:** Task 1

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:195-252` (topology rest lengths), `:490-560` (`BoundarySizes` helpers), `:670-691` (`Seeds.nodePosition`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:16-19`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:360-397` (idle baseline re-record)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/GroupOnlyProjectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java` (only if the module suite shows churn)

**Interfaces:**
- Consumes: `NodeSeparationProjection.MIN_GAP` (`public static final double 6.0`) from Task 1; `GraphProjection.prominence()` and `NodeProminence.scale()`; `ProjectedEnclosure.directNodes()` returning `List<ProjectedNodeKey>`; `GraphProjection.nodes()`, `ProjectedEnclosure.hullKey()`; `BoundarySizes.directNodeRingRadius(EnclosureHullKey)` and `BoundarySizes.enclosure(EnclosureHullKey)`; `Topology`/`Seeds`/`ForceLink` private structures of `GraphStreamLayoutEngine`.
- Produces: `BoundarySizes.contentRingRadius(EnclosureHullKey)`, `BoundarySizes.discRadius(ProjectedNodeKey)`, relation rest length `radiusOf(first) + radiusOf(second) + MIN_GAP`, containment rest length `contentRingRadius(hullKey)`, seed ring radius `contentRingRadius(parentKey)`; deletion of `TypedSpringBox.REST_LENGTH`; re-recorded idle and churn expectations.

- [ ] **Step 1: Record the pre-change churn baseline**

Before touching production code, run the four churn-affected suites plus the idle gate and record the current expectations in the task report:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould" --tests "org.freeplane.plugin.graph.projection.GroupOnlyProjectionShould" --tests "org.freeplane.plugin.graph.projection.ProjectionDeterminismShould" --tests "org.freeplane.plugin.graph.projection.StructuralProjectionShould" -PTestLoggingFull
```

Expected: PASS. In `TypedForcesShould.twoMapWorkspaceSettlesToIdle` (current `:382`), tighten the gate before the change: add `private static final int TWO_MAP_FIRST_IDLE_STEP = <observed>;` next to the other constants and change `assertThat(firstIdleStep).isLessThanOrEqualTo(2000);` to `assertThat(firstIdleStep).isEqualTo(TWO_MAP_FIRST_IDLE_STEP);`, where `<observed>` is the value the current run reports. Keep the 100-frame `rms <= 0.05` / `max <= 0.10` loop exactly as it is. Record the pre-change value in the report; it is the baseline the spring change is measured against.

- [ ] **Step 2: Delete the obsolete rest-length constant**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`, delete line 18 (`static final double REST_LENGTH = 24.0;`). Nothing else in the class reads it; if the compiler reports a caller, that caller is one of the two engine sites updated in Step 3 and must be migrated, not kept.

- [ ] **Step 3: Derive radii and rest lengths from the projection**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`, add the import `org.freeplane.plugin.graph.layout.NodeSeparationProjection` and add these helpers inside `BoundarySizes`, directly after the field declarations (current lines 497-499):

```java
        private final Map<ProjectedNodeKey, Double> discRadii = new LinkedHashMap<ProjectedNodeKey, Double>();

        double discRadius(final ProjectedNodeKey key) {
            final Double radius = discRadii.get(key);
            return radius == null ? 0.0 : radius.doubleValue();
        }

        double contentRingRadius(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            final int count = directNodes.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxRadius = 0.0;
            for (final ProjectedNodeKey nodeKey : directNodes) {
                maxRadius = Math.max(maxRadius, discRadius(nodeKey));
            }
            return (2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }
```

and in the `BoundarySizes(GraphProjection projection)` constructor (current lines 501-508) add, inside the node loop:

```java
                final double scale = projection.prominence().containsKey(node.key())
                    ? projection.prominence().get(node.key()).scale() : 1.0;
                discRadii.put(node.key(), Double.valueOf(NODE_RADIUS * scale));
```

Then in `topology` (current lines 195-252), after the `anchorIds`/`enclosureEndpoints` maps are built and before the edge loop, build the radius-by-particle-id index:

```java
        final Map<String, Double> radiusById = new LinkedHashMap<String, Double>();
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            radiusById.put(nodeIds.get(node.key()),
                Double.valueOf(sizes.discRadius(node.key())));
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            radiusById.put(anchorIds.get(enclosure.hullKey()),
                Double.valueOf(sizes.contentRingRadius(enclosure.hullKey())));
        }
```

Change the relationship link (current lines 228-231) to:

```java
                result.add(new ForceLink(first, second, ForceKind.RELATIONSHIP,
                    !edge.first().mapReferenceId().equals(edge.second().mapReferenceId()),
                    radiusOf(first, radiusById) + radiusOf(second, radiusById)
                        + NodeSeparationProjection.MIN_GAP));
```

Change the containment link (current lines 238-239) to:

```java
                    result.add(new ForceLink(anchor, node, ForceKind.CONTAINMENT, false,
                        sizes.contentRingRadius(enclosure.hullKey())));
```

Add the private helper next to `hierarchyRestLength`:

```java
    private static double radiusOf(final String particleId, final Map<String, Double> radiusById) {
        final Double radius = radiusById.get(particleId);
        return radius == null ? 0.0 : radius.doubleValue();
    }
```

`R(k) = (2*max_r + MIN_GAP) / (2*sin(pi/k))` for `k >= 2` and `R(k) = 0` for `k <= 1`; `max_r` is the largest disc radius among the anchor's direct node children, `0` when it has none. Calibration multipliers (`LayoutCalibration.containment/hierarchy/sameMap`) and `hierarchyRestLength` (`GROUP_SPACING`/`SUB_GROUP_SPACING`) are unchanged.

- [ ] **Step 4: Use the disc-derived ring radius for seeds**

In `Seeds.nodePosition` (current line 682), replace `final double radius = sizes.directNodeRingRadius(parentKey);` with:

```java
                final double radius = sizes.contentRingRadius(parentKey);
```

`BoundarySizes.directNodeRingRadius` stays: `directNodeReach -> sizeOf -> boundaryRadius` (`:558`) and `topRingPosition` keep the label-aware formula per design N4. No other seed, hierarchy or top-ring position changes.

- [ ] **Step 5: Re-record the churn expectations**

Run the four churn suites and the idle gate again:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould" --tests "org.freeplane.plugin.graph.projection.GroupOnlyProjectionShould" --tests "org.freeplane.plugin.graph.projection.ProjectionDeterminismShould" --tests "org.freeplane.plugin.graph.projection.StructuralProjectionShould" -PTestLoggingFull
```

For every failure, read the actual value from the assertion message, confirm it is a legitimate consequence of the new rest lengths (settled positions, distances or counts moved), and update the literal in the test. Never delete or weaken an assertion, and never change a tolerance to make a moved value fit. Update `TWO_MAP_FIRST_IDLE_STEP` to the new observed value and record both the pre-change and post-change values in the task report; the 100 stable frames must still satisfy `rms <= 0.05` and `max <= 0.10`. `ReferenceRepulsionFixture` is a shared fixture: if the new settled geometry is the assertion value, update the fixture constant rather than the callers.

- [ ] **Step 6: Enumerate any additional churn with the module suite**

Run the whole module suite and list every remaining failure:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: green after Step 5 updates. If `BoundarySeparationShould` or any other suite fails, treat it as churn from the new rest lengths: inspect the failure, update only the moved literal, and add the file to this task's commit. Stop and report if a failure is not a moved settled value (for example a lost invariant or a new exception), because that is a correctness finding, not churn.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph
git commit -m "Derive spring rest lengths and seed rings from disc radii [2026-09-12-graph-node-separation]"
```

## Task 4: Pin the three-map correction characterization

**Implementer tier:** Standard
**Lane:** projection-layout
**Depends on:** Task 1

**Files:**
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java` (new test plus the `square` helper overload)

**Interfaces:**
- Consumes: `MapTierCorrection.apply(GraphProjection, LayoutPositions, GraphGeometry)` and `CorrectionResult.positions()`; `HullGeometry.of(List<LayoutPoint> exactPolygon, LayoutPoint labelAnchor)`, `HullGeometry.exactPolygon()`; `HullIntersection.minimumSeparatingTranslation(HullGeometry, HullGeometry)` returning `LayoutPoint`; `HullIntersection.siblingOverlap(HullGeometry, HullGeometry)` returning `boolean`; `GraphProjection.projected(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>, List<RelationshipResolution>, List<PinProjection>)`; `GraphGeometry.of(Map<ProjectedNodeKey, NodeGeometry>, Map<EnclosureHullKey, HullGeometry>)`; the existing private helpers `root(MapReferenceId, String, ProjectedNodeKey, BoundaryTier)`, `positions(...)`, `node(ProjectedNodeKey)`, `nodeKey(MapReferenceId, String)`.
- Produces: the test `MapTierCorrectionShould.separatesThreeHullsAtThePinnedThirtyUnitHalfExtent` covering spec §5.3 and design §8.2.4.

- [ ] **Step 1: Write the failing characterization test**

Add this test and helper to `MapTierCorrectionShould.java`, after `moveOnlyTheFirstMapWhenTheSecondMapHasAnActivePin`:

```java
    @Test
    public void separatesThreeHullsAtThePinnedThirtyUnitHalfExtent() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        ProjectedEnclosure third = root(MAP_THREE, "root-three", NODE_THREE, BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(node(NODE_ONE), node(NODE_TWO), node(NODE_THREE)),
            Arrays.asList(first, second, third), Collections.emptyList(), Collections.emptyList(),
            Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE_ONE, LayoutPoint.of(0.0, 0.0));
        nodes.put(NODE_TWO, LayoutPoint.of(40.0, 0.0));
        nodes.put(NODE_THREE, LayoutPoint.of(-40.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(first.hullKey(), LayoutPoint.of(0.0, 0.0));
        anchors.put(second.hullKey(), LayoutPoint.of(40.0, 0.0));
        anchors.put(third.hullKey(), LayoutPoint.of(-40.0, 0.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(first.hullKey(), square(0.0, 30.0));
        hulls.put(second.hullKey(), square(40.0, 30.0));
        hulls.put(third.hullKey(), square(-40.0, 30.0));

        LayoutPositions corrected = new MapTierCorrection().apply(projection,
            LayoutPositions.of(nodes, anchors),
            GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls)).positions();

        assertThat(corrected.nodes().get(NODE_ONE)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(corrected.nodes().get(NODE_TWO)).isEqualTo(LayoutPoint.of(50.0, 0.0));
        assertThat(corrected.nodes().get(NODE_THREE)).isEqualTo(LayoutPoint.of(-50.0, 0.0));

        ProjectedNodeKey secondAfter = NODE_TWO;
        ProjectedNodeKey firstAfter = NODE_ONE;
        HullGeometry firstHull = square(corrected.nodes().get(firstAfter).x(), 30.0);
        HullGeometry secondHull = square(corrected.nodes().get(secondAfter).x(), 30.0);
        assertThat(HullIntersection.minimumSeparatingTranslation(firstHull, secondHull))
            .isEqualTo(LayoutPoint.of(10.0, 0.0));
        assertThat(HullIntersection.siblingOverlap(firstHull, secondHull)).isTrue();
    }

    private static HullGeometry square(double offset, double halfExtent) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(offset - halfExtent, -halfExtent),
            LayoutPoint.of(offset + halfExtent, -halfExtent),
            LayoutPoint.of(offset + halfExtent, halfExtent),
            LayoutPoint.of(offset - halfExtent, halfExtent)), LayoutPoint.of(offset, 0.0));
    }
```

`NodeGeometry` is already imported by the file; if it is not, add `import org.freeplane.plugin.graph.geometry.NodeGeometry;`. The test asserts the literal correction output `(0,0),(50,0),(-50,0)`, the recomputed A/B translation `(10,0)` and `siblingOverlap == true` (hull overlap 10). It makes no claim about hull overlap after the projection (design N5).

- [ ] **Step 2: Run the test**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.MapTierCorrectionShould" -PTestLoggingFull
```

Expected: PASS on the first run. This is a characterization test of existing behavior: if it fails, the fixture is wrong, not the production code. Re-derive the hull construction from `square(offset, halfExtent)` until the literal `(0,0),(50,0),(-50,0)` output reproduces, and report if it does not.

- [ ] **Step 3: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java
git commit -m "Pin the three-map correction characterization at the thirty-unit hulls [2026-09-12-graph-node-separation]"
```

## Task 5: Build the screen-space placement core and degradation ladder

**Implementer tier:** Advanced
**Lane:** screen-placement
**Depends on:** none (lane is file-disjoint from Tasks 1-4)

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelPlacementRequest.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelFonts.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`

**Interfaces:**
- Consumes: `GraphProjection.nodes()`, `GraphProjection.enclosures()`, `GraphProjection.prominence()`; `GraphGeometry.nodes()` returning `Map<ProjectedNodeKey, NodeGeometry>`, `GraphGeometry.hulls()`; `NodeGeometry.center()` returning `LayoutPoint`, `NodeGeometry.radius()`; `LayoutPositions.nodes()`; `ProjectedNode.key()`, `ProjectedNode.label()`; `SafeNodeLabel.displayText()`; `ProjectedEndpointKey.ofNode(ProjectedNodeKey)`, `ProjectedEndpointKey.isNode()`, `ProjectedEndpointKey.node()`; `RenderingLevel.FULL/DENSE/OVER_TARGET`; `GraphTheme.resolve(DisplaySettings.CanvasTheme)`, `GraphTheme.labelFont()`, `GraphTheme.denseLabelFont()`, `GraphTheme.emphaticLabelFont()`; `ProjectedNodeKey.of(SourceNodeKey)`, `SourceNodeKey.persisted(NodeReference)`, `NodeReference.of(MapReferenceId, PersistedNodeId)`, `PersistedNodeId.of(String)`; `NodeReference.nodeId()`, `PersistedNodeId.value()`; `LayoutPoint.of(double, double)`.
- Produces: `LabelPlacementRequest.of(GraphProjection, GraphGeometry, LayoutPositions, double zoom, double centerX, double centerY, Rectangle2D placementArea, Set<ProjectedEndpointKey> forced, RenderingLevel)` (G1/G2) with accessors `projection()`, `geometry()`, `positions()`, `zoom()`, `centerX()`, `centerY()`, `placementArea()`, `forced()`, `renderingLevel()`, package-private `screenX(double)`, `screenY(double)`, `worldX(double)`, `worldY(double)`; `PlacedLabel` with `Mode`, `Rung`, `of(...)` package-private factory and the accessors of spec §2.5; `LabelFonts.from(GraphTheme)` with `full()`, `dense()`, `emphatic()`; `ScreenLabelPlacement.place(LabelPlacementRequest, List<PlacedLabel>, LabelFonts)` and package-private `place(request, previous, fonts, List<Rectangle2D> seedObstacles)`, plus package-private static `slotAnchor`, `slotMaxWidth`, `screenBounds`, `truncateTo`, `rectangle`; `ScreenLabelPlacementCache.place(LabelPlacementRequest, LabelFonts)`; the new tests of `ScreenLabelPlacementShould`.

- [ ] **Step 1: Write the three value types**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelPlacementRequest.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class LabelPlacementRequest {
    private final GraphProjection projection;
    private final GraphGeometry geometry;
    private final LayoutPositions positions;
    private final double zoom;
    private final double centerX;
    private final double centerY;
    private final Rectangle2D placementArea;
    private final Set<ProjectedEndpointKey> forced;
    private final RenderingLevel renderingLevel;

    private LabelPlacementRequest(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final double zoom, final double centerX, final double centerY,
            final Rectangle2D placementArea, final Set<ProjectedEndpointKey> forced,
            final RenderingLevel renderingLevel) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.positions = Objects.requireNonNull(positions, "positions");
        if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
            throw new IllegalArgumentException("zoom must be finite and positive");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw new IllegalArgumentException("viewport centre must be finite");
        }
        if (placementArea == null || !Double.isFinite(placementArea.getWidth())
                || !Double.isFinite(placementArea.getHeight())
                || !(placementArea.getWidth() > 0.0) || !(placementArea.getHeight() > 0.0)
                || !Double.isFinite(placementArea.getX()) || !Double.isFinite(placementArea.getY())) {
            throw new IllegalArgumentException("placement area must be finite and positive");
        }
        this.zoom = zoom;
        this.centerX = centerX;
        this.centerY = centerY;
        this.placementArea = placementArea;
        final Set<ProjectedEndpointKey> copy = new LinkedHashSet<ProjectedEndpointKey>();
        for (final ProjectedEndpointKey endpoint : Objects.requireNonNull(forced, "forced")) {
            copy.add(Objects.requireNonNull(endpoint, "forced entry"));
        }
        this.forced = Collections.unmodifiableSet(copy);
        this.renderingLevel = Objects.requireNonNull(renderingLevel, "renderingLevel");
    }

    public static LabelPlacementRequest of(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final double zoom, final double centerX, final double centerY,
            final Rectangle2D placementArea, final Set<ProjectedEndpointKey> forced,
            final RenderingLevel renderingLevel) {
        return new LabelPlacementRequest(projection, geometry, positions, zoom, centerX, centerY,
            placementArea, forced, renderingLevel);
    }

    public GraphProjection projection() {
        return projection;
    }

    public GraphGeometry geometry() {
        return geometry;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public double zoom() {
        return zoom;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public Rectangle2D placementArea() {
        return placementArea;
    }

    public Set<ProjectedEndpointKey> forced() {
        return forced;
    }

    public RenderingLevel renderingLevel() {
        return renderingLevel;
    }

    double screenX(final double worldX) {
        return placementArea.getWidth() * 0.5 + zoom * (worldX - centerX);
    }

    double screenY(final double worldY) {
        return placementArea.getHeight() * 0.5 + zoom * (worldY - centerY);
    }

    double worldX(final double screenX) {
        return centerX + (screenX - placementArea.getWidth() * 0.5) / zoom;
    }

    double worldY(final double screenY) {
        return centerY + (screenY - placementArea.getHeight() * 0.5) / zoom;
    }
}
```

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class PlacedLabel {
    public enum Mode {
        INTERIOR, ARC, EXTERNAL, HOVER_ONLY
    }

    public enum Rung {
        FULL_NEAR, FULL_DISPLACED, DENSE_NEAR, DENSE_DISPLACED,
        TRUNCATED_NEAR, TRUNCATED_DISPLACED, TRUNCATED_DENSE_NEAR, TRUNCATED_DENSE_DISPLACED, HOVER_ONLY
    }

    private final ProjectedEndpointKey endpoint;
    private final String text;
    private final Font font;
    private final Mode mode;
    private final Rung rung;
    private final double anchorX;
    private final double anchorY;
    private final double width;
    private final double height;
    private final boolean truncated;
    private final boolean forced;
    private final boolean emphaticAtAnchor;
    private final boolean forcedAtBaseSlot;
    private final boolean fullTextSlotWasFree;
    private final Optional<LayoutPoint> leaderStart;
    private final ScreenLabelPlacement.Slot slot;

    PlacedLabel(final ProjectedEndpointKey endpoint, final String text, final Font font, final Mode mode,
            final Rung rung, final double anchorX, final double anchorY, final double width,
            final double height, final boolean truncated, final boolean forced,
            final boolean emphaticAtAnchor, final boolean forcedAtBaseSlot,
            final boolean fullTextSlotWasFree, final Optional<LayoutPoint> leaderStart,
            final ScreenLabelPlacement.Slot slot) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.text = Objects.requireNonNull(text, "text");
        this.font = Objects.requireNonNull(font, "font");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.rung = Objects.requireNonNull(rung, "rung");
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(width)
                || !Double.isFinite(height) || !(width > 0.0) || !(height > 0.0)) {
            throw new IllegalArgumentException("Placed label geometry must be finite and positive");
        }
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.width = width;
        this.height = height;
        this.truncated = truncated;
        this.forced = forced;
        this.emphaticAtAnchor = emphaticAtAnchor;
        this.forcedAtBaseSlot = forcedAtBaseSlot;
        this.fullTextSlotWasFree = fullTextSlotWasFree;
        this.leaderStart = Objects.requireNonNull(leaderStart, "leaderStart");
        this.slot = slot;
    }

    public ProjectedEndpointKey endpoint() {
        return endpoint;
    }

    public String text() {
        return text;
    }

    public Font font() {
        return font;
    }

    public Mode mode() {
        return mode;
    }

    public Rung rung() {
        return rung;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Rectangle2D bounds() {
        return new Rectangle2D.Double(anchorX - width * 0.5, anchorY - height * 0.5, width, height);
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean forced() {
        return forced;
    }

    public boolean emphaticAtAnchor() {
        return emphaticAtAnchor;
    }

    public boolean forcedAtBaseSlot() {
        return forcedAtBaseSlot;
    }

    public boolean fullTextSlotWasFree() {
        return fullTextSlotWasFree;
    }

    public Optional<LayoutPoint> leaderStart() {
        return leaderStart;
    }

    ScreenLabelPlacement.Slot slot() {
        return slot;
    }
}
```

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelFonts.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.util.Objects;

public final class LabelFonts {
    private final Font full;
    private final Font dense;
    private final Font emphatic;

    private LabelFonts(final Font full, final Font dense, final Font emphatic) {
        this.full = Objects.requireNonNull(full, "full");
        this.dense = Objects.requireNonNull(dense, "dense");
        this.emphatic = Objects.requireNonNull(emphatic, "emphatic");
    }

    public static LabelFonts from(final GraphTheme theme) {
        final GraphTheme value = Objects.requireNonNull(theme, "theme");
        return new LabelFonts(value.labelFont(), value.denseLabelFont(), value.emphaticLabelFont());
    }

    public Font full() {
        return full;
    }

    public Font dense() {
        return dense;
    }

    public Font emphatic() {
        return emphatic;
    }
}
```

- [ ] **Step 2: Write the placement engine**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` with exactly this content. Node placement, the eight-rung ladder, I4 retention, the O4 base-slot rule and the level filter are complete; enclosure labels are added by Task 7.

```java
package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class ScreenLabelPlacement {
    static final double SLOT_GAP = 6.0;
    static final double DISPLACED_OFFSET = 30.0;
    static final double ARC_GAP = 1.0;
    static final double EXTERNAL_GAP = 4.0;
    static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8;
    private static final double MIN_DISC_RADIUS = 2.0;
    private static final double VERTICAL_MAX_WIDTH = 200.0;
    private static final double HORIZONTAL_MAX_WIDTH = 130.0;
    private static final double DIAGONAL_MAX_WIDTH = 150.0;
    private static final String ELLIPSIS = "\u2026";
    static final FontRenderContext SCREEN_FRC = new FontRenderContext(null, true, true);

    enum Slot {
        ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT,
        ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR
    }

    static final Slot[] NEAR_SLOTS = { Slot.ABOVE, Slot.BELOW, Slot.RIGHT, Slot.LEFT,
        Slot.ABOVE_RIGHT, Slot.ABOVE_LEFT, Slot.BELOW_RIGHT, Slot.BELOW_LEFT };
    static final Slot[] FAR_SLOTS = { Slot.ABOVE_FAR, Slot.BELOW_FAR, Slot.RIGHT_FAR, Slot.LEFT_FAR };

    public List<PlacedLabel> place(final LabelPlacementRequest request, final List<PlacedLabel> previous,
            final LabelFonts fonts) {
        return place(request, previous, fonts, Collections.<Rectangle2D>emptyList());
    }

    List<PlacedLabel> place(final LabelPlacementRequest request, final List<PlacedLabel> previous,
            final LabelFonts fonts, final List<Rectangle2D> seedObstacles) {
        final Context context = new Context(request, fonts, seedObstacles);
        final Map<ProjectedNodeKey, PlacedLabel> previousByNode = indexPrevious(previous);
        final List<PlacedLabel> placed = new ArrayList<PlacedLabel>();
        placeForced(context, previousByNode, placed);
        placeNodes(context, previousByNode, placed);
        return filterByLevel(request, placed);
    }

    private static final class Context {
        final LabelPlacementRequest request;
        final LabelFonts fonts;
        final Map<ProjectedNodeKey, ProjectedNode> nodes =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        final List<Rectangle2D> obstacles = new ArrayList<Rectangle2D>();
        final List<ProjectedNodeKey> order = new ArrayList<ProjectedNodeKey>();

        Context(final LabelPlacementRequest request, final LabelFonts fonts,
                final List<Rectangle2D> seedObstacles) {
            this.request = request;
            this.fonts = fonts;
            for (final ProjectedNode node : request.projection().nodes()) {
                nodes.put(node.key(), node);
            }
            for (final ProjectedNodeKey key : request.positions().nodes().keySet()) {
                if (!nodes.containsKey(key)) {
                    continue;
                }
                final NodeGeometry geometry = request.geometry().nodes().get(key);
                if (geometry == null) {
                    continue;
                }
                final double radius = Math.max(MIN_DISC_RADIUS, geometry.radius() * request.zoom());
                radii.put(key, Double.valueOf(radius));
                final double centerX = request.screenX(geometry.center().x());
                final double centerY = request.screenY(geometry.center().y());
                obstacles.add(new Rectangle2D.Double(centerX - radius, centerY - radius,
                    2.0 * radius, 2.0 * radius));
            }
            obstacles.addAll(seedObstacles);
            order.addAll(radii.keySet());
            Collections.sort(order, new Comparator<ProjectedNodeKey>() {
                @Override
                public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                    return Double.compare(radii.get(second).doubleValue(), radii.get(first).doubleValue());
                }
            });
        }

        double radius(final ProjectedNodeKey key) {
            return radii.get(key).doubleValue();
        }

        double screenX(final ProjectedNodeKey key) {
            return request.screenX(request.geometry().nodes().get(key).center().x());
        }

        double screenY(final ProjectedNodeKey key) {
            return request.screenY(request.geometry().nodes().get(key).center().y());
        }
    }

    private static Map<ProjectedNodeKey, PlacedLabel> indexPrevious(final List<PlacedLabel> previous) {
        final Map<ProjectedNodeKey, PlacedLabel> result = new LinkedHashMap<ProjectedNodeKey, PlacedLabel>();
        if (previous == null) {
            return result;
        }
        for (final PlacedLabel label : previous) {
            if (label.endpoint().isNode()) {
                result.put(label.endpoint().node().get(), label);
            }
        }
        return result;
    }

    private static void placeForced(final Context context,
            final Map<ProjectedNodeKey, PlacedLabel> previousByNode, final List<PlacedLabel> placed) {
        for (final ProjectedNodeKey key : context.order) {
            if (!isForced(context, key)) {
                continue;
            }
            placed.add(placeNode(context, key, true, previousByNode.get(key)));
        }
    }

    private static void placeNodes(final Context context,
            final Map<ProjectedNodeKey, PlacedLabel> previousByNode, final List<PlacedLabel> placed) {
        for (final ProjectedNodeKey key : context.order) {
            if (isForced(context, key)) {
                continue;
            }
            placed.add(placeNode(context, key, false, previousByNode.get(key)));
        }
    }

    private static boolean isForced(final Context context, final ProjectedNodeKey key) {
        return context.request.forced().contains(ProjectedEndpointKey.ofNode(key));
    }

    private static PlacedLabel placeNode(final Context context, final ProjectedNodeKey key,
            final boolean forced, final PlacedLabel previous) {
        final ProjectedNode node = context.nodes.get(key);
        final String fullText = node.label().displayText();
        final PlacedLabel retained = retained(context, key, previous);
        if (retained != null) {
            return retained;
        }
        final PlacedLabel laddered = ladder(context, key, ProjectedEndpointKey.ofNode(key), fullText, forced);
        if (laddered != null) {
            return laddered;
        }
        if (forced) {
            return baseSlot(context, key, ProjectedEndpointKey.ofNode(key), fullText, true);
        }
        return hoverOnly(context, key, ProjectedEndpointKey.ofNode(key), fullText);
    }

    private static PlacedLabel retained(final Context context, final ProjectedNodeKey key,
            final PlacedLabel previous) {
        if (previous == null || previous.mode() == PlacedLabel.Mode.HOVER_ONLY) {
            return null;
        }
        final double centerX = context.screenX(key);
        final double centerY = context.screenY(key);
        final double radius = context.radius(key);
        final double[] anchor = slotAnchor(previous.slot(), centerX, centerY, radius,
            previous.width(), previous.height());
        final Rectangle2D candidate = rectangle(anchor[0], anchor[1], previous.width(), previous.height());
        if (!context.request.placementArea().contains(candidate)
                || intersectsAny(context.obstacles, candidate)) {
            return null;
        }
        context.obstacles.add(candidate);
        return new PlacedLabel(previous.endpoint(), previous.text(), previous.font(), previous.mode(),
            previous.rung(), anchor[0], anchor[1], previous.width(), previous.height(),
            previous.truncated(), previous.forced(), previous.emphaticAtAnchor(),
            previous.forcedAtBaseSlot(), false, previous.leaderStart(), previous.slot());
    }

    private static PlacedLabel ladder(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText, final boolean forced) {
        final double centerX = context.screenX(key);
        final double centerY = context.screenY(key);
        final double radius = context.radius(key);
        boolean fullTextSlotWasFree = false;
        for (final LadderRung rung : LADDER) {
            if (forced && rung.rung != PlacedLabel.Rung.FULL_NEAR
                    && rung.rung != PlacedLabel.Rung.FULL_DISPLACED) {
                continue;
            }
            for (final Slot slot : rung.far ? FAR_SLOTS : NEAR_SLOTS) {
                final Font font = rung.font(context.fonts);
                final double limit = slotMaxWidth(slot);
                if (!rung.truncating && textWidth(fullText, font) > limit) {
                    continue;
                }
                final String candidateText = rung.truncating ? truncateTo(fullText, font, limit) : fullText;
                final Rectangle2D size = screenBounds(candidateText, font);
                final double[] anchor = slotAnchor(slot, centerX, centerY, radius,
                    size.getWidth(), size.getHeight());
                final Rectangle2D candidate = rectangle(anchor[0], anchor[1], size.getWidth(),
                    size.getHeight());
                if (!context.request.placementArea().contains(candidate)) {
                    continue;
                }
                final boolean clash = intersectsAny(context.obstacles, candidate);
                if (!rung.truncating && !clash) {
                    fullTextSlotWasFree = true;
                }
                if (clash) {
                    continue;
                }
                context.obstacles.add(candidate);
                return new PlacedLabel(endpoint, candidateText, font, PlacedLabel.Mode.INTERIOR,
                    rung.rung, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
                    rung.truncating && !candidateText.equals(fullText), forced, false, false,
                    fullTextSlotWasFree, Optional.<LayoutPoint>empty(), slot);
            }
        }
        return null;
    }

    private static PlacedLabel baseSlot(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText, final boolean forced) {
        final Font font = context.fonts.full();
        final Rectangle2D size = screenBounds(fullText, font);
        final double[] anchor = slotAnchor(Slot.ABOVE, context.screenX(key), context.screenY(key),
            context.radius(key), size.getWidth(), size.getHeight());
        final Rectangle2D candidate = rectangle(anchor[0], anchor[1], size.getWidth(), size.getHeight());
        context.obstacles.add(candidate);
        return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.INTERIOR,
            PlacedLabel.Rung.FULL_NEAR, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
            false, forced, false, true, false, Optional.<LayoutPoint>empty(), Slot.ABOVE);
    }

    private static PlacedLabel hoverOnly(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText) {
        final Font font = context.fonts.full();
        final Rectangle2D size = screenBounds(fullText, font);
        final double[] anchor = slotAnchor(Slot.ABOVE, context.screenX(key), context.screenY(key),
            context.radius(key), size.getWidth(), size.getHeight());
        return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.HOVER_ONLY,
            PlacedLabel.Rung.HOVER_ONLY, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
            false, false, false, false, false, Optional.<LayoutPoint>empty(), Slot.ABOVE);
    }

    private static List<PlacedLabel> filterByLevel(final LabelPlacementRequest request,
            final List<PlacedLabel> placed) {
        if (request.renderingLevel() != RenderingLevel.OVER_TARGET) {
            return placed;
        }
        final List<PlacedLabel> filtered = new ArrayList<PlacedLabel>();
        for (final PlacedLabel label : placed) {
            if (label.forced()) {
                filtered.add(label);
            }
        }
        return filtered;
    }

    private enum FontSelector {
        FULL, DENSE
    }

    private static final class LadderRung {
        final PlacedLabel.Rung rung;
        final FontSelector fontSelector;
        final boolean truncating;
        final boolean far;

        LadderRung(final PlacedLabel.Rung rung, final FontSelector fontSelector, final boolean truncating,
                final boolean far) {
            this.rung = rung;
            this.fontSelector = fontSelector;
            this.truncating = truncating;
            this.far = far;
        }

        Font font(final LabelFonts fonts) {
            return fontSelector == FontSelector.DENSE ? fonts.dense() : fonts.full();
        }
    }

    private static final LadderRung[] LADDER = {
        new LadderRung(PlacedLabel.Rung.FULL_NEAR, FontSelector.FULL, false, false),
        new LadderRung(PlacedLabel.Rung.FULL_DISPLACED, FontSelector.FULL, false, true),
        new LadderRung(PlacedLabel.Rung.DENSE_NEAR, FontSelector.DENSE, false, false),
        new LadderRung(PlacedLabel.Rung.DENSE_DISPLACED, FontSelector.DENSE, false, true),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_NEAR, FontSelector.FULL, true, false),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DISPLACED, FontSelector.FULL, true, true),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DENSE_NEAR, FontSelector.DENSE, true, false),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DENSE_DISPLACED, FontSelector.DENSE, true, true)
    };

    static double[] slotAnchor(final Slot slot, final double centerX, final double centerY,
            final double radius, final double width, final double height) {
        switch (slot) {
            case ABOVE:
                return new double[] { centerX, centerY - radius - SLOT_GAP - height / 2 };
            case BELOW:
                return new double[] { centerX, centerY + radius + SLOT_GAP + height / 2 };
            case RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2, centerY };
            case LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2, centerY };
            case ABOVE_FAR:
                return new double[] { centerX, centerY - radius - DISPLACED_OFFSET - height / 2 };
            case BELOW_FAR:
                return new double[] { centerX, centerY + radius + DISPLACED_OFFSET + height / 2 };
            case RIGHT_FAR:
                return new double[] { centerX + radius + DISPLACED_OFFSET + width / 2, centerY };
            case LEFT_FAR:
                return new double[] { centerX - radius - DISPLACED_OFFSET - width / 2, centerY };
            case ABOVE_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case ABOVE_LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case BELOW_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
            default:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
        }
    }

    static double slotMaxWidth(final Slot slot) {
        switch (slot) {
            case ABOVE:
            case BELOW:
            case ABOVE_FAR:
            case BELOW_FAR:
                return VERTICAL_MAX_WIDTH;
            case RIGHT:
            case LEFT:
            case RIGHT_FAR:
            case LEFT_FAR:
                return HORIZONTAL_MAX_WIDTH;
            default:
                return DIAGONAL_MAX_WIDTH;
        }
    }

    static Rectangle2D screenBounds(final String text, final Font font) {
        return font.getStringBounds(text, SCREEN_FRC);
    }

    static double textWidth(final String text, final Font font) {
        return screenBounds(text, font).getWidth();
    }

    static String truncateTo(final String text, final Font font, final double limit) {
        if (textWidth(text, font) <= limit) {
            return text;
        }
        for (int cut = text.length() - 1; cut > 2; cut--) {
            final String candidate = stripTrailing(text.substring(0, cut)) + ELLIPSIS;
            if (textWidth(candidate, font) <= limit) {
                return candidate;
            }
        }
        return text.substring(0, Math.min(3, text.length())) + ELLIPSIS;
    }

    private static String stripTrailing(final String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    static Rectangle2D rectangle(final double x, final double y, final double width, final double height) {
        return new Rectangle2D.Double(x - width * 0.5, y - height * 0.5, width, height);
    }

    private static boolean intersectsAny(final List<Rectangle2D> obstacles, final Rectangle2D candidate) {
        for (final Rectangle2D obstacle : obstacles) {
            if (obstacle.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }
}
```

Two details are load-bearing and are already correct in the listing above: `stripTrailing` is hand-written because the Java 8 language level forbids `String.stripTrailing()` (Java 11) even though the build runs on Java 21, and the forced-label guard restricts the ladder to `FULL_NEAR` and `FULL_DISPLACED` only.

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class ScreenLabelPlacementCache {
    private final ScreenLabelPlacement placement = new ScreenLabelPlacement();
    private Key lastKey;
    private List<PlacedLabel> lastPlacements = Collections.emptyList();

    public List<PlacedLabel> place(final LabelPlacementRequest request, final LabelFonts fonts) {
        final Key key = Key.of(request, fonts);
        if (key.equals(lastKey)) {
            return lastPlacements;
        }
        final List<PlacedLabel> next = placement.place(request, lastPlacements, fonts);
        lastKey = key;
        lastPlacements = next;
        return next;
    }

    private static final class Key {
        private final long generation;
        private final LayoutPositions positions;
        private final double zoom;
        private final double centerX;
        private final double centerY;
        private final int width;
        private final int height;
        private final Set<ProjectedEndpointKey> forced;
        private final RenderingLevel level;
        private final Font full;
        private final Font dense;
        private final Font emphatic;

        private Key(final LabelPlacementRequest request, final LabelFonts fonts) {
            this.generation = request.projection().generation();
            this.positions = request.positions();
            this.zoom = request.zoom();
            this.centerX = request.centerX();
            this.centerY = request.centerY();
            this.width = (int) Math.round(request.placementArea().getWidth());
            this.height = (int) Math.round(request.placementArea().getHeight());
            this.forced = request.forced();
            this.level = request.renderingLevel();
            this.full = fonts.full();
            this.dense = fonts.dense();
            this.emphatic = fonts.emphatic();
        }

        static Key of(final LabelPlacementRequest request, final LabelFonts fonts) {
            return new Key(Objects.requireNonNull(request, "request"), Objects.requireNonNull(fonts, "fonts"));
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            final Key that = (Key) other;
            return generation == that.generation
                && positions == that.positions
                && Double.compare(zoom, that.zoom) == 0
                && Double.compare(centerX, that.centerX) == 0
                && Double.compare(centerY, that.centerY) == 0
                && width == that.width
                && height == that.height
                && forced.equals(that.forced)
                && level == that.level
                && full.equals(that.full)
                && dense.equals(that.dense)
                && emphatic.equals(that.emphatic);
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(generation).hashCode();
            result = 31 * result + System.identityHashCode(positions);
            result = 31 * result + Double.valueOf(zoom).hashCode();
            result = 31 * result + Double.valueOf(centerX).hashCode();
            result = 31 * result + Double.valueOf(centerY).hashCode();
            result = 31 * result + width;
            result = 31 * result + height;
            result = 31 * result + forced.hashCode();
            result = 31 * result + level.hashCode();
            result = 31 * result + full.hashCode();
            result = 31 * result + dense.hashCode();
            result = 31 * result + emphatic.hashCode();
            return result;
        }
    }
}
```

The key is `projection generation -> positions identity (==) -> zoom -> viewport centre and area size -> forced set equality -> rendering level -> the three fonts` (spec §2.10). On a miss the retained `lastPlacements` is passed as `previous`, so I4 stickiness survives key changes.

- [ ] **Step 3: Write the failing fixtures for §5.4, §5.5, §5.9 and §5.10**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class ScreenLabelPlacementShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final double ELLIPSIS_TOLERANCE = 1e-4;
    private static final String[] DENSE_NAMES = { "Theorem", "Axiom of Choice",
        "Replacement Scheme", "Extensionality", "Pairing", "Union", "Power Set", "Infinity",
        "Separation", "Foundation / Regularity", "Comprehension", "Well-Ordering" };
    private static final String[] LONG_NAMES = {
        "Well-Ordering Theorem of Choice and Regularity",
        "Axiom Schema of Replacement and Comprehension",
        "Transfinite Induction over Ordinal Numbers",
        "Cardinal Arithmetic under the Continuum Hypothesis",
        "Ultrafilter Lemma and Boolean Prime Ideal Theorem",
        "Kuratowski Zorn Lemma for Partially Ordered Sets" };

    @Test
    public void placesTheDenseSceneAtThePinnedViewport() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, standIn(scene, 1.0), forced("Axiom of Choice"),
            RenderingLevel.FULL, null);

        assertThat(placed).hasSize(12);
        assertRow(placed, area, "Axiom of Choice", "ABOVE", 12, "Axiom of Choice",
            -17.0, -56.172057, 91.104675, 16.344114);
        assertRow(placed, area, "Theorem", "LEFT", 12, "Theorem", -96.530182, -34.0,
            51.060364, 16.344114);
        assertRow(placed, area, "Extensionality", "RIGHT", 12, "Extensionality", 110.216270, -34.0,
            78.432541, 16.344114);
        assertRow(placed, area, "Power Set", "RIGHT_FAR", 12, "Power Set", 89.242210, 0.0,
            56.484421, 16.344114);
        assertRow(placed, area, "Foundation / Regularity", "BELOW", 12,
            "Foundation / Regularity", -17.0, 62.172057, 132.600922, 16.344114);
        assertRow(placed, area, "Replacement Scheme", "ABOVE_RIGHT", 12, "Replacement Scheme",
            91.672424, -56.172057, 121.344849, 16.344114);
        assertRow(placed, area, "Pairing", "LEFT", 12, "Pairing", -84.968140, 0.0,
            39.936279, 16.344114);
        assertRow(placed, area, "Infinity", "BELOW_RIGHT", 12, "Infinity", 84.836136, 22.172057,
            39.672272, 16.344114);
        assertRow(placed, area, "Separation", "LEFT", 12, "Separation", -95.630211, 34.0,
            61.260422, 16.344114);
        assertRow(placed, area, "Comprehension", "BELOW_FAR", 12, "Comprehension", 17.0, 80.172057,
            90.288651, 16.344114);
        assertRow(placed, area, "Well-Ordering", "BELOW_RIGHT", 12, "Well-Ordering", 104.654289,
            56.172057, 79.308578, 16.344114);
        PlacedLabel union = find(placed, "Union");
        assertThat(union.mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(histogram(placed, "full")).isEqualTo(11);
        assertThat(histogram(placed, "dense")).isZero();
        assertThat(histogram(placed, "truncated")).isZero();
        assertThat(histogram(placed, "hover-only")).isEqualTo(1);
        assertThat(labelLabelCollisions(placed)).isZero();
        assertThat(labelDiscCollisions(placed, scene, 1.0)).isZero();
        assertThat(meanLeader(placed, scene, 1.0)).isCloseTo(48.046026, within(1e-4));
        assertThat(maxLeader(placed, scene, 1.0)).isCloseTo(77.894615, within(1e-4));
    }

    @Test
    public void keepsThePinnedDenseHistogramsAtSmallerViewports() {
        List<SceneNode> scene = denseScene();
        Rectangle2D standIn = standIn(scene, 1.0);

        List<PlacedLabel> wide = place(scene, 1.0, area(420.0, 240.0), standIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(wide, "full")).isEqualTo(11);
        assertThat(histogram(wide, "hover-only")).isEqualTo(1);

        List<PlacedLabel> medium = place(scene, 1.0, area(280.0, 170.0), standIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(medium, "full")).isEqualTo(7);
        assertThat(histogram(medium, "dense")).isEqualTo(4);
        assertThat(histogram(medium, "hover-only")).isEqualTo(1);

        List<PlacedLabel> cramped = place(scene, 1.0, area(200.0, 130.0), standIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(cramped, "full")).isEqualTo(2);
        assertThat(histogram(cramped, "dense")).isEqualTo(4);
        assertThat(histogram(cramped, "hover-only")).isEqualTo(6);
    }

    @Test
    public void placesTheLongSceneThroughTheO4BaseSlotWithoutTheStandIn() {
        List<SceneNode> scene = longScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, null, forced(LONG_NAMES[0]),
            RenderingLevel.FULL, null);

        PlacedLabel forced = find(placed, LONG_NAMES[0]);
        assertThat(forced.forcedAtBaseSlot()).isTrue();
        assertRow(placed, area, LONG_NAMES[0], "ABOVE", 12, LONG_NAMES[0], -22.0, -22.172057,
            274.130005, 16.344114);
        assertRow(placed, area, LONG_NAMES[1], "ABOVE_FAR", 12,
            "Axiom Schema of Replacement a\u2026", 0.0, -46.172057, 193.873367, 16.344114);
        assertRow(placed, area, LONG_NAMES[2], "BELOW_FAR", 9, LONG_NAMES[2], 22.0, 44.129043,
            185.041336, 12.258085);
        assertRow(placed, area, LONG_NAMES[3], "LEFT", 12, "Cardinal Arithmetic u\u2026",
            -100.392456, 22.0, 128.784912, 16.344114);
        assertRow(placed, area, LONG_NAMES[4], "BELOW_FAR", 12,
            "Ultrafilter Lemma and Boolean Pr\u2026", 0.0, 68.172057, 198.541412, 16.344114);
        assertRow(placed, area, LONG_NAMES[5], "RIGHT", 12, "Kuratowski Zorn Lem\u2026",
            100.656464, 22.0, 129.312927, 16.344114);
        assertThat(histogram(placed, "full")).isEqualTo(1);
        assertThat(histogram(placed, "dense")).isEqualTo(1);
        assertThat(histogram(placed, "truncated")).isEqualTo(4);
        assertThat(histogram(placed, "hover-only")).isZero();
    }

    @Test
    public void placesTheLongSceneThroughTheO4BaseSlotWithTheStandIn() {
        List<SceneNode> scene = longScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, standIn(scene, 1.0), forced(LONG_NAMES[0]),
            RenderingLevel.FULL, null);

        assertThat(find(placed, LONG_NAMES[0]).forcedAtBaseSlot()).isTrue();
        assertRow(placed, area, LONG_NAMES[0], "ABOVE", 12, LONG_NAMES[0], -22.0, -22.172057,
            274.130005, 16.344114);
        assertRow(placed, area, LONG_NAMES[1], "BELOW_FAR", 12,
            "Axiom Schema of Replacement a\u2026", 0.0, 46.172057, 193.873367, 16.344114);
        assertRow(placed, area, LONG_NAMES[2], "RIGHT", 12, "Transfinite Induction\u2026",
            99.558441, 0.0, 127.116882, 16.344114);
        assertRow(placed, area, LONG_NAMES[3], "LEFT", 12, "Cardinal Arithmetic u\u2026",
            -100.392456, 22.0, 128.784912, 16.344114);
        assertRow(placed, area, LONG_NAMES[4], "BELOW_FAR", 12,
            "Ultrafilter Lemma and Boolean Pr\u2026", 0.0, 68.172057, 198.541412, 16.344114);
        assertRow(placed, area, LONG_NAMES[5], "RIGHT", 12, "Kuratowski Zorn Lem\u2026",
            100.656464, 22.0, 129.312927, 16.344114);
        assertThat(histogram(placed, "full")).isEqualTo(1);
        assertThat(histogram(placed, "dense")).isZero();
        assertThat(histogram(placed, "truncated")).isEqualTo(5);
        assertThat(histogram(placed, "hover-only")).isZero();
    }

    @Test
    public void keepsThePinnedLongHistogramsAtTheSmallerViewports() {
        List<SceneNode> scene = longScene();

        List<PlacedLabel> wide = place(scene, 1.0, area(500.0, 300.0), null,
            forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(wide, "full")).isEqualTo(1);
        assertThat(histogram(wide, "dense")).isEqualTo(1);
        assertThat(histogram(wide, "truncated")).isEqualTo(4);
        assertThat(histogram(wide, "hover-only")).isZero();
        assertThat(maxLeader(wide, scene, 1.0)).isCloseTo(78.6565, within(1e-3));

        List<PlacedLabel> cramped = place(scene, 1.0, area(200.0, 130.0), null,
            forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(cramped, "full")).isEqualTo(1);
        assertThat(histogram(cramped, "truncated")).isEqualTo(2);
        assertThat(histogram(cramped, "hover-only")).isEqualTo(3);

        List<PlacedLabel> crampedWithStandIn = place(scene, 1.0, area(200.0, 130.0),
            standIn(scene, 1.0), forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(crampedWithStandIn, "full")).isEqualTo(1);
        assertThat(histogram(crampedWithStandIn, "truncated")).isEqualTo(1);
        assertThat(histogram(crampedWithStandIn, "hover-only")).isEqualTo(4);
    }

    @Test
    public void followsThePinnedZoomAndRenderingLevelMatrix() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);

        assertZoomCell(scene, area, 0.25, 8, 7, 1, "ABOVE", -4.25, -24.672057,
            Arrays.asList("Pairing", "Union", "Infinity", "Well-Ordering"));
        assertZoomCell(scene, area, 0.5, 10, 10, 0, "ABOVE", -8.5, -35.172057,
            Arrays.asList("Union", "Well-Ordering"));
        assertZoomCell(scene, area, 1.0, 11, 11, 0, "ABOVE", -17.0, -56.172057,
            Collections.singletonList("Union"));
        assertZoomCell(scene, area, 2.0, 11, 11, 0, "ABOVE_FAR", -34.0, -122.172057,
            Collections.singletonList("Replacement Scheme"));

        for (double zoom : new double[] { 0.25, 0.5, 1.0, 2.0 }) {
            List<PlacedLabel> overTarget = place(scene, zoom, area, null, forced("Axiom of Choice"),
                RenderingLevel.OVER_TARGET, null);
            assertThat(overTarget).hasSize(1);
            assertThat(overTarget.get(0).endpoint())
                .isEqualTo(ProjectedEndpointKey.ofNode(key("Axiom of Choice")));
            assertThat(overTarget.get(0).forced()).isTrue();
        }
    }

    @Test
    public void neverTruncatesWhileAFullTextSlotWasFree() {
        List<SceneNode> scene = longScene();
        for (double width : new double[] { 1128.0, 500.0 }) {
            List<PlacedLabel> withoutStandIn = place(scene, 1.0, area(width, 364.0), null,
                forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
            for (PlacedLabel label : withoutStandIn) {
                if (label.truncated()) {
                    assertThat(label.fullTextSlotWasFree()).as(label.text()).isFalse();
                }
            }
            List<PlacedLabel> withStandIn = place(scene, 1.0, area(width, 364.0), standIn(scene, 1.0),
                forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
            for (PlacedLabel label : withStandIn) {
                if (label.truncated()) {
                    assertThat(label.fullTextSlotWasFree()).as(label.text()).isFalse();
                }
            }
        }
    }

    private static void assertZoomCell(List<SceneNode> scene, Rectangle2D area, double zoom,
            int placedCount, int fullCount, int denseCount, String forcedSlot, double forcedX,
            double forcedY, List<String> hidden) {
        List<PlacedLabel> placed = place(scene, zoom, area, null, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);

        assertThat(countVisible(placed)).as("zoom " + zoom + " placed").isEqualTo(placedCount);
        assertThat(histogram(placed, "full")).as("zoom " + zoom + " full").isEqualTo(fullCount);
        assertThat(histogram(placed, "dense")).as("zoom " + zoom + " dense").isEqualTo(denseCount);
        for (String name : hidden) {
            assertThat(find(placed, name).mode()).as(name).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        }
        PlacedLabel forced = find(placed, "Axiom of Choice");
        assertThat(forced.mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(slotOf(forced, scene, zoom)).isEqualTo(forcedSlot);
        assertThat(forced.anchorX() - area.getWidth() * 0.5).isCloseTo(forcedX, within(ELLIPSIS_TOLERANCE));
        assertThat(forced.anchorY() - area.getHeight() * 0.5).isCloseTo(forcedY, within(ELLIPSIS_TOLERANCE));
    }

    static List<SceneNode> denseScene() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        for (int index = 0; index < DENSE_NAMES.length; index++) {
            scene.add(new SceneNode(DENSE_NAMES[index], index % 3 == 0 ? 14.0 : 8.0, index == 1,
                (index % 4 - 1.5) * 34.0, (index / 4 - 1) * 34.0));
        }
        return scene;
    }

    static List<SceneNode> longScene() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        for (int index = 0; index < LONG_NAMES.length; index++) {
            scene.add(new SceneNode(LONG_NAMES[index], 8.0, index == 0,
                (index % 3 - 1) * 22.0, (index / 3) * 22.0));
        }
        return scene;
    }

    static List<PlacedLabel> place(List<SceneNode> scene, double zoom, Rectangle2D area,
            Rectangle2D standIn, Set<ProjectedEndpointKey> forced, RenderingLevel level,
            List<PlacedLabel> previous) {
        List<Rectangle2D> obstacles = standIn == null
            ? Collections.<Rectangle2D>emptyList() : Collections.singletonList(standIn);
        return new ScreenLabelPlacement().place(request(scene, zoom, area, forced, level),
            previous, LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)), obstacles);
    }

    static LabelPlacementRequest request(List<SceneNode> scene, double zoom, Rectangle2D area,
            Set<ProjectedEndpointKey> forced, RenderingLevel level) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (SceneNode node : scene) {
            ProjectedNodeKey nodeKey = key(node.name);
            nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(node.name, node.name), "Map", false));
            geometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(node.x, node.y), node.radius));
            positions.put(nodeKey, LayoutPoint.of(node.x, node.y));
        }
        GraphProjection projection = GraphProjection.structure(1L, nodes,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        GraphGeometry graphGeometry = GraphGeometry.of(geometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        LayoutPositions layoutPositions = LayoutPositions.of(positions,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        return LabelPlacementRequest.of(projection, graphGeometry, layoutPositions, zoom, 0.0, 0.0,
            area, forced, level);
    }

    static Rectangle2D area(double width, double height) {
        return new Rectangle2D.Double(0.0, 0.0, width, height);
    }

    static Rectangle2D standIn(List<SceneNode> scene, double zoom) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (SceneNode node : scene) {
            minX = Math.min(minX, node.x * zoom - node.radius * zoom);
            minY = Math.min(minY, node.y * zoom - node.radius * zoom);
        }
        double width = ScreenLabelPlacement.textWidth("Basic Definitions and Theorems",
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)).emphatic()
                .deriveFont(java.awt.Font.PLAIN, 10.0f));
        double height = ScreenLabelPlacement.screenBounds("Basic Definitions and Theorems",
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)).emphatic()
                .deriveFont(java.awt.Font.PLAIN, 10.0f)).getHeight();
        return new Rectangle2D.Double(minX - 11.0 + 1128.0 * 0.5, minY - 18.0 - height + 364.0 * 0.5,
            width, height);
    }

    static Set<ProjectedEndpointKey> forced(String... names) {
        Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        for (String name : names) {
            forced.add(ProjectedEndpointKey.ofNode(key(name)));
        }
        return forced;
    }

    static PlacedLabel find(List<PlacedLabel> placed, String name) {
        for (PlacedLabel label : placed) {
            if (label.endpoint().isNode() && name.equals(nodeName(label.endpoint()))) {
                return label;
            }
        }
        return null;
    }

    static String nodeName(ProjectedEndpointKey endpoint) {
        return endpoint.node().get().source().persistedReference().get().nodeId().value();
    }

    static void assertRow(List<PlacedLabel> placed, Rectangle2D area, String name, String slotName,
            int fontSize, String text, double fixtureX, double fixtureY, double fixtureWidth,
            double fixtureHeight) {
        PlacedLabel label = find(placed, name);
        assertThat(label).as("label " + name).isNotNull();
        assertThat(label.font().getSize()).as(name + " font").isEqualTo(fontSize);
        assertThat(label.text()).as(name + " text").isEqualTo(text);
        assertThat(label.anchorX() - area.getWidth() * 0.5).as(name + " x")
            .isCloseTo(fixtureX, within(ELLIPSIS_TOLERANCE));
        assertThat(label.anchorY() - area.getHeight() * 0.5).as(name + " y")
            .isCloseTo(fixtureY, within(ELLIPSIS_TOLERANCE));
        assertThat(label.width()).as(name + " w").isCloseTo(fixtureWidth, within(ELLIPSIS_TOLERANCE));
        assertThat(label.height()).as(name + " h").isCloseTo(fixtureHeight, within(ELLIPSIS_TOLERANCE));
    }

    static String slotOf(PlacedLabel label, List<SceneNode> scene, double zoom) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + 564.0;
        double centerY = node.y * zoom + 182.0;
        double radius = Math.max(2.0, node.radius * zoom);
        for (ScreenLabelPlacement.Slot slot : ScreenLabelPlacement.Slot.values()) {
            double[] anchor = ScreenLabelPlacement.slotAnchor(slot, centerX, centerY, radius,
                label.width(), label.height());
            if (Math.abs(anchor[0] - label.anchorX()) <= 1e-6
                    && Math.abs(anchor[1] - label.anchorY()) <= 1e-6) {
                return slot.name();
            }
        }
        return null;
    }

    private static SceneNode sceneNode(List<SceneNode> scene, String name) {
        for (SceneNode node : scene) {
            if (node.name.equals(name)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Unknown scene node " + name);
    }

    static int countVisible(List<PlacedLabel> placed) {
        int count = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                count++;
            }
        }
        return count;
    }

    static int histogram(List<PlacedLabel> placed, String kind) {
        int count = 0;
        for (PlacedLabel label : placed) {
            if ("hover-only".equals(kind)) {
                if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    count++;
                }
            }
            else if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                if (label.truncated()) {
                    if ("truncated".equals(kind)) {
                        count++;
                    }
                }
                else if (label.font().getSize() == 12 && "full".equals(kind)) {
                    count++;
                }
                else if (label.font().getSize() == 9 && "dense".equals(kind)) {
                    count++;
                }
            }
        }
        return count;
    }

    static int labelLabelCollisions(List<PlacedLabel> placed) {
        int collisions = 0;
        for (int first = 0; first < placed.size(); first++) {
            for (int second = first + 1; second < placed.size(); second++) {
                if (placed.get(first).mode() == PlacedLabel.Mode.HOVER_ONLY
                        || placed.get(second).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    continue;
                }
                if (placed.get(first).bounds().intersects(placed.get(second).bounds())) {
                    collisions++;
                }
            }
        }
        return collisions;
    }

    static int labelDiscCollisions(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        int collisions = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (SceneNode node : scene) {
                double radius = Math.max(2.0, node.radius * zoom);
                Rectangle2D disc = new Rectangle2D.Double(node.x * zoom - radius + 564.0,
                    node.y * zoom - radius + 182.0, 2.0 * radius, 2.0 * radius);
                if (disc.intersects(label.bounds())) {
                    collisions++;
                    break;
                }
            }
        }
        return collisions;
    }

    static double leader(PlacedLabel label, List<SceneNode> scene, double zoom) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + 564.0;
        double centerY = node.y * zoom + 182.0;
        return Math.hypot(label.anchorX() - centerX, label.anchorY() - centerY);
    }

    static double meanLeader(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        double sum = 0.0;
        int count = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            sum += leader(label, scene, zoom);
            count++;
        }
        return sum / count;
    }

    static double maxLeader(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        double max = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            max = Math.max(max, leader(label, scene, zoom));
        }
        return max;
    }

    static ProjectedNodeKey key(String name) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(name))));
    }

    static final class SceneNode {
        final String name;
        final double radius;
        final boolean selected;
        final double x;
        final double y;

        SceneNode(String name, double radius, boolean selected, double x, double y) {
            this.name = name;
            this.radius = radius;
            this.selected = selected;
            this.x = x;
            this.y = y;
        }
    }
}
```

The helpers are fixed to the 1128x364 area in `slotOf`, `standIn`, `labelDiscCollisions` and `leader` (the `+564.0/+182.0` offsets), so later tasks must use the same viewport for those helpers. The `standIn` helper recomputes `148.289871 x 13.619987` from the 10 pt face; the translation `(+564,+182)` converts the world-centred rectangle of §5.4 to the absolute screen area `(0,0,1128,364)`.

- [ ] **Step 4: Run the fixtures and confirm they fail first**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould" -PTestLoggingFull
```

Expected: the tests compile and then FAIL on assertions while the class under test is still being written; if they pass on the first run, check that `place` is actually being called (a null `placed` list would throw, not pass). Do not adjust the pinned numbers.

- [ ] **Step 5: Run the fixtures and confirm they pass**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould" -PTestLoggingFull
```

Expected: PASS with 7 tests, 0 failures. If a pinned anchor, size, histogram or leader differs, compare it against a fresh run of the committed oracle:

```bash
java -Djava.awt.headless=true docs/superpowers/specs/mockups/2026-09-12-node-separation/FixtureProbe.java
```

The oracle must print `checks=689 failures=0` and exit 0; if the oracle and the implementation disagree, the implementation is wrong unless the oracle itself fails, in which case stop and report. Do not modify the oracle.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelPlacementRequest.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelFonts.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java
git commit -m "Add the screen-space placement core and its pinned fixtures [2026-09-12-graph-node-separation]"
```

## Task 6: Prove retention, invalidation and painted-ink separation

**Implementer tier:** Advanced
**Lane:** screen-placement
**Depends on:** Task 5

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
- Modify (only if the new fixtures falsify the implementation): `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` and `.../ScreenLabelPlacementCache.java`

**Interfaces:**
- Consumes: `ScreenLabelPlacement.place(LabelPlacementRequest, List<PlacedLabel>, LabelFonts)`, package-private `place(request, previous, fonts, List<Rectangle2D>)`; `ScreenLabelPlacementCache.place(LabelPlacementRequest, LabelFonts)`; `LabelPlacementRequest.of(GraphProjection, GraphGeometry, LayoutPositions, double, double, double, Rectangle2D, Set<ProjectedEndpointKey>, RenderingLevel)`; `PlacedLabel.rung()`, `slot()`, `bounds()`, `truncated()`, `mode()`; test helpers from Task 5: `denseScene()`, `longScene()`, `place(scene, zoom, area, standIn, forced, level, previous)`, `request(scene, zoom, area, forced, level)`, `area(w, h)`, `standIn(scene, zoom)`, `forced(names...)`, `find(placed, name)`, `nodeName(endpoint)`, `countVisible(placed)`, `histogram(placed, kind)`, `labelLabelCollisions(placed)`, `labelDiscCollisions(placed, scene, zoom)`, `leader(label, scene, zoom)`, `maxLeader(placed, scene, zoom)`, `SceneNode(name, radius, selected, x, y)`, `key(name)`.
- Produces: the tests `keepsPlacementsStickyAcrossAPan`, `retainsASlotForAOnePixelMove`, `invalidatesAStaleSlotAndReLadders`, `keepsPaintedInkSeparatedAcrossTheZoomMatrix`, `boundsEveryPlacedLabelByItsSlotForms`, `promotesAHoverOnlyLabelToForcedWithoutCollisions`, `reusesTheCachedPlacementUntilTheKeyChanges`; test helpers `placeShifted(...)`, `assertSamePlacement(...)`, `renderInk(...)`, `inkMask(...)`, `discMask(...)`, `overhang(...)`, `minRectGap(...)`, `leaderForm(slot, r, w, h)`, `supportForm(slot, r, w, h)`, `support(label, scene, zoom)`, `maxSupport(placed, scene, zoom)`, `slotOfAt(label, cx, cy, r)`.

- [ ] **Step 1: Generalize the request helper for the viewport centre**

In `ScreenLabelPlacementShould.java`, replace the existing `request(List<SceneNode>, double, Rectangle2D, Set, RenderingLevel)` method with these two methods:

```java
    static LabelPlacementRequest request(List<SceneNode> scene, double zoom, Rectangle2D area,
            Set<ProjectedEndpointKey> forced, RenderingLevel level) {
        return request(scene, zoom, area, forced, level, 0.0, 0.0);
    }

    static LabelPlacementRequest request(List<SceneNode> scene, double zoom, Rectangle2D area,
            Set<ProjectedEndpointKey> forced, RenderingLevel level, double centerX, double centerY) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (SceneNode node : scene) {
            ProjectedNodeKey nodeKey = key(node.name);
            nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(node.name, node.name), "Map", false));
            geometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(node.x, node.y), node.radius));
            positions.put(nodeKey, LayoutPoint.of(node.x, node.y));
        }
        GraphProjection projection = GraphProjection.structure(1L, nodes,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        GraphGeometry graphGeometry = GraphGeometry.of(geometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        LayoutPositions layoutPositions = LayoutPositions.of(positions,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        return LabelPlacementRequest.of(projection, graphGeometry, layoutPositions, zoom, centerX, centerY,
            area, forced, level);
    }

    static List<PlacedLabel> placeShifted(List<SceneNode> scene, double zoom, Rectangle2D area,
            Rectangle2D standIn, Set<ProjectedEndpointKey> forced, RenderingLevel level,
            List<PlacedLabel> previous, double centerX, double centerY) {
        List<Rectangle2D> obstacles = standIn == null
            ? Collections.<Rectangle2D>emptyList() : Collections.singletonList(standIn);
        return new ScreenLabelPlacement().place(
            request(scene, zoom, area, forced, level, centerX, centerY), previous,
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)), obstacles);
    }
```

- [ ] **Step 2: Add the I4 tests**

Add these three tests and the shared helpers:

```java
    @Test
    public void keepsPlacementsStickyAcrossAPan() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        Rectangle2D standIn = standIn(scene, 1.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);
        Rectangle2D shiftedStandIn = new Rectangle2D.Double(standIn.getX() - 1.0, standIn.getY(),
            standIn.getWidth(), standIn.getHeight());

        List<PlacedLabel> panned = placeShifted(scene, 1.0, area, shiftedStandIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline, 1.0, 0.0);

        assertThat(countVisible(panned)).isEqualTo(11);
        for (PlacedLabel label : baseline) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            PlacedLabel after = find(panned, nodeName(label.endpoint()));
            assertThat(after).as(nodeName(label.endpoint())).isNotNull();
            assertThat(after.rung()).isEqualTo(label.rung());
            assertThat(after.text()).isEqualTo(label.text());
            assertThat(after.font()).isEqualTo(label.font());
        }
        List<PlacedLabel> reapplied = placeShifted(scene, 1.0, area, shiftedStandIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, panned, 1.0, 0.0);
        assertSamePlacement(panned, reapplied);
    }

    @Test
    public void retainsASlotForAOnePixelMove() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(200.0, 130.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);

        assertRow(placedIn(baseline, area, 200.0, 130.0), area,
            "Axiom of Choice", "ABOVE", 12, "Axiom of Choice", -17.0, -56.172057,
            91.104675, 16.344114);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Theorem", "BELOW_FAR", 9,
            "Theorem", -51.0, 16.129043, 38.295258, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Pairing", "LEFT", 9,
            "Pairing", -79.976112, 0.0, 29.952225, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Infinity", "RIGHT", 9,
            "Infinity", 79.877113, 0.0, 29.754227, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Separation", "BELOW", 12,
            "Separation", -51.0, 56.172057, 61.260422, 16.344114);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Comprehension", "BELOW", 9,
            "Comprehension", 17.0, 54.129043, 67.716476, 12.258085);

        List<SceneNode> movedScene = moved(scene, "Infinity", 51.0, -1.0);
        List<PlacedLabel> again = place(movedScene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline);

        PlacedLabel infinity = find(again, "Infinity");
        assertThat(slotOfAt(infinity, 51.0 + 100.0, -1.0 + 65.0, 8.0)).isEqualTo("RIGHT");
        assertThat(infinity.font().getSize()).isEqualTo(9);
        assertThat(infinity.anchorX() - 100.0).isCloseTo(79.877113, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.anchorY() - 65.0).isCloseTo(-1.0, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.width()).isCloseTo(29.754227, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.height()).isCloseTo(12.258085, within(ELLIPSIS_TOLERANCE));
        for (PlacedLabel label : baseline) {
            if ("Infinity".equals(nodeName(label.endpoint()))
                    || label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            PlacedLabel after = find(again, nodeName(label.endpoint()));
            assertThat(after.anchorX()).isEqualTo(label.anchorX());
            assertThat(after.anchorY()).isEqualTo(label.anchorY());
            assertThat(after.text()).isEqualTo(label.text());
            assertThat(after.font()).isEqualTo(label.font());
        }
        assertThat(labelLabelCollisions(again)).isZero();
        assertThat(discCollisionsIn(again, movedScene, 1.0, area)).isZero();
        assertSamePlacement(again, place(movedScene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, again));
    }

    @Test
    public void invalidatesAStaleSlotAndReLadders() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        scene.add(new SceneNode("Alpha", 8.0, true, 0.0, 0.0));
        scene.add(new SceneNode("Beta", 8.0, false, -40.0, 0.0));
        Rectangle2D area = area(400.0, 300.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, null);

        assertRow(baseline, area, "Alpha", "ABOVE", 12, "Alpha", 0.0, -22.172057,
            32.292221, 16.344114);
        assertRow(baseline, area, "Beta", "ABOVE", 12, "Beta", -40.0, -22.172057,
            25.632172, 16.344114);

        List<SceneNode> movedScene = moved(scene, "Beta", 0.0, -35.0);
        List<PlacedLabel> again = place(movedScene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, baseline);

        assertThat(slotOfAt(find(again, "Alpha"), 200.0, 150.0, 8.0)).isEqualTo("BELOW");
        assertThat(find(again, "Alpha").anchorY() - 150.0).isCloseTo(22.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(again, "Alpha").width()).isCloseTo(32.292221, within(ELLIPSIS_TOLERANCE));
        assertThat(slotOfAt(find(again, "Beta"), 200.0, 115.0, 8.0)).isEqualTo("ABOVE");
        assertThat(find(again, "Beta").anchorY() - 150.0).isCloseTo(-57.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(again, "Beta").width()).isCloseTo(25.632172, within(ELLIPSIS_TOLERANCE));
        assertThat(labelLabelCollisions(again)).isZero();
        assertThat(discCollisionsIn(again, movedScene, 1.0, area)).isZero();
        assertSamePlacement(again, place(movedScene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, again));
    }
```

Add these helpers:

```java
    static List<SceneNode> moved(List<SceneNode> scene, String name, double x, double y) {
        List<SceneNode> moved = new ArrayList<SceneNode>();
        for (SceneNode node : scene) {
            moved.add(name.equals(node.name) ? new SceneNode(node.name, node.radius, node.selected, x, y) : node);
        }
        return moved;
    }

    static Rectangle2D standInIn(List<SceneNode> scene, double zoom, Rectangle2D area) {
        Rectangle2D worldCentred = standIn(scene, zoom);
        return new Rectangle2D.Double(worldCentred.getX() - 564.0 + area.getWidth() * 0.5,
            worldCentred.getY() - 182.0 + area.getHeight() * 0.5,
            worldCentred.getWidth(), worldCentred.getHeight());
    }

    static int discCollisionsIn(List<PlacedLabel> placed, List<SceneNode> scene, double zoom,
            Rectangle2D area) {
        int collisions = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (SceneNode node : scene) {
                double radius = Math.max(2.0, node.radius * zoom);
                Rectangle2D disc = new Rectangle2D.Double(node.x * zoom - radius + area.getWidth() * 0.5,
                    node.y * zoom - radius + area.getHeight() * 0.5, 2.0 * radius, 2.0 * radius);
                if (disc.intersects(label.bounds())) {
                    collisions++;
                    break;
                }
            }
        }
        return collisions;
    }

    static String slotOfAt(PlacedLabel label, double centerX, double centerY, double radius) {
        for (ScreenLabelPlacement.Slot slot : ScreenLabelPlacement.Slot.values()) {
            double[] anchor = ScreenLabelPlacement.slotAnchor(slot, centerX, centerY, radius,
                label.width(), label.height());
            if (Math.abs(anchor[0] - label.anchorX()) <= 1e-6
                    && Math.abs(anchor[1] - label.anchorY()) <= 1e-6) {
                return slot.name();
            }
        }
        return null;
    }

    static void assertSamePlacement(List<PlacedLabel> expected, List<PlacedLabel> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (PlacedLabel label : expected) {
            PlacedLabel other = find(actual, nodeName(label.endpoint()));
            assertThat(other).isNotNull();
            assertThat(other.rung()).isEqualTo(label.rung());
            assertThat(other.text()).isEqualTo(label.text());
            assertThat(other.font()).isEqualTo(label.font());
            assertThat(other.anchorX()).isEqualTo(label.anchorX());
            assertThat(other.anchorY()).isEqualTo(label.anchorY());
            assertThat(other.width()).isEqualTo(label.width());
            assertThat(other.height()).isEqualTo(label.height());
        }
    }

    static List<PlacedLabel> placedIn(List<PlacedLabel> placed, Rectangle2D area, double width,
            double height) {
        return placed;
    }
```

`assertRow` subtracts the area's half-size, so `placedIn` only exists to make the 200x130 fixture's area explicit; it is the identity. The pan test shifts the stand-in by `(-1, 0)` because a `+1 px` viewport-origin change moves world content left by one pixel. Pan and invalidation use `place` with `previous` (the cache is not needed to retain).

- [ ] **Step 3: Add the painted-ink matrix of §5.7**

Add this test and helpers:

```java
    @Test
    public void keepsPaintedInkSeparatedAcrossTheZoomMatrix() {
        assertInk("dense z=0.25", denseScene(), 0.25, standIn(denseScene(), 0.25), 6,
            "Axiom of Choice", "BELOW_FAR", -4.25, 31.672057, false, 0.4958, 2.155886);
        assertInk("dense z=1.00", denseScene(), 1.0, standIn(denseScene(), 1.0), 11,
            "Axiom of Choice", "ABOVE", -17.0, -56.172057, false, 0.5156, 1.655886);
        assertInk("dense z=2.00", denseScene(), 2.0, standIn(denseScene(), 2.0), 11,
            "Axiom of Choice", "ABOVE", -34.0, -98.172057, true, 0.7578, 19.921654);
        assertInk("long z=1 none", longScene(), 1.0, null, 6,
            "Well-Ordering Theorem of Choice and Regularity", "ABOVE", -22.0, -22.172057, true,
            0.4793, 7.655886);
        assertInk("long z=1 stand-in", longScene(), 1.0, standIn(longScene(), 1.0), 6,
            "Well-Ordering Theorem of Choice and Regularity", "ABOVE", -22.0, -22.172057, true,
            0.2151, 5.655886);
    }

    private static void assertInk(String tag, List<SceneNode> scene, double zoom, Rectangle2D standIn,
            int expectedPlaced, String forcedName, String forcedSlot, double forcedX, double forcedY,
            boolean forcedBaseSlot, double expectedOverhang, double expectedMinGap) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        assertThat(countVisible(placed)).as(tag + " placed").isEqualTo(expectedPlaced);
        PlacedLabel forcedLabel = find(placed, forcedName);
        assertThat(slotOf(forcedLabel, scene, zoom)).as(tag + " forced slot").isEqualTo(forcedSlot);
        assertThat(forcedLabel.anchorX() - area.getWidth() * 0.5).as(tag + " forced x")
            .isCloseTo(forcedX, within(1e-4));
        assertThat(forcedLabel.anchorY() - area.getHeight() * 0.5).as(tag + " forced y")
            .isCloseTo(forcedY, within(1e-4));
        assertThat(forcedLabel.forcedAtBaseSlot()).as(tag + " forced base").isEqualTo(forcedBaseSlot);

        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        List<boolean[]> masks = new ArrayList<boolean[]>();
        double maxOverhang = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            boolean[] mask = inkMask(label, width, height);
            masks.add(mask);
            double[] overhang = overhang(label, mask, width, height);
            maxOverhang = Math.max(maxOverhang,
                Math.max(Math.max(overhang[0], overhang[1]), Math.max(overhang[2], overhang[3])));
        }
        assertThat(maxOverhang).as(tag + " max overhang").isCloseTo(expectedOverhang, within(1e-4));
        assertThat(minRectGap(placed)).as(tag + " min gap").isCloseTo(expectedMinGap, within(1e-6));

        for (int first = 0; first < masks.size(); first++) {
            for (int second = first + 1; second < masks.size(); second++) {
                assertThat(overlaps(masks.get(first), masks.get(second)))
                    .as(tag + " ink pair " + first + "/" + second).isFalse();
            }
        }
        List<boolean[]> discs = new ArrayList<boolean[]>();
        for (SceneNode node : scene) {
            discs.add(discMask(node.x * zoom + width * 0.5, node.y * zoom + height * 0.5,
                Math.max(2.0, node.radius * zoom), width, height));
        }
        for (boolean[] mask : masks) {
            for (boolean[] disc : discs) {
                assertThat(overlaps(mask, disc)).as(tag + " ink/disc").isFalse();
            }
        }
    }

    static boolean[] inkMask(PlacedLabel label, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(label.font());
            graphics.setColor(java.awt.Color.BLACK);
            Rectangle2D bounds = label.font().getStringBounds(label.text(), ScreenLabelPlacement.SCREEN_FRC);
            java.awt.FontMetrics metrics = graphics.getFontMetrics(label.font());
            double baseline = label.anchorY() + (metrics.getAscent() - metrics.getDescent()) / 2.0;
            graphics.drawString(label.text(), (float) (label.anchorX() - bounds.getWidth() / 2.0),
                (float) baseline);
        }
        finally {
            graphics.dispose();
        }
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    mask[y * width + x] = true;
                }
            }
        }
        return mask;
    }

    static boolean[] discMask(double centerX, double centerY, double radius, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.fill(new java.awt.geom.Ellipse2D.Double(centerX - radius, centerY - radius,
                2.0 * radius, 2.0 * radius));
        }
        finally {
            graphics.dispose();
        }
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    mask[y * width + x] = true;
                }
            }
        }
        return mask;
    }

    static boolean overlaps(boolean[] first, boolean[] second) {
        for (int index = 0; index < first.length; index++) {
            if (first[index] && second[index]) {
                return true;
            }
        }
        return false;
    }

    static double[] overhang(PlacedLabel label, boolean[] mask, int width, int height) {
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int minY = Integer.MAX_VALUE;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        Rectangle2D bounds = label.bounds();
        return new double[] { bounds.getMinX() - minX, maxX + 1 - bounds.getMaxX(),
            bounds.getMinY() - minY, maxY + 1 - bounds.getMaxY() };
    }

    static double minRectGap(List<PlacedLabel> placed) {
        double best = Double.MAX_VALUE;
        for (int first = 0; first < placed.size(); first++) {
            if (placed.get(first).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (int second = first + 1; second < placed.size(); second++) {
                if (placed.get(second).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    continue;
                }
                Rectangle2D a = placed.get(first).bounds();
                Rectangle2D b = placed.get(second).bounds();
                double dx = Math.max(0.0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
                double dy = Math.max(0.0, Math.max(a.getMinY() - b.getMaxY(), b.getMinY() - a.getMaxY()));
                best = Math.min(best, Math.hypot(dx, dy));
            }
        }
        return best;
    }
```

The expected values are the committed oracle's §5.7 rows: `placed`, forced slot/anchor/base flag, maximum per-side overhang and minimum rectangle gap as listed in the `assertInk` calls. The dense z=2 fixture is the one case whose base-slot rectangle intersects a disc; the ink assertions still hold for it and are not weakened.

- [ ] **Step 4: Add the per-slot bound fixtures of §5.10**

Add this test and helpers:

```java
    @Test
    public void boundsEveryPlacedLabelByItsSlotForms() {
        assertBounds(denseScene(), 1.0, standIn(denseScene(), 1.0), 11, 77.894615, 138.704698,
            "Axiom of Choice");
        assertBounds(denseScene(), 2.0, standIn(denseScene(), 2.0), 11, 73.611911, 118.655013,
            "Axiom of Choice");
        assertBounds(longScene(), 1.0, standIn(longScene(), 1.0), 6, 78.656464, 143.545734,
            "Well-Ordering Theorem of Choice and Regularity");
        assertBounds(longScene(), 2.0, standIn(longScene(), 2.0), 5, 86.656464, 151.533443,
            "Well-Ordering Theorem of Choice and Regularity");
    }

    private static void assertBounds(List<SceneNode> scene, double zoom, Rectangle2D standIn,
            int expectedVisible, double expectedMaxLeader, double expectedMaxSupport,
            String forcedName) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        assertThat(countVisible(placed)).isEqualTo(expectedVisible);
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            String slot = slotOf(label, scene, zoom);
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
            double radius = Math.max(2.0, node.radius * zoom);
            assertThat(leader(label, scene, zoom)).as("leader " + slot)
                .isLessThanOrEqualTo(leaderForm(slot, radius, label.width(), label.height()) + 1e-6);
            assertThat(support(label, scene, zoom)).as("support " + slot)
                .isLessThanOrEqualTo(supportForm(slot, radius, label.width(), label.height()) + 1e-6);
        }
        assertThat(maxLeader(placed, scene, zoom)).isCloseTo(expectedMaxLeader, within(1e-4));
        assertThat(maxSupport(placed, scene, zoom)).isCloseTo(expectedMaxSupport, within(1e-4));
    }

    static double leaderForm(String slot, double radius, double width, double height) {
        if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
            return radius + 6.0 + height / 2.0;
        }
        if ("ABOVE_FAR".equals(slot) || "BELOW_FAR".equals(slot)) {
            return radius + 30.0 + height / 2.0;
        }
        if ("RIGHT".equals(slot) || "LEFT".equals(slot)) {
            return radius + 6.0 + width / 2.0;
        }
        if ("RIGHT_FAR".equals(slot) || "LEFT_FAR".equals(slot)) {
            return radius + 30.0 + width / 2.0;
        }
        return Math.hypot(radius + 6.0 + width / 2.0, radius + 6.0 + height / 2.0);
    }

    static double supportForm(String slot, double radius, double width, double height) {
        if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
            return Math.hypot(width / 2.0, radius + 6.0 + height);
        }
        if ("ABOVE_FAR".equals(slot) || "BELOW_FAR".equals(slot)) {
            return Math.hypot(width / 2.0, radius + 30.0 + height);
        }
        if ("RIGHT".equals(slot) || "LEFT".equals(slot)) {
            return Math.hypot(radius + 6.0 + width, height / 2.0);
        }
        if ("RIGHT_FAR".equals(slot) || "LEFT_FAR".equals(slot)) {
            return Math.hypot(radius + 30.0 + width, height / 2.0);
        }
        return Math.hypot(radius + 6.0 + width, radius + 6.0 + height);
    }

    static double support(PlacedLabel label, List<SceneNode> scene, double zoom) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + 564.0;
        double centerY = node.y * zoom + 182.0;
        return Math.hypot(Math.abs(label.anchorX() - centerX) + label.width() / 2.0,
            Math.abs(label.anchorY() - centerY) + label.height() / 2.0);
    }

    static double maxSupport(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        double max = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                max = Math.max(max, support(label, scene, zoom));
            }
        }
        return max;
    }
```

`sceneNode` is the Task 5 private helper; change its visibility to package-private `static` so this task's helpers can call it. The four rows are the oracle's per-slot maxima table (`77.894615`/`138.704698`, `73.611911`/`118.655013`, `78.656464`/`143.545734`, `86.656464`/`151.533443`), asserted per label through the exact per-slot closed forms of §2.7 and never through the deleted single-form bound.

- [ ] **Step 5: Add the hover-only to forced transition test**

Add:

```java
    @Test
    public void promotesAHoverOnlyLabelToForcedWithoutCollisions() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(200.0, 130.0);
        Rectangle2D standIn = standIn(scene, 1.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);
        assertThat(find(baseline, "Well-Ordering").mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(find(baseline, "Comprehension").mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);

        Set<ProjectedEndpointKey> promoted = new LinkedHashSet<ProjectedEndpointKey>();
        promoted.add(ProjectedEndpointKey.ofNode(key("Axiom of Choice")));
        promoted.add(ProjectedEndpointKey.ofNode(key("Well-Ordering")));
        List<PlacedLabel> forcedResult = place(scene, 1.0, area, standIn, promoted,
            RenderingLevel.FULL, baseline);

        assertThat(slotOfAt(find(forcedResult, "Well-Ordering"), 51.0 + 100.0, 34.0 + 65.0, 8.0))
            .isEqualTo("BELOW");
        assertThat(find(forcedResult, "Well-Ordering").font().getSize()).isEqualTo(12);
        assertThat(find(forcedResult, "Well-Ordering").anchorY() - 65.0)
            .isCloseTo(56.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(forcedResult, "Comprehension").mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        for (String name : Arrays.asList("Axiom of Choice", "Theorem", "Pairing", "Infinity",
                "Separation")) {
            assertThat(find(forcedResult, name).mode()).as(name).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        }
        assertThat(labelLabelCollisions(forcedResult)).isZero();
        assertThat(discCollisionsIn(forcedResult, scene, 1.0, area)).isZero();
        assertSamePlacement(forcedResult, place(scene, 1.0, area, standIn, promoted,
            RenderingLevel.FULL, forcedResult));
    }
```

- [ ] **Step 6: Add the cache-key test**

Add:

```java
    @Test
    public void reusesTheCachedPlacementUntilTheKeyChanges() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        LabelFonts fonts = LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
        ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        LabelPlacementRequest request = request(scene, 1.0, area, forced("Axiom of Choice"),
            RenderingLevel.FULL);

        List<PlacedLabel> first = cache.place(request, fonts);
        List<PlacedLabel> second = cache.place(request, fonts);
        assertThat(second).isSameAs(first);

        LabelPlacementRequest rebuilt = request(scene, 1.0, area, forced("Axiom of Choice"),
            RenderingLevel.FULL);
        List<PlacedLabel> third = cache.place(rebuilt, fonts);
        assertThat(third).isNotSameAs(first);
        assertSamePlacement(first, third);

        LabelPlacementRequest forcedChanged = request(scene, 1.0, area, forced("Axiom of Choice",
            "Well-Ordering"), RenderingLevel.FULL);
        List<PlacedLabel> fourth = cache.place(forcedChanged, fonts);
        assertThat(fourth).isNotSameAs(first);
        assertThat(find(fourth, "Well-Ordering").forced()).isTrue();
        assertThat(labelLabelCollisions(fourth)).isZero();
    }
```

- [ ] **Step 7: Run the fixtures**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould" -PTestLoggingFull
```

Expected: PASS with 14 tests. If a retained anchor, painted-ink overhang, minimum gap or per-slot bound differs, compare against the oracle run:

```bash
java -Djava.awt.headless=true docs/superpowers/specs/mockups/2026-09-12-node-separation/FixtureProbe.java | grep -A 1 "5.7"
```

The oracle prints `checks=689 failures=0`; every mismatch is either an implementation defect or a test transcription defect. Fix the implementation first; change a pinned literal only with the oracle's printed value as evidence. If the only way to pass requires changing `ScreenLabelPlacement`, keep the change inside the pinned rules of §2.7 and report it.

- [ ] **Step 8: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java
git commit -m "Prove retention, invalidation and painted-ink separation for labels [2026-09-12-graph-node-separation]"
```

## Task 7: Place enclosure labels through the pinned tier rule

**Implementer tier:** Advanced
**Lane:** screen-placement
**Depends on:** Task 5

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` (imports, `place`, new enclosure methods, `filterByLevel`)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` (new fixtures)

**Interfaces:**
- Consumes: `ProjectedEnclosure.of(EnclosureHullKey, List<EnclosureKey>, List<SafeNodeLabel>, String, Optional<EnclosureHullKey>, List<ProjectedNodeKey>, List<EnclosureHullKey>, boolean, BoundaryTier)`; `ProjectedEnclosure.hullKey()`, `endpointKeys()`, `labels()`, `boundaryTier()`; `BoundaryTier.SUPPRESSED/EMPHATIC/SUBTLE`; `HullGeometry.of(List<LayoutPoint>, LayoutPoint)`, `exactPolygon()`, `labelAnchor()`, `contains(LayoutPoint)`, `nearestBoundaryPoint(LayoutPoint)`; `EnclosureHullKey.of(List<EnclosureKey>)`; `EnclosureKey.of(SourceNodeKey)`; `LabelPlacementRequest.projection()`, `geometry()`, `placementArea()`, `forced()`, `renderingLevel()`, `screenX/screenY/worldX/worldY`; the package-private `ScreenLabelPlacement` helpers from Task 5 (`rectangle`, `screenBounds`, `slotMaxWidth`, `intersectsAny`) and Task 6 test helpers (`denseScene`, `place`, `placeShifted`, `find`, `slotOfAt`, `discCollisionsIn`, `standInIn`).
- Produces: enclosure placement inside `ScreenLabelPlacement.place` (Tier A `INTERIOR`, Tier B `ARC`, Tier C `EXTERNAL` with the finite lane rule and the subtle budget, the emphatic anchor terminal, the subtle `HOVER_ONLY` terminal), the required-emphatic level filter, and the tests `placesEmphaticEnclosureLabelsInTheHullInterior`, `placesEmphaticEnclosureLabelsOnTheLeastPopulatedArc`, `placesEmphaticEnclosureLabelsExternallyWithALeader`, `forcesAnEmphaticEnclosureLabelAtTheAnchorWhenNoSlotIsAccepted`, `hidesASubtleEnclosureLabelWhenNoExternalSlotIsAccepted`, `keepsHullGeometryUnchangedAcrossEnclosurePlacement`, `placesEmphaticAndSubtleEnclosureLabelsThroughTheLevelFilter`.

- [ ] **Step 1: Add the enclosure group to `place`**

In `ScreenLabelPlacement.java`, add the imports `org.freeplane.plugin.graph.geometry.HullGeometry`, `org.freeplane.plugin.graph.projection.BoundaryTier`, `org.freeplane.plugin.graph.projection.EnclosureKey`, `org.freeplane.plugin.graph.projection.ProjectedEnclosure`, then replace the body of `place(request, previous, fonts, seedObstacles)` with:

```java
        final Context context = new Context(request, fonts, seedObstacles);
        final Map<ProjectedNodeKey, PlacedLabel> previousByNode = indexPrevious(previous);
        final List<PlacedLabel> placed = new ArrayList<PlacedLabel>();
        placeForced(context, previousByNode, placed);
        placeEnclosures(context, placed);
        placeNodes(context, previousByNode, placed);
        return filterByLevel(request, placed);
```

Add `placeEnclosures` and its helpers after `placeNodes`:

```java
    private static void placeEnclosures(final Context context, final List<PlacedLabel> placed) {
        for (final ProjectedEnclosure enclosure : context.request.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry hull = context.request.geometry().hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            final boolean emphatic = enclosure.boundaryTier() == BoundaryTier.EMPHATIC;
            final Font font = emphatic ? context.fonts.emphatic() : context.fonts.full();
            final List<EnclosureKey> endpoints = enclosure.endpointKeys();
            for (int index = 0; index < endpoints.size(); index++) {
                final EnclosureKey endpointKey = endpoints.get(index);
                final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(endpointKey);
                final String text = enclosure.labels().get(index).displayText();
                final boolean forced = context.request.forced().contains(endpoint);
                placed.add(placeEnclosureLabel(context, endpoint, text, font, emphatic, hull, forced));
            }
        }
    }

    private static PlacedLabel placeEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final boolean emphatic,
            final HullGeometry hull, final boolean forced) {
        final Rectangle2D size = screenBounds(text, font);
        final List<LayoutPoint> polygon = screenPolygon(context, hull);
        final PlacedLabel interior = interiorEnclosureLabel(context, endpoint, text, font, hull, size, forced);
        if (interior != null) {
            return interior;
        }
        final PlacedLabel arc = arcEnclosureLabel(context, endpoint, text, font, hull, size, forced, polygon);
        if (arc != null) {
            return arc;
        }
        final PlacedLabel external = externalEnclosureLabel(context, endpoint, text, font, emphatic, hull,
            size, forced, polygon);
        if (external != null) {
            return external;
        }
        return anchorTerminal(context, endpoint, text, font, emphatic, hull, size, forced);
    }

    private static PlacedLabel interiorEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final HullGeometry hull,
            final Rectangle2D size, final boolean forced) {
        final double anchorX = context.request.screenX(hull.labelAnchor().x());
        final double anchorY = context.request.screenY(hull.labelAnchor().y());
        final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
        if (!context.request.placementArea().contains(candidate)
                || !rectInHull(context, hull, candidate)
                || intersectsAny(context.obstacles, candidate)) {
            return null;
        }
        context.obstacles.add(candidate);
        return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.INTERIOR, PlacedLabel.Rung.FULL_NEAR,
            anchorX, anchorY, size, forced, false, Optional.<LayoutPoint>empty());
    }

    private static PlacedLabel arcEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final HullGeometry hull,
            final Rectangle2D size, final boolean forced, final List<LayoutPoint> polygon) {
        final int edgeCount = polygon.size();
        final int[] population = new int[edgeCount];
        final double[] lengths = new double[edgeCount];
        for (int index = 0; index < edgeCount; index++) {
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            population[index] = edgePopulation(polygon, start, end, outwardNormal(polygon, index),
                context.obstacles, size.getHeight() + ARC_GAP);
            lengths[index] = Math.hypot(end.x() - start.x(), end.y() - start.y());
        }
        final List<Integer> edgeOrder = new ArrayList<Integer>();
        for (int index = 0; index < edgeCount; index++) {
            edgeOrder.add(Integer.valueOf(index));
        }
        Collections.sort(edgeOrder, new Comparator<Integer>() {
            @Override
            public int compare(final Integer first, final Integer second) {
                final int firstIndex = first.intValue();
                final int secondIndex = second.intValue();
                if (population[firstIndex] != population[secondIndex]) {
                    return population[firstIndex] - population[secondIndex];
                }
                if (lengths[firstIndex] != lengths[secondIndex]) {
                    return Double.compare(lengths[secondIndex], lengths[firstIndex]);
                }
                return firstIndex - secondIndex;
            }
        });
        for (final Integer edge : edgeOrder) {
            final int index = edge.intValue();
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            final LayoutPoint outward = outwardNormal(polygon, index);
            final double anchorX = (start.x() + end.x()) * 0.5
                - outward.x() * (size.getHeight() * 0.5 + ARC_GAP);
            final double anchorY = (start.y() + end.y()) * 0.5
                - outward.y() * (size.getHeight() * 0.5 + ARC_GAP);
            final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
            if (context.request.placementArea().contains(candidate)
                    && rectInHull(context, hull, candidate)
                    && !intersectsAny(context.obstacles, candidate)) {
                context.obstacles.add(candidate);
                return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.ARC, PlacedLabel.Rung.FULL_NEAR,
                    anchorX, anchorY, size, forced, false, Optional.<LayoutPoint>empty());
            }
        }
        return null;
    }

    private static PlacedLabel externalEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final boolean emphatic,
            final HullGeometry hull, final Rectangle2D size, final boolean forced,
            final List<LayoutPoint> polygon) {
        final int edgeCount = polygon.size();
        int total = 0;
        for (int index = 0; index < edgeCount; index++) {
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            final LayoutPoint outward = outwardNormal(polygon, index);
            final double areaSupport = areaSupport(context.request.placementArea(), outward);
            final double halfNormal = outward.x() != 0.0
                ? size.getWidth() * 0.5 : size.getHeight() * 0.5;
            final double midpointX = (start.x() + end.x()) * 0.5;
            final double midpointY = (start.y() + end.y()) * 0.5;
            for (int lane = 0; ; lane++) {
                total++;
                final double distance = size.getHeight() * 0.5 + EXTERNAL_GAP
                    + lane * (size.getHeight() + EXTERNAL_GAP);
                final double anchorX = midpointX + outward.x() * distance;
                final double anchorY = midpointY + outward.y() * distance;
                final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
                if (!hull.contains(worldPoint(context, anchorX, anchorY))
                        && context.request.placementArea().contains(candidate)
                        && !intersectsAny(context.obstacles, candidate)) {
                    final LayoutPoint leaderWorld = hull.nearestBoundaryPoint(
                        worldPoint(context, anchorX, anchorY));
                    final LayoutPoint leader = LayoutPoint.of(context.request.screenX(leaderWorld.x()),
                        context.request.screenY(leaderWorld.y()));
                    context.obstacles.add(candidate);
                    return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.EXTERNAL,
                        PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, false,
                        Optional.of(leader));
                }
                if (!emphatic && total >= SUBTLE_EXTERNAL_CANDIDATE_BUDGET) {
                    return null;
                }
                final double innerEdge = outward.x() * anchorX + outward.y() * anchorY - halfNormal;
                if (innerEdge > areaSupport) {
                    break;
                }
            }
        }
        return null;
    }

    private static PlacedLabel anchorTerminal(final Context context, final ProjectedEndpointKey endpoint,
            final String text, final Font font, final boolean emphatic, final HullGeometry hull,
            final Rectangle2D size, final boolean forced) {
        final double anchorX = context.request.screenX(hull.labelAnchor().x());
        final double anchorY = context.request.screenY(hull.labelAnchor().y());
        if (emphatic) {
            return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.INTERIOR,
                PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, true,
                Optional.<LayoutPoint>empty());
        }
        return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.HOVER_ONLY,
            PlacedLabel.Rung.HOVER_ONLY, anchorX, anchorY, size, forced, false,
            Optional.<LayoutPoint>empty());
    }

    private static PlacedLabel enclosureLabel(final ProjectedEndpointKey endpoint, final String text,
            final Font font, final PlacedLabel.Mode mode, final PlacedLabel.Rung rung, final double anchorX,
            final double anchorY, final Rectangle2D size, final boolean forced,
            final boolean emphaticAtAnchor, final Optional<LayoutPoint> leaderStart) {
        return new PlacedLabel(endpoint, text, font, mode, rung, anchorX, anchorY, size.getWidth(),
            size.getHeight(), false, forced, emphaticAtAnchor, false, false, leaderStart, Slot.ABOVE);
    }

    private static List<LayoutPoint> screenPolygon(final Context context, final HullGeometry hull) {
        final List<LayoutPoint> polygon = new ArrayList<LayoutPoint>();
        for (final LayoutPoint point : hull.exactPolygon()) {
            polygon.add(LayoutPoint.of(context.request.screenX(point.x()),
                context.request.screenY(point.y())));
        }
        return polygon;
    }

    private static LayoutPoint outwardNormal(final List<LayoutPoint> polygon, final int index) {
        final LayoutPoint start = polygon.get(index);
        final LayoutPoint end = polygon.get((index + 1) % polygon.size());
        final double dx = end.x() - start.x();
        final double dy = end.y() - start.y();
        final double length = Math.hypot(dx, dy);
        final double sign = signedArea(polygon) >= 0.0 ? 1.0 : -1.0;
        return LayoutPoint.of(sign * dy / length, -sign * dx / length);
    }

    private static double signedArea(final List<LayoutPoint> polygon) {
        double area = 0.0;
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint first = polygon.get(index);
            final LayoutPoint second = polygon.get((index + 1) % polygon.size());
            area += first.x() * second.y() - second.x() * first.y();
        }
        return area * 0.5;
    }

    private static boolean rectInHull(final Context context, final HullGeometry hull,
            final Rectangle2D rectangle) {
        return hull.contains(worldPoint(context, rectangle.getMinX(), rectangle.getMinY()))
            && hull.contains(worldPoint(context, rectangle.getMinX(), rectangle.getMaxY()))
            && hull.contains(worldPoint(context, rectangle.getMaxX(), rectangle.getMinY()))
            && hull.contains(worldPoint(context, rectangle.getMaxX(), rectangle.getMaxY()));
    }

    private static LayoutPoint worldPoint(final Context context, final double screenX,
            final double screenY) {
        return LayoutPoint.of(context.request.worldX(screenX), context.request.worldY(screenY));
    }

    private static int edgePopulation(final List<LayoutPoint> polygon, final LayoutPoint start,
            final LayoutPoint end, final LayoutPoint outward, final List<Rectangle2D> obstacles,
            final double depth) {
        final double dx = end.x() - start.x();
        final double dy = end.y() - start.y();
        final double length = Math.hypot(dx, dy);
        final double tangentX = dx / length;
        final double tangentY = dy / length;
        final double minT = Math.min(tangentX * start.x() + tangentY * start.y(),
            tangentX * end.x() + tangentY * end.y());
        final double maxT = Math.max(tangentX * start.x() + tangentY * start.y(),
            tangentX * end.x() + tangentY * end.y());
        double support = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : polygon) {
            support = Math.max(support, outward.x() * point.x() + outward.y() * point.y());
        }
        int count = 0;
        for (final Rectangle2D obstacle : obstacles) {
            final double[] onTangent = projection(obstacle, tangentX, tangentY);
            final double[] onNormal = projection(obstacle, outward.x(), outward.y());
            if (onTangent[0] <= maxT && onTangent[1] >= minT
                    && onNormal[0] <= support + depth && onNormal[1] >= support - depth) {
                count++;
            }
        }
        return count;
    }

    private static double[] projection(final Rectangle2D rectangle, final double normalX,
            final double normalY) {
        final double first = normalX * rectangle.getMinX() + normalY * rectangle.getMinY();
        final double second = normalX * rectangle.getMinX() + normalY * rectangle.getMaxY();
        final double third = normalX * rectangle.getMaxX() + normalY * rectangle.getMinY();
        final double fourth = normalX * rectangle.getMaxX() + normalY * rectangle.getMaxY();
        return new double[] { Math.min(Math.min(first, second), Math.min(third, fourth)),
            Math.max(Math.max(first, second), Math.max(third, fourth)) };
    }

    private static double areaSupport(final Rectangle2D area, final LayoutPoint normal) {
        final double first = normal.x() * area.getMinX() + normal.y() * area.getMinY();
        final double second = normal.x() * area.getMinX() + normal.y() * area.getMaxY();
        final double third = normal.x() * area.getMaxX() + normal.y() * area.getMinY();
        final double fourth = normal.x() * area.getMaxX() + normal.y() * area.getMaxY();
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }
```

Replace `filterByLevel` with:

```java
    private static List<PlacedLabel> filterByLevel(final LabelPlacementRequest request,
            final List<PlacedLabel> placed) {
        if (request.renderingLevel() != RenderingLevel.OVER_TARGET) {
            return placed;
        }
        final List<PlacedLabel> filtered = new ArrayList<PlacedLabel>();
        for (final PlacedLabel label : placed) {
            if (label.forced() || isRequiredEmphaticEnclosure(request, label)) {
                filtered.add(label);
            }
        }
        return filtered;
    }

    private static boolean isRequiredEmphaticEnclosure(final LabelPlacementRequest request,
            final PlacedLabel label) {
        if (!label.endpoint().isEnclosure()) {
            return false;
        }
        final EnclosureKey target = label.endpoint().enclosure().get();
        for (final ProjectedEnclosure enclosure : request.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(target)) {
                return enclosure.boundaryTier() == BoundaryTier.EMPHATIC;
            }
        }
        return false;
    }
```

The critical detail is the Tier C loop: an edge is abandoned at the first lane whose candidate's inward edge passes the area's outward support (`innerEdge > areaSupport`), so the search is finite for any finite area; subtle labels stop after `SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8` candidates in total; emphatic labels search every edge. The rule runs in screen space on the mapped polygon, and `HullGeometry` is never mutated.

- [ ] **Step 2: Add the §5.8 fixtures**

In `ScreenLabelPlacementShould.java`, add this test and the helpers:

```java
    @Test
    public void placesEmphaticEnclosureLabelsInTheHullInterior() {
        Rectangle2D area = area(1128.0, 364.0);
        PlacedLabel label = placeEnclosure("Axioms", true, area, Collections.<SceneNode>emptyList(),
            RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(label.font().getSize()).isEqualTo(15);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(label.emphaticAtAnchor()).isFalse();
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void placesEmphaticEnclosureLabelsOnTheLeastPopulatedArc() {
        Rectangle2D area = area(1128.0, 364.0);
        List<SceneNode> obstacle = Collections.singletonList(
            new SceneNode("Theorem", 8.0, false, 0.0, 0.0));
        PlacedLabel label = placeEnclosure("Axioms", true, area, obstacle, RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.ARC);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-38.784928, within(1e-6));
        assertThat(label.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void placesEmphaticEnclosureLabelsExternallyWithALeader() {
        Rectangle2D area = area(320.0, 320.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-64.215072, within(1e-6));
        assertThat(label.leaderStart()).isPresent();
        assertThat(label.leaderStart().get().x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart().get().y() - area.getHeight() * 0.5)
            .isCloseTo(-50.0, within(1e-6));
    }

    @Test(timeout = 5000)
    public void forcesAnEmphaticEnclosureLabelAtTheAnchorWhenNoSlotIsAccepted() {
        Rectangle2D area = area(120.0, 120.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(label.emphaticAtAnchor()).isTrue();
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test(timeout = 5000)
    public void hidesASubtleEnclosureLabelWhenNoExternalSlotIsAccepted() {
        Rectangle2D area = area(120.0, 120.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", false, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void keepsHullGeometryUnchangedAcrossEnclosurePlacement() {
        Rectangle2D area = area(320.0, 320.0);
        LabelPlacementRequest request = enclosureRequest("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
        Map<EnclosureHullKey, HullGeometry> before =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(request.geometry().hulls());

        new ScreenLabelPlacement().place(request, null, fonts(), Collections.<Rectangle2D>emptyList());

        assertThat(request.geometry().hulls()).isEqualTo(before);
        assertThat(request.geometry().hulls().get(firstHullKey())).isEqualTo(before.get(firstHullKey()));
    }
```

Add the §5.9 cell in the same step:

```java
    @Test
    public void placesEmphaticAndSubtleEnclosureLabelsThroughTheLevelFilter() {
        Rectangle2D area = area(1128.0, 364.0);
        List<SceneNode> nodes = Collections.singletonList(
            new SceneNode("Theorem", 8.0, false, 0.0, -80.0));

        List<PlacedLabel> emphatic = placeEnclosure("Axioms", true, area, nodes, RenderingLevel.FULL);
        assertThat(emphatic).hasSize(2);
        PlacedLabel enclosure = findEnclosure(emphatic);
        assertThat(enclosure.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(enclosure.font().getSize()).isEqualTo(15);
        assertThat(enclosure.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(enclosure.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(enclosure.emphaticAtAnchor()).isFalse();
        assertThat(enclosure.leaderStart()).isEmpty();
        PlacedLabel node = find(emphatic, "Theorem");
        assertThat(node.font().getSize()).isEqualTo(12);
        assertThat(node.width()).isCloseTo(51.060364, within(1e-6));
        assertThat(node.height()).isCloseTo(16.344114, within(1e-6));
        assertThat(node.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(node.anchorY() - area.getHeight() * 0.5).isCloseTo(-102.172057, within(1e-6));
        assertThat(enclosure.bounds().intersects(node.bounds())).isFalse();

        List<PlacedLabel> overTarget = placeEnclosure("Axioms", true, area, nodes,
            RenderingLevel.OVER_TARGET);
        assertThat(overTarget).hasSize(1);
        assertThat(overTarget.get(0).endpoint().isEnclosure()).isTrue();

        List<PlacedLabel> subtle = placeEnclosure("Axioms", false, area, nodes, RenderingLevel.FULL);
        assertThat(subtle).hasSize(2);
        assertThat(findEnclosure(subtle).font().getSize()).isEqualTo(12);
        assertThat(findEnclosure(subtle).width()).isCloseTo(41.340302, within(1e-6));
        assertThat(findEnclosure(subtle).height()).isCloseTo(16.344114, within(1e-6));
        assertThat(placeEnclosure("Axioms", false, area, nodes, RenderingLevel.OVER_TARGET)).isEmpty();
    }
```

Add these helpers:

```java
    static LabelFonts fonts() {
        return LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
    }

    static List<PlacedLabel> placeEnclosure(String text, boolean emphatic, Rectangle2D area,
            List<SceneNode> nodes, RenderingLevel level) {
        return new ScreenLabelPlacement().place(
            enclosureRequest(text, emphatic, area, nodes, level), null, fonts(),
            Collections.<Rectangle2D>emptyList());
    }

    static LabelPlacementRequest enclosureRequest(String text, boolean emphatic, Rectangle2D area,
            List<SceneNode> nodes, RenderingLevel level) {
        EnclosureKey endpointKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("axioms"))));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpointKey));
        List<LayoutPoint> polygon = Arrays.asList(LayoutPoint.of(-50.0, -50.0),
            LayoutPoint.of(50.0, -50.0), LayoutPoint.of(50.0, 50.0), LayoutPoint.of(-50.0, 50.0));
        List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
        List<ProjectedNode> projected = new ArrayList<ProjectedNode>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (SceneNode node : nodes) {
            ProjectedNodeKey nodeKey = key(node.name);
            projected.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(node.name, node.name), "Map", false));
            geometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(node.x, node.y), node.radius));
            positions.put(nodeKey, LayoutPoint.of(node.x, node.y));
            directNodes.add(nodeKey);
        }
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(endpointKey), Collections.singletonList(SafeNodeLabel.of(text, text)),
            "Map", Optional.<EnclosureHullKey>empty(), directNodes,
            Collections.<EnclosureHullKey>emptyList(), true,
            emphatic ? BoundaryTier.EMPHATIC : BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.structure(1L, projected,
            Collections.singletonList(enclosure));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hullKey, HullGeometry.of(polygon, LayoutPoint.of(0.0, 0.0)));
        GraphGeometry graphGeometry = GraphGeometry.of(geometry, hulls);
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(0.0, 0.0));
        return LabelPlacementRequest.of(projection, graphGeometry, LayoutPositions.of(positions, anchors),
            1.0, 0.0, 0.0, area, Collections.<ProjectedEndpointKey>emptySet(), level);
    }

    static PlacedLabel findEnclosure(List<PlacedLabel> placed) {
        for (PlacedLabel label : placed) {
            if (label.endpoint().isEnclosure()) {
                return label;
            }
        }
        return null;
    }

    static EnclosureHullKey firstHullKey() {
        EnclosureKey endpointKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("axioms"))));
        return EnclosureHullKey.of(Collections.singletonList(endpointKey));
    }
```

Add the imports `java.util.Optional`, `org.freeplane.plugin.graph.projection.BoundaryTier`, `org.freeplane.plugin.graph.projection.EnclosureKey`, `org.freeplane.plugin.graph.projection.ProjectedEnclosure`. The expected values are the oracle's §5.8 row (`(0,-38.784928)` arc, `(0,-64.215072)` external with leader `(0,-50)`, the emphatic terminal at `(0,0)`) and the §5.9 emphatic/subtle enclosure cell (`55.065384x20.430143` bold 15, `41.340302x16.344114` plain 12, node `Theorem` `ABOVE` at `(0,-102.172057)` `51.060364x16.344114`, `OVER_TARGET` exactly one emphatic label and zero subtle labels). The candidate counts of the finite lane rule (16 total `2/6/2/6` for the emphatic terminal, 8 total `2/6/0/0` for the subtle terminal) are pinned in the oracle and are covered there; the unit tests assert termination through `@Test(timeout = 5000)` and the terminal outcomes.

- [ ] **Step 3: Run the fixtures**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould" -PTestLoggingFull
```

Expected: PASS with 21 tests. Run the oracle once for the enclosure sections:

```bash
java -Djava.awt.headless=true docs/superpowers/specs/mockups/2026-09-12-node-separation/FixtureProbe.java | sed -n '/5.8 Enclosure/,/5.9 Zoom/p'
```

Every anchor, mode, font and size in those lines must match the assertions. If the emphatic terminal does not terminate (test timeout), the lane abandonment rule is wrong; fix the rule, not the test.

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java
git commit -m "Place enclosure labels through the pinned tier rule [2026-09-12-graph-node-separation]"
```

## Task 8: Rewrite the painter and wire placement into the paint path

**Implementer tier:** Advanced
**Lane:** integration
**Depends on:** Tasks 5, 6, 7 (both lanes merged)

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:36-90`, `:231-313`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:17-30`, `:357-366`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java:38-71`, `:254-271`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`

**Interfaces:**
- Consumes: `ScreenLabelPlacementCache.place(LabelPlacementRequest, LabelFonts)`; `LabelPlacementRequest.of(GraphProjection, GraphGeometry, LayoutPositions, double, double, double, Rectangle2D, Set<ProjectedEndpointKey>, RenderingLevel)`; `LabelFonts.from(GraphTheme)`; `PlacedLabel.text()`, `font()`, `mode()`, `anchorX()`, `anchorY()`, `bounds()`, `leaderStart()`, `forced()`, `endpoint()`; `ScreenLabelPlacement.SCREEN_FRC`; `GraphPaintState.selection()`, `hover()`, `searchMatches()`; `GraphCanvas`'s existing `renderingPolicy.forCounts(int, int)`, `viewport`, `theme`, `paintState`, `canvasState`; existing test helpers `fixture(double)`, `labelFixture(int)`, `paintLabelFixture(LabelFixture, GraphTheme, GraphPaintState)`, `paintCanvas(GraphCanvas)`, `lightTheme()`, `labelPixels(BufferedImage, GraphTheme, int, int, int, int)`, `forcedOrdinaryPaintState(LabelFixture)`, `SIZE`.
- Produces: `GraphPainter.paint(Graphics2D, CanvasState, GraphPaintState, GraphViewport, Dimension, GraphTheme, List<PlacedLabel>)` and the 9-argument overload with `showArrowheads`/`dimUnrelatedEnabled`; `GraphCanvas.labelsFor(CanvasState, Dimension, RenderingLevel)`; the deleted `GraphPainter.labelFont`/`shouldPaintLabel` and `GraphTheme.overTargetLabelFont()`/`labelFont(RenderingLevel)`; migrated `GraphCanvasPaintShould`; the new test `keepsPublishedGeometryStableAcrossPlacementChanges`.

- [ ] **Step 1: Rewrite `GraphPainter` to consume carried placements**

In `GraphPainter.java`, change the two `paint` signatures (current lines 37-45) to carry the placement list instead of a rendering level, and remove the now-unused `level` handling:

```java
    void paint(final Graphics2D graphics, final CanvasState state, final GraphPaintState paintState,
            final GraphViewport viewport, final java.awt.Dimension size, final GraphTheme theme,
            final List<PlacedLabel> labels) {
        paint(graphics, state, paintState, viewport, size, theme, labels, true, true);
    }

    void paint(final Graphics2D graphics, final CanvasState state, final GraphPaintState paintState,
            final GraphViewport viewport, final java.awt.Dimension size, final GraphTheme theme,
            final List<PlacedLabel> labels, final boolean showArrowheads, final boolean dimUnrelatedEnabled) {
```

Inside the second method delete `final RenderingLevel renderingLevel = Objects.requireNonNull(level, "level");` and change the `paintLabels` call (current line 74) to:

```java
            paintLabels(copy, labels, paintState, currentTheme, currentViewport, componentSize, dimUnrelated);
```

Replace the whole `paintLabels`, `labelFont`, `shouldPaintLabel` and `drawCentered` block (current lines 231-313) with:

```java
    private static void paintLabels(final Graphics2D graphics, final List<PlacedLabel> labels,
            final GraphPaintState paintState, final GraphTheme theme, final GraphViewport viewport,
            final java.awt.Dimension size, final boolean dimUnrelated) {
        for (final PlacedLabel label : labels) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY && !label.forced()) {
                continue;
            }
            final boolean forced = label.forced() || isRelated(label.endpoint(), paintState);
            final boolean dim = dimUnrelated && !forced;
            final AlphaComposite oldComposite = setOpacity(graphics, dim);
            final Font font = label.font().deriveFont(Math.max(1.0f,
                label.font().getSize2D() / (float) viewport.zoom()));
            graphics.setFont(font);
            graphics.setColor(theme.labelColor());
            final double anchorX = worldX(label.anchorX(), viewport, size);
            final double anchorY = worldY(label.anchorY(), viewport, size);
            if (label.leaderStart().isPresent()) {
                graphics.setStroke(theme.edgeStroke());
                graphics.draw(new Line2D.Double(worldX(label.leaderStart().get().x(), viewport, size),
                    worldY(label.leaderStart().get().y(), viewport, size), anchorX, anchorY));
            }
            drawCentered(graphics, font, label.text(), anchorX, anchorY);
            graphics.setComposite(oldComposite);
        }
    }

    private static double worldX(final double screenX, final GraphViewport viewport,
            final java.awt.Dimension size) {
        return viewport.centerX() + (screenX - size.getWidth() * 0.5) / viewport.zoom();
    }

    private static double worldY(final double screenY, final GraphViewport viewport,
            final java.awt.Dimension size) {
        return viewport.centerY() + (screenY - size.getHeight() * 0.5) / viewport.zoom();
    }

    private static void drawCentered(final Graphics2D graphics, final Font font, final String text,
            final double x, final double y) {
        final Rectangle2D bounds = font.getStringBounds(text, ScreenLabelPlacement.SCREEN_FRC);
        final java.awt.font.LineMetrics line = font.getLineMetrics(text, ScreenLabelPlacement.SCREEN_FRC);
        final float baseline = (line.getAscent() - line.getDescent()) * 0.5f;
        graphics.drawString(text, (float) (x - bounds.getWidth() * 0.5), (float) (y + baseline));
    }
```

Add the imports `java.awt.geom.Rectangle2D` and `java.util.List`, and delete the now-unused `org.freeplane.plugin.graph.geometry.LabelPlacement` and `org.freeplane.plugin.graph.projection.EnclosureKey` imports. `BoundaryTier` is still used by `paintHulls`; keep it. The double-precision `getStringBounds` width is the C14 mandate: do not reintroduce `FontMetrics.stringWidth`.

- [ ] **Step 2: Wire the cache into `GraphCanvas`**

In `GraphCanvas.java`, add the imports `java.awt.geom.Rectangle2D`, `java.util.Collections`, `java.util.LinkedHashSet`, `java.util.List`, `org.freeplane.plugin.graph.projection.ProjectedEndpointKey`, then add the field next to `painter`:

```java
    private final ScreenLabelPlacementCache labelPlacementCache = new ScreenLabelPlacementCache();
```

Replace `paintComponent` (current lines 357-366) with:

```java
    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Dimension size = new Dimension(getWidth(), getHeight());
        final CanvasState state = canvasState;
        final int nodeCount = state == null ? 0 : state.projection().projectedNodeCount();
        final int edgeCount = state == null ? 0 : state.projection().edges().size();
        final RenderingLevel level = renderingPolicy.forCounts(nodeCount, edgeCount);
        final List<PlacedLabel> labels = state == null
            ? Collections.<PlacedLabel>emptyList() : labelsFor(state, size, level);
        painter.paint((Graphics2D) graphics, state, paintState, viewport, size, theme, labels,
            showArrowheads, dimUnrelated);
    }

    private List<PlacedLabel> labelsFor(final CanvasState state, final Dimension size,
            final RenderingLevel level) {
        final Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        if (paintState.selection().isPresent()) {
            forced.add(paintState.selection().get());
        }
        if (paintState.hover().isPresent()) {
            forced.add(paintState.hover().get());
        }
        forced.addAll(paintState.searchMatches());
        final LabelPlacementRequest request = LabelPlacementRequest.of(state.projection(), state.geometry(),
            state.layout().positions(), viewport.zoom(), viewport.centerX(), viewport.centerY(),
            new Rectangle2D.Double(0.0, 0.0, size.width, size.height), forced, level);
        return labelPlacementCache.place(request, LabelFonts.from(theme));
    }
```

The cache owns `previous`, so I4 stickiness survives layout, zoom and pan changes; placement is recomputed on a key miss and the old list is re-validated.

- [ ] **Step 3: Delete the dead theme members**

In `GraphTheme.java` delete `overTargetLabelFont()` (current lines 258-260), `labelFont(RenderingLevel)` (current lines 262-271), the `overTargetLabelFont` field (line 41) and its constructor assignment (line 70). Remove the `RenderingLevel` import if nothing else in the file uses it. Do not change `labelFont()`, `denseLabelFont()` or `emphaticLabelFont()`.

- [ ] **Step 4: Migrate `GraphCanvasPaintShould` to the 2-argument `GraphGeometry.of` and the placement-driven fixture**

In `GraphCanvasPaintShould.java`:

1. Delete the `Map<EnclosureKey, LabelPlacement> labels` block in `labelFixture` (current lines 930-944) and change `GraphGeometry.of(geometries, hulls, labels)` (line 971) to `GraphGeometry.of(geometries, hulls)`. Delete the `LabelPlacement` import.
2. Change the 3-argument `GraphGeometry.of` calls at lines 238, 325, 665, 684, 888, 1225 and 1253 to the 2-argument form; where a `labels` local disappears, delete the now-unused local. At line 1253 use `fixture.state.geometry().hulls()`.
3. Replace `useDedicatedEmphaticLabelsAndPreserveLevelSpecificVisibility` (current lines 263-295) with:

```java
    @Test
    public void paintsCarriedPlacementFontsAndPreservesLevelSpecificVisibility() {
        GraphTheme theme = lightTheme();
        LabelFixture fullFixture = labelFixture(499);
        LabelFixture denseFixture = labelFixture(2000);
        LabelFixture overTargetFixture = labelFixture(2001);
        List<PlacedLabel> full = placedLabels(fullFixture, theme, GraphPaintState.empty(),
            RenderingLevel.FULL);
        List<PlacedLabel> dense = placedLabels(denseFixture, theme, GraphPaintState.empty(),
            RenderingLevel.DENSE);
        List<PlacedLabel> overTarget = placedLabels(overTargetFixture, theme, GraphPaintState.empty(),
            RenderingLevel.OVER_TARGET);

        assertThat(theme.emphaticLabelFont().isBold()).isTrue();
        assertThat(theme.emphaticLabelFont().getSize2D()).isGreaterThan(theme.labelFont().getSize2D());
        assertThat(findPlaced(full, "EMPHATIC").font().getSize()).isEqualTo(15);
        assertThat(findPlaced(full, "ORDINARY").font().getSize()).isEqualTo(12);
        assertThat(findPlaced(dense, "EMPHATIC").font().getSize()).isEqualTo(15);
        assertThat(findPlaced(dense, "ORDINARY").font().getSize()).isEqualTo(12);
        assertThat(overTarget).hasSize(1);
        assertThat(overTarget.get(0).text()).isEqualTo("EMPHATIC");
        assertThat(overTarget.get(0).font().getSize()).isEqualTo(15);
        for (PlacedLabel label : full) {
            assertThat(label.font().getSize()).isIn(9, 12, 15);
            assertThat(label.font().getSize()).isNotEqualTo(7);
        }

        GraphPaintState forcedState = forcedOrdinaryPaintState(overTargetFixture);
        List<PlacedLabel> forcedOverTarget = placedLabels(overTargetFixture, theme, forcedState,
            RenderingLevel.OVER_TARGET);
        assertThat(texts(forcedOverTarget)).contains("SELECTED", "HOVERED", "SEARCH");
        assertThat(texts(forcedOverTarget)).doesNotContain("ORDINARY");

        BufferedImage image = paintLabelFixture(overTargetFixture, theme, forcedState);
        for (PlacedLabel label : forcedOverTarget) {
            Rectangle2D bounds = label.bounds();
            assertThat(labelPixels(image, theme, (int) Math.floor(bounds.getMinX()),
                (int) Math.floor(bounds.getMinY()), (int) Math.ceil(bounds.getMaxX()),
                (int) Math.ceil(bounds.getMaxY()))).as(label.text()).isGreaterThan(0);
        }
    }

    private static List<PlacedLabel> placedLabels(LabelFixture fixture, GraphTheme theme,
            GraphPaintState paintState, RenderingLevel level) {
        Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        if (paintState.selection().isPresent()) {
            forced.add(paintState.selection().get());
        }
        if (paintState.hover().isPresent()) {
            forced.add(paintState.hover().get());
        }
        forced.addAll(paintState.searchMatches());
        LabelPlacementRequest request = LabelPlacementRequest.of(fixture.state.projection(),
            fixture.state.geometry(), fixture.state.layout().positions(), 1.0, 0.0, 0.0,
            new java.awt.geom.Rectangle2D.Double(0.0, 0.0, SIZE.width, SIZE.height), forced, level);
        return new ScreenLabelPlacement().place(request, null, LabelFonts.from(theme),
            Collections.<java.awt.geom.Rectangle2D>emptyList());
    }

    private static List<String> texts(List<PlacedLabel> labels) {
        List<String> texts = new ArrayList<String>();
        for (PlacedLabel label : labels) {
            texts.add(label.text());
        }
        return texts;
    }

    private static PlacedLabel findPlaced(List<PlacedLabel> labels, String text) {
        for (PlacedLabel label : labels) {
            if (text.equals(label.text())) {
                return label;
            }
        }
        return null;
    }
```

4. Delete `assertForcedOrdinaryLabelsVisible` (current lines 705-709), `assertEmphaticGlyphUsesDedicatedFont` (711-726), `assertForcedOrdinaryGlyphsUseFullDetailFont` (728-733) and `assertGlyphUsesFullDetailFont` (735-746). Their coverage is replaced above: font selection is asserted on the carried `PlacedLabel.font()` and the paint path is asserted to paint each returned label at its anchor. The 7 pt face no longer exists to compare against; the assertion `font().getSize() != 7` and the absent `overTargetLabelFont()` accessor replace it.

- [ ] **Step 5: Add the geometry-independence test of §5.10**

Add this test to `GraphCanvasPaintShould`:

```java
    @Test
    public void keepsPublishedGeometryStableAcrossPlacementChanges() {
        Fixture fixture = fixture(16.0);
        CanvasState state = fixture.state;
        Map<ProjectedNodeKey, NodeGeometry> nodesBefore =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>(state.geometry().nodes());
        Map<EnclosureHullKey, HullGeometry> hullsBefore =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(state.geometry().hulls());
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(lightTheme());
        canvas.setCanvasState(state);
        canvas.setPaintState(GraphPaintState.empty());
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));

        paintCanvas(canvas);

        canvas.setViewport(GraphViewport.of(0.0, 0.0, 2.0));
        paintCanvas(canvas);

        canvas.setViewport(GraphViewport.of(5.0, -3.0, 1.0));
        paintCanvas(canvas);

        ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(fixture.first.key());
        canvas.setPaintState(GraphPaintState.empty().withSelection(first));
        paintCanvas(canvas);

        assertThat(state.geometry().nodes()).isEqualTo(nodesBefore);
        assertThat(state.geometry().hulls()).isEqualTo(hullsBefore);
        assertThat(state.geometry().nodes()).isEqualTo(fixture.state.geometry().nodes());
    }
```

The test drives the real paint path through `GraphCanvas.paintComponent -> GraphPainter` while zoom, pan and the forced set change; placement recomputes (asserted by the cache test in Task 6) but the published geometry maps stay bit-identical. If a harness setter runs asynchronously, call the existing `flushEdt()`-style helper used elsewhere in the file after each setter before painting.

- [ ] **Step 6: Run the canvas suite and repair the churn**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.*" -PTestLoggingFull
```

Expected: PASS after the migration. The `fixture(...)`-based tests now paint labels produced by the real placement engine instead of the hand-built `LabelPlacement` map, so any pixel region that assumed a hand-built label rectangle is churn: re-anchor it on the returned `PlacedLabel.bounds()` or update the literal to the observed placement, and never delete or weaken an assertion. Record every updated region in the task report. `useDedicatedEmphaticLabelsAndPreserveLevelSpecificVisibility` is renamed, not deleted; the other four helpers are deleted because their assertions moved to the carried font.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Paint carried label placements through the canvas paint path [2026-09-12-graph-node-separation]"
```

## Task 9: Delete the old label engine and migrate its removal surface

**Implementer tier:** Advanced
**Lane:** integration
**Depends on:** Task 8

**Files:**
- Delete: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacementEngine.java`
- Delete: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacement.java`
- Delete: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/LabelPlacementShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java:13-62`, `:101-138`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:57`, `:112`, `:543-544`, `:721-722`, `:982-990`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:71-72`, `:275-298`, `:1043-1049`
- Modify: the remaining files the compiler names: `canvas/GraphInteractionControllerShould.java:588`, `window/GraphWorkspaceWindowModelShould.java:1911`, `window/WorkspaceDialogsShould.java:440` and `:483`, `canvas/AccessibleGraphCanvasShould.java`, `performance/PerformanceTripwiresShould.java`, `smoke/GraphWorkspaceUiEvidence.java`, `control/GraphWorkspaceCommandAcceptanceShould.java`, `command/ContributorDeletionPlanShould.java`, `canvas/GraphSearchModelShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java:290-310`, `:354-358` (remove the deleted engine references only; Task 10 adds the new stages)

**Interfaces:**
- Consumes: `GraphGeometry.of(Map<ProjectedNodeKey, NodeGeometry>, Map<EnclosureHullKey, HullGeometry>)` (no labels); `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)`; `ScreenLabelPlacement.place(LabelPlacementRequest, List<PlacedLabel>, LabelFonts)`; `LabelPlacementRequest.of(...)`; `LabelFonts.from(GraphTheme)`; `PlacedLabel.text()`, `mode()`, `bounds()`, `endpoint()`; `LayoutSettleLoop`'s `Run`/`metrics`/`geometryEngine` fields.
- Produces: the deleted types and members listed in spec §6; a `GraphGeometry` without the label surface; `LayoutSettleLoop` without `LabelAssembler`; the migrated tests; the acceptance-5 grep evidence.

- [ ] **Step 1: Remove the label surface from `GraphGeometry`**

In `GraphGeometry.java`: delete the `labels` field (line 16), the `labels` constructor parameter and the label lookup construction (lines 24-38), the private 2-argument constructor (19-22) so the public `of(nodes, hulls)` constructs directly, the 3-argument `of` (46-50), `labels()` (60-62), `copyLabels` (101-115), and the label parts of `equals` (126), `hashCode` (131) and `toString` (136-137). The result is:

```java
public final class GraphGeometry {
    private final Map<ProjectedNodeKey, NodeGeometry> nodes;
    private final Map<EnclosureHullKey, HullGeometry> hulls;
    private final Map<EnclosureKey, EnclosureHullKey> hullByEnclosureKey;

    private GraphGeometry(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        this.nodes = copyNodes(nodes);
        this.hulls = copyHulls(hulls);
        final Map<EnclosureKey, EnclosureHullKey> lookup = new LinkedHashMap<EnclosureKey, EnclosureHullKey>();
        for (final EnclosureHullKey hullKey : this.hulls.keySet()) {
            for (final EnclosureKey endpoint : hullKey.endpointKeys()) {
                if (lookup.put(endpoint, hullKey) != null) {
                    throw new IllegalArgumentException("An enclosure key must belong to exactly one hull");
                }
            }
        }
        this.hullByEnclosureKey = Collections.unmodifiableMap(lookup);
    }

    public static GraphGeometry of(final Map<ProjectedNodeKey, NodeGeometry> nodes,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        return new GraphGeometry(nodes, hulls);
    }

    public Map<ProjectedNodeKey, NodeGeometry> nodes() {
        return nodes;
    }

    public Map<EnclosureHullKey, HullGeometry> hulls() {
        return hulls;
    }

    public LayoutPoint edgeAttachment(final ProjectedEndpointKey endpoint, final LayoutPoint toward) {
        ... unchanged ...
    }
    ... copyNodes, copyHulls unchanged ...
    equals/hashCode/toString compare only nodes and hulls
}
```

- [ ] **Step 1b: Remove the deleted engine from the performance diagnostic**

In `GraphWorkspacePerformanceDiagnostic.java`, delete the worker-side label timing (current lines 293-297) and the `LabelPlacementEngine` call so `workerGeometry` becomes the `workerHull` geometry, and delete the direct-probe label timing (current lines 354-358). Keep the `ACCEPTED_BATCH_FIRST_FRAME` timing and the `workerHullEnd < workerHullStart` guard; delete the `workerLabelEnd < workerLabelStart` half of the guard. Task 10 adds the `SEPARATION` and `PLACEMENT` measurements.

- [ ] **Step 2: Delete the old engine and its test**

Delete `geometry/LabelPlacementEngine.java` (560 lines), `geometry/LabelPlacement.java` (143 lines) and `geometry/LabelPlacementShould.java`. Do not leave an empty package behind: the `geometry` package keeps `GraphGeometry`, `GraphGeometryEngine`, `HullGeometry`, `HullIntersection`, `LayoutPoint`, `LayoutPositions`, `NodeGeometry`, `AwtGeometryTextMetrics`, `GeometryTextMetrics`.

- [ ] **Step 3: Remove `LabelAssembler` from `LayoutSettleLoop`**

In `LayoutSettleLoop.java`:

1. Delete the `private final LabelAssembler labels;` field (line 57) and its initialization `this.labels = new LabelAssembler();` (line 112).
2. Replace the normal publication call (current lines 543-544) with:

```java
            final GraphGeometry geometry =
                geometryEngine.computeHulls(run.projection, frame.positions(), metrics);
```

3. Replace the failure publication call (current lines 721-722) with:

```java
            final GraphGeometry geometry =
                geometryEngine.computeHulls(run.projection, failed.positions(), metrics);
```

4. Delete the `LabelAssembler` class (current lines 982-990) and the now-unused `GraphGeometry` import if nothing else in the file uses it (the two replacements above still use `GraphGeometry`, so keep the import).

Hull computation stays at both sites; no label placement runs at layout time any more.

- [ ] **Step 4: Migrate `GraphWorkspaceModelAcceptanceShould`**

Delete the `LabelPlacement` and `LabelPlacementEngine` imports (lines 71-72) and replace the `labelsFor` helper (current lines 1043-1049) and its assertions (current lines 293-298) with placement-driven equivalents:

```java
    private static List<PlacedLabel> labelsFor(final GraphProjection projection) {
        AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(new AffineTransform(), false, false));
        GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positionsFor(projection), metrics);
        LabelPlacementRequest request = LabelPlacementRequest.of(projection, geometry,
            positionsFor(projection), 1.0, 0.0, 0.0,
            new java.awt.geom.Rectangle2D.Double(0.0, 0.0, 1128.0, 364.0),
            Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL);
        return new ScreenLabelPlacement().place(request, null,
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)),
            Collections.<java.awt.geom.Rectangle2D>emptyList());
    }
```

and the assertions with:

```java
        List<PlacedLabel> labels = labelsFor(projection);

        assertThat(labels).extracting(PlacedLabel::text)
            .containsExactlyInAnyOrder("Map fixture", "Second root");
        assertThat(labels).allMatch(label ->
            label.mode() != PlacedLabel.Mode.HOVER_ONLY || label.emphaticAtAnchor());
        for (int first = 0; first < labels.size(); first++) {
            for (int second = first + 1; second < labels.size(); second++) {
                assertThat(labels.get(first).bounds().intersects(labels.get(second).bounds())).isFalse();
            }
        }
```

The old assertions pinned the deleted engine's `INTERIOR` modes; the replacement pins the observable I2 property (both enclosure labels are returned and pairwise disjoint) plus the texts, which is the requirement the acceptance test exists to protect. Add the imports `org.freeplane.plugin.graph.canvas.LabelFonts`, `LabelPlacementRequest`, `PlacedLabel`, `RenderingLevel`, `ScreenLabelPlacement`, `org.freeplane.plugin.graph.projection.ProjectedEndpointKey`, `org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme`.

- [ ] **Step 5: Migrate the remaining 3-argument `GraphGeometry.of` call sites**

Run the compiler and fix every remaining 3-argument call:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:compileTestJava -PTestLoggingFull
```

For each error: `GraphGeometry.of(nodes, hulls, labels)` becomes `GraphGeometry.of(nodes, hulls)`; a `labels` local that becomes unused is deleted; an assertion on `geometry().labels()` is rewritten against the new placement result or deleted with the coverage moved to `ScreenLabelPlacementShould` named in the report. The known sites are `GraphInteractionControllerShould.java:588`, `GraphWorkspaceWindowModelShould.java:1911`, `WorkspaceDialogsShould.java:440` and `:483`, and any remaining `GraphCanvasPaintShould` site from Task 8. Do not add a 2-argument/3-argument compatibility overload.

- [ ] **Step 6: Run the acceptance-5 removal check**

Run:

```bash
grep -rnE "\bLabelPlacement\b|\bLabelPlacementEngine\b" freeplane_plugin_graph/src --include=*.java
grep -rn "GraphGeometry.of(.*,.*,.*)" freeplane_plugin_graph/src --include=*.java
grep -rn "REST_LENGTH\|MAX_INTERIOR_EXPANSION" freeplane_plugin_graph/src --include=*.java
grep -rn "\.labels()" freeplane_plugin_graph/src --include=*.java
```

Expected: all four commands print nothing (exit status 1). The new names `LabelPlacementRequest`, `ScreenLabelPlacement`, `ScreenLabelPlacementCache` and `PlacedLabel` are expected and are not matched because of the word boundaries; the 3-argument pattern must not match the new `GraphGeometry.of` call in `GraphGeometry.java` (which has two parameters) or `LabelPlacementRequest.of` (a different type). If a grep matches, fix the call site; never add a compatibility overload. Also run the migration check on the main sources only:

```bash
grep -rnE "\bLabelPlacement\b|\bLabelPlacementEngine\b" freeplane_plugin_graph/src/main/java --include=*.java
```

- [ ] **Step 7: Run the full module suite**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: BUILD SUCCESSFUL with every suite green. The removed `LabelPlacementShould` suite is gone; its required behaviour is covered by `ScreenLabelPlacementShould` (§5.4-§5.10). Note the suite total in the task report and stop if any unrelated suite fails.

- [ ] **Step 8: Commit**

```bash
git add -A freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph
git commit -m "Delete the old label engine and its geometry label surface [2026-09-12-graph-node-separation]"
```

## Task 10: Add the separation baseline, the placement stage and fixture golden hashes

**Implementer tier:** Advanced
**Lane:** integration
**Depends on:** Tasks 2, 3, 8, 9

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java:27-39`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java:62-77`, `:120-147`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java` (SEPARATION and PLACEMENT measurement plus per-trigger sampling)

**Interfaces:**
- Consumes: `NodeSeparationProjection.project(GraphProjection, LayoutPositions, Set<ProjectedNodeKey>)`; `LayoutRequest.pins()`, `PinProjection.active()`/`projectedNode()`; `ScreenLabelPlacementCache.place(LabelPlacementRequest, LabelFonts)`; `LabelPlacementRequest.of(...)`; `LabelFonts.from(GraphTheme)`; `GraphTheme.resolve(DisplaySettings.CanvasTheme)`; `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)`; `PerformanceMeasurements.Stage`, `PerformanceMeasurements(String scenario, int warmupCount, int measuredCount)`, `recordWarmup(Stage, long)`, `recordMeasured(Stage, long)`, `toCsv()`; `GeneratedWorkspace.writeFixtures(Path)`, `GeneratedWorkspace.Scenario`; the diagnostic's `clock`, `measurements`, `textMetrics` and `repaintBounded()` members.
- Produces: `PerformanceMeasurements.Stage.SEPARATION("separation")` after `CORRECTION` and `Stage.PLACEMENT("placement")` replacing `LABEL("label")` at the same position; per-trigger `placement` rows in the diagnostic CSV (`warmupCount=20`, `measuredCount=100`, threshold `-1`); a recorded `separation` baseline row; the golden SHA-256 constants in `PerformanceTripwiresShould`.

- [ ] **Step 1: Move the stage enum**

In `PerformanceMeasurements.java`, change the enum body (current lines 27-39) to:

```java
    public enum Stage {
        SNAPSHOT("snapshot"),
        PROJECTION("projection"),
        DIFF("diff"),
        MUTATION("mutation"),
        FORCE("force"),
        CORRECTION("correction"),
        SEPARATION("separation"),
        HULL("hull"),
        PLACEMENT("placement"),
        FULL_WORKER("full-worker"),
        EDT_SWAP("edt-swap"),
        REPAINT("repaint"),
        ACCEPTED_BATCH_FIRST_FRAME("accepted-batch-first-frame");
```

`SEPARATION` is the G7 name; `PLACEMENT` replaces `LABEL` at the same enum position, so `Stage.names()` order changes by exactly those two entries. Do not change `normalThresholdNanos`, `strictThresholdNanos` or any threshold constant: both new stages stay diagnostic-only until a baseline is recorded.

- [ ] **Step 2: Update the stage-list tripwire**

In `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds` (current lines 62-77), replace the `containsExactly` list (lines 64-66) with:

```java
        assertThat(PerformanceMeasurements.Stage.names()).containsExactly(
            "snapshot", "projection", "diff", "mutation", "force", "correction", "separation",
            "hull", "placement", "full-worker", "edt-swap", "repaint", "accepted-batch-first-frame");
```

Leave the threshold assertions below it unchanged.

- [ ] **Step 3: Record the separation baseline**

In `GraphWorkspacePerformanceDiagnostic.java`, in `runDirectProbe` after the `CORRECTION` timing (current lines 342-347) and before the `HULL` timing, insert:

```java
            final long separationStart = clock.nanoTime();
            final NodeSeparationResult separation = new NodeSeparationProjection().project(projection,
                corrected.positions(), pinnedNodes(request.pins()));
            final long separationEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.SEPARATION, separationStart,
                separationEnd, warmup);
```

and pass `separation.positions()` to the subsequent hull computation instead of `corrected.positions()` so the hull stage measures the published positions. Add the helper:

```java
    private static Set<ProjectedNodeKey> pinnedNodes(final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active() && pin.projectedNode().isPresent()) {
                pinned.add(pin.projectedNode().get());
            }
        }
        return pinned;
    }
```

- [ ] **Step 4: Add the C16 placement sampling**

Add a placement measurement call to `runSample` after `runDirectProbe(...)` and before `return current;`:

```java
        measurePlacement(current, workerHull, workerFrame.positions(), warmup);
```

Add these methods to the diagnostic:

```java
    private void measurePlacement(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final boolean warmup) {
        final LabelFonts fonts = LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
        final java.awt.geom.Rectangle2D area =
            new java.awt.geom.Rectangle2D.Double(0.0, 0.0, 1128.0, 364.0);
        final Set<ProjectedEndpointKey> noPins = Collections.<ProjectedEndpointKey>emptySet();
        measurePlacementTrigger("positions-identity", projection, geometry, positions, noPins, fonts, area,
            warmup, 0);
        measurePlacementTrigger("zoom", projection, geometry, positions, noPins, fonts, area, warmup, 1);
        measurePlacementTrigger("viewport", projection, geometry, positions, noPins, fonts, area, warmup, 2);
        measurePlacementTrigger("forced-set", projection, geometry, positions, noPins, fonts, area, warmup, 3);
    }

    private void measurePlacementTrigger(final String trigger, final GraphProjection projection,
            final GraphGeometry geometry, final LayoutPositions positions,
            final Set<ProjectedEndpointKey> forced, final LabelFonts fonts,
            final java.awt.geom.Rectangle2D area, final boolean warmup, final int triggerIndex) {
        if (warmup) {
            return;
        }
        final ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        final PerformanceMeasurements triggerMeasurements = new PerformanceMeasurements(
            scenarioSuffix(trigger), WARMUP_SAMPLES, TIMED_SAMPLES);
        for (int sample = 0; sample < WARMUP_SAMPLES + TIMED_SAMPLES; sample++) {
            final boolean timed = sample >= WARMUP_SAMPLES;
            final double zoom = triggerIndex == 1 ? 1.0 + sample * 1e-4 : 1.0;
            final double centerX = triggerIndex == 2 ? sample * 0.5 : 0.0;
            final double centerY = triggerIndex == 2 ? -sample * 0.25 : 0.0;
            final LayoutPositions triggerPositions = triggerIndex == 0
                ? copyPositions(positions) : positions;
            final Set<ProjectedEndpointKey> triggerForced = triggerIndex == 3
                ? forcedForSample(projection, sample) : forced;
            final LabelPlacementRequest request = LabelPlacementRequest.of(projection, geometry,
                triggerPositions, zoom, centerX, centerY, area, triggerForced, RenderingLevel.FULL);
            final long start = clock.nanoTime();
            cache.place(request, fonts);
            final long end = clock.nanoTime();
            if (timed) {
                triggerMeasurements.recordMeasured(PerformanceMeasurements.Stage.PLACEMENT, end - start);
            }
            else {
                triggerMeasurements.recordWarmup(PerformanceMeasurements.Stage.PLACEMENT, end - start);
            }
        }
        measurements.recordMeasured(PerformanceMeasurements.Stage.PLACEMENT, triggerP95(triggerMeasurements));
    }
```

with `private static final int WARMUP_SAMPLES = 20;`, `private static final int TIMED_SAMPLES = 100;`, a `scenarioSuffix(trigger)` returning the run scenario plus `":placement-" + trigger`, `copyPositions(LayoutPositions)` returning a new `LayoutPositions` with the same maps (positions-identity trigger), `forcedForSample(GraphProjection, int)` returning alternating deterministic forced sets drawn from the projection's node keys, and `triggerP95(PerformanceMeasurements)` reading the nearest-rank p95 the instance computed. The four trigger names, the 20 warm-ups, the 100 timed samples, the cold-key-with-retained-`previous` cache state and the nearest-rank p95 are the pinned C16 method; the measured rows are diagnostic-only, so no threshold is set.

- [ ] **Step 5: Record the fixture golden hashes**

In `PerformanceTripwiresShould.serializeCorrectedVisibleLeafLabelsIntoFixtureBytes` (current lines 120-147), keep the `historicalHashes` map and the `isNotEqualTo` assertion, and add a current-bytes golden assertion inside the loop:

```java
            assertThat(sha256(bytes)).isEqualTo(currentHashes.get(fixture.getKey()));
```

Declare the map next to `historicalHashes`:

```java
        Map<String, String> currentHashes = new LinkedHashMap<String, String>();
        currentHashes.put("two-map.fpg", "<recorded>");
        currentHashes.put("three-map.fpg", "<recorded>");
        currentHashes.put("reference-2000-5000.fpg", "<recorded>");
```

Run the test once; it fails with the actual SHA-256 for each fixture, which is the recorded value because the change does not touch the persisted format. Insert the three observed digests and re-run. If a digest differs from the pre-change bytes, stop and report: acceptance 4 requires that no workspace XML is written by the change.

- [ ] **Step 6: Run the performance gates and record the baselines**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:graphPerformanceDiagnostic
```

Expected: BUILD SUCCESSFUL; `freeplane_plugin_graph/build/graph-performance/` contains CSV rows for `separation` (one row per scenario, diagnostic-only, threshold `-1`) and for `placement` from each of the four triggers with `warmupCount=20`, `measuredCount=100`, threshold `-1`. Record the observed `separation` and `placement` p95 values in the task report as the C7/C15 baselines; do not set a threshold.

Then run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.performance.*" -PTestLoggingFull
```

Expected: PASS with the stage-list tripwire and the golden hashes green.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance
git commit -m "Record the separation baseline, the placement stage and fixture hashes [2026-09-12-graph-node-separation]"
```

## Task 11: Run the acceptance sweep and close the verification contract

**Implementer tier:** Advanced
**Lane:** integration
**Depends on:** Tasks 1-10

**Files:**
- No file changes. This task produces the evidence report only; if a check fails, stop and report the failure instead of editing production code in this task.

**Interfaces:**
- Consumes: every deliverable of Tasks 1-10; the module suite `:freeplane_plugin_graph:test`; the repository suite `test`; the four removal greps of Task 9; the golden fixture hashes of Task 10.
- Produces: the acceptance report mapping specification §7.1-§7.8 to the exact test names and observed results, the module and repository suite counts, and the removal and persistence evidence.

- [ ] **Step 1: Run the module suite**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: BUILD SUCCESSFUL, every suite green. Record the total test count and the new suites: `NodeSeparationProjectionShould` 9 tests, `ScreenLabelPlacementShould` 21 tests, the new tests in `LayoutWorkerShould` (3), `LayoutSettleLoopShould` (1), `GraphUpdateCoordinatorShould` (1), `MapTierCorrectionShould` (1), and the migrated `GraphCanvasPaintShould` (the renamed label test plus `keepsPublishedGeometryStableAcrossPlacementChanges`). If a suite count differs from the plan, report the observed count; do not edit assertions to match the plan.

- [ ] **Step 2: Run the repository suite**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle test -PTestLoggingFull
```

Expected: BUILD SUCCESSFUL with every module green, including `freeplane_plugin_graph`. Record the total. If an out-of-module suite fails, report it as a finding; do not fix it in this task.

- [ ] **Step 3: Re-run the acceptance-5 removal greps**

Run:

```bash
grep -rnE "\bLabelPlacement\b|\bLabelPlacementEngine\b" freeplane_plugin_graph/src --include=*.java
grep -rn "GraphGeometry.of(.*,.*,.*)" freeplane_plugin_graph/src --include=*.java
grep -rn "REST_LENGTH\|MAX_INTERIOR_EXPANSION" freeplane_plugin_graph/src --include=*.java
grep -rn "\.labels()" freeplane_plugin_graph/src --include=*.java
```

Expected: all four print nothing. `LabelPlacementRequest`, `ScreenLabelPlacement`, `ScreenLabelPlacementCache` and `PlacedLabel` are expected names and must not be matched.

- [ ] **Step 4: Check the persistence and documentation scope**

Run:

```bash
git diff --exit-code -- docs/superpowers/specs
git diff --exit-code -- freeplane_plugin_graph/build.gradle
git diff --name-only
git status --porcelain
```

Expected: `docs/superpowers/specs` (specification, design, mockups including `FixtureProbe.java` and `NodeSeparationMockups.java`, and images), `freeplane_plugin_graph/build.gradle` and every `Resources_*.properties` are byte-identical; the changed-file list contains only the production and test files named in Global Constraints. Report any file outside that allowlist as a scope finding.

- [ ] **Step 5: Map the acceptance criteria and record the evidence**

Produce a report section with exactly this mapping, quoting the observed result of each item:

- §7.1 -> `NodeSeparationProjectionShould.separatesTwoProminentNodesAndClearsTheResidual`, `reportsTheNonConvergingSandwichAtThePassCap`, `leavesPinnedPinnedOverlapUntouchedAndCounted`, `convergesASolvablePairToTheMinimumDistance`, `separatesCoincidentParticlesDeterministically` (I1, pass counts, residual).
- §7.2 -> `LayoutWorkerShould.projectsAcceptedFramesAndPublishesTheRecomputedResidual`, `LayoutWorkerShould.carriesTheRetainedResidualIntoAFailedFrame`, `LayoutSettleLoopShould.publishesAFallbackFrameWithARecomputedResidual`, `GraphUpdateCoordinatorShould.loadsAnEmptyInitialFrameWithAZeroResidual` (verification closure).
- §7.3 -> `ScreenLabelPlacementShould` tests for §5.4-§5.10, `GraphCanvasPaintShould.keepsPublishedGeometryStableAcrossPlacementChanges` (I2/I3/I4 and the O4 exception).
- §7.4 -> `PerformanceTripwiresShould.serializeCorrectedVisibleLeafLabelsIntoFixtureBytes` with the golden hashes (no persisted-format change).
- §7.5 -> the four greps of Step 3 (removal).
- §7.6 -> `TypedForcesShould.twoMapWorkspaceSettlesToIdle` with the re-recorded `TWO_MAP_FIRST_IDLE_STEP` and the 100 stable frames (settle/idle).
- §7.7 -> the `separation` and `placement` rows of `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` with thresholds `-1` and the recorded baselines (performance).
- §7.8 -> Steps 1 and 2 (build).

- [ ] **Step 6: Commit the report and the acceptance state**

```bash
git status --porcelain
git log --oneline -12
```

Expected: the tree is clean after the Task 10 commit, or contains only the report artifacts the controller asked for. Do not create a commit in this task unless a file changed; if nothing changed, report "no commit: verification-only task".

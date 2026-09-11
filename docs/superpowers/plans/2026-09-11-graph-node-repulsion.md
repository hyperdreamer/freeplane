# Graph Node Repulsion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

**Goal:** Make the Graph Workspace layout reach the perceptual idle state for the unpinned relationship-free workspace class and for moderate multi-map workspaces, and keep nested parent/child/grandchild boundaries excluded from boundary repulsion.

**Architecture:** Three localized changes in `org.freeplane.plugin.graph.layout.graphstream`: `TypedSpringBox` gains `isAnchorParticle` and `TypedNodeParticle.scaleRepulsion` skips the prominence radius scaling for anchor particles; `GraphStreamLayoutEngine` assigns each anchor's `parentAnchorId` in a second topology phase and passes it through `configureParticle`; `TypedSpringBox` records `parentOf`, removes it in `forgetParticle`, and `addBoundaryRepulsion` skips ancestor pairs before the zero-distance fallback. Ten regression tests in one sequential lane prove the momentum symmetry, the settled proximity bounds, the pin and unpin transitions, the reparenting refresh, the two-map idle behavior, and the unchanged existing suites.

**Tech Stack:** Java 8 (class major 52), GraphStream 1.3 `SpringBox` layout, JUnit 4 with AssertJ, Gradle (`:freeplane_plugin_graph:test`), Freeplane OSGi plugin `freeplane_plugin_graph`.

Requirement coverage: Task 1 implements `R1`-`R3` and test `T2`; Task 2 implements `R4`-`R13` and `R15`-`R19` and tests `T3`, `T4`, `T8`, `T9`; Task 3 proves tests `T1`, `T5`, `T6`; Task 4 proves tests `T7`, `T10`, `R23`, `V1`-`V4` and `R14`; `R20`-`R22` are the assertion contracts of `T2`, `T4`, `T6`.

## Global Constraints

- Java 8 source and bytecode target (class major version 52); do not use APIs newer than Java 8.
- Encoding UTF-8; four-space indentation; `final` parameters, matching the surrounding `org.freeplane.plugin.graph` code style.
- JUnit 4 (`org.junit.Test`) with AssertJ assertions; public `void` test methods; `*Should` class names; all new tests live in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/`; append new test methods and helper methods; never modify or delete an existing test method or assertion.
- All production changes are confined to package `org.freeplane.plugin.graph.layout.graphstream`, and exactly three production files may change: `TypedNodeParticle.java`, `TypedSpringBox.java`, `GraphStreamLayoutEngine.java`. No other main source file may change. The only test files that may change are `TypedForcesShould.java`, `BoundarySeparationShould.java`, and the new `ReferenceRepulsionFixture.java`.
- The reference fixture is binding: workspace `00000000-0000-0000-0000-0000000000aa`; single map `00000000-0000-0000-0000-000000000001`; map name `"M"`; generation `1L`; no edges; no pins; nodes in list order `[n1, n2, n3, n4]` labelled `"Fundation / Regularity"`, `"Replacement Scheme"`, `"Axiom of Choice"`, `"Theorem"`, each `graphGroup == true`; enclosures in list order `[root, zfc, axioms, definitions]` labelled `"Axiomatic Set Theory"` (`SUPPRESSED`, map root, no parent, children `[zfc]`), `"ZFC"` (`EMPHATIC`, parent `root`, children `[axioms, definitions]`), `"Axioms"` (`SUBTLE`, parent `zfc`, direct nodes `[n1, n2, n3]`), `"Basic Definitions and Theorems"` (`SUBTLE`, parent `zfc`, direct node `[n4]`); persisted endpoint node ids `root`, `zfc`, `axioms`, `defs`.
- The reparented fixture uses generation `2L`, the same nodes and the same enclosure list order, with `zfc.directEnclosures = [axioms]`, `axioms.directEnclosures = [definitions]`, and `definitions.parentHull = axioms`.
- The two-map fixture uses workspace `00000000-0000-0000-0000-0000000000aa`, maps `00000000-0000-0000-0000-000000000001` (A) and `00000000-0000-0000-0000-000000000002` (B), generation `1L`, 24 nodes and 8 anchors; map A root `"Axiomatic Set Theory"` (`SUPPRESSED`), map B root `"Topology"` (`EMPHATIC`); sub-boundaries `"A-sub 0"`-`"A-sub 2"` and `"B-sub 0"`-`"B-sub 2"` (`SUBTLE`) each holding four nodes `a{s}_{n}`/`b{s}_{n}` labelled `"Boundary {s} node {n}"`; enclosure list order `[a-sub-0, a-sub-1, a-sub-2, a-root, b-sub-0, b-sub-1, b-sub-2, b-root]`; four `FORWARD` cross-map `GraphRelationshipRecord`s with ids `20000000-0000-0000-0000-00000000000{i+1}`, sequence `i + 1`, from `a0_{i}` to `b0_{i}` for `i = 0..3`, projected through `EdgeContributor.graphRelationship`.
- Exact measured bounds and values: `boundaryRadius(root) ≈ boundaryRadius(zfc) ≈ 1441.73`, `boundaryRadius(axioms) ≈ 593.87`, `boundaryRadius(definitions) ≈ 117.45`; T1 first idle step 457 (rms 0.0472, max 0.0939); T2 momentum fixed `9.9e-14` versus unfixed `6.55` (third interval fixed `1.0e-13`, unfixed `0.54`); T3 settled root-zfc `100.23`, zfc-axioms `355.37`, zfc-definitions `355.11`, bound `1441.73 - 16 = 1425.73`, unfixed `2810.58 / 1984.77 / 1522.81`; T4 settled axioms-definitions `710.48`, contact `719.32`, maximum pairwise bound `1000.0`, unfixed `3485.41 / 3814.17`; T5 first idle 2471 (rms `0.049654`, guard bounds `0.0505` and `0.10`); T6 first idle 292 steps after unpin, formerly pinned node `108.97` units from `(1059, -145)`, assertion bound `> 10.0`; T7 refreshed pair `61.84`, stale pair `699.88`, bound `577.87`; T8 fixed `455.60`, direct-parent-only `≈ 2025`, full-walk-removed `2043.08`; T9 fixed `100.23`; T10 first idle 419 (rms 0.0468, max 0.0648), baseline rms ≈ `0.24`.
- Idle thresholds are unchanged: `PerceptualIdlePolicy.spikeDefaults()` = 8 consecutive frames, RMS `0.05`, max `0.10`.
- Layout constants are unchanged: `K2 = 16.0` (`REPULSION_FACTOR`), `force = 1`, `ATTRACTION_FACTOR = 0.05`, `BOUNDARY_REPULSION_FACTOR = 0.5`, `REST_LENGTH = 24.0`, `BASE_SEPARATION_RADIUS = 8.0`, `CROSS_MAP_DISPLACEMENT_LIMIT = 0.005`, `LayoutCalibration` containment `0.15` / hierarchy `0.30` / sameMap `1.0`, `setQuality(0.10)` with `nodesPerCell = 10`, node-prominence scaling for node particles (`NodeProminence.MAX_SCALE = 1.75`), typed attraction, pin freezing, `Seeds` placement, `BoundarySizes`, `MapTierCorrection`, `GraphGeometry`, and the `GraphStreamLayoutEngine.apply` empty-request fast path.
- No system-property switches, runtime flags, or new public APIs; the three changes are unconditional.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and the repository `gradle` (never `gradlew` or Maven); add `-PTestLoggingFull` for verbose failures.
- Verification contract `V1`-`V4`: `gradle :freeplane_plugin_graph:test` must pass; the existing 88 layout/settle-loop tests stay green (`TypedForcesShould` 13, `BoundarySeparationShould` 7, `GraphStreamBoundaryShould` 5, `LayoutWorkerShould` 13, `MapTierCorrectionShould` 7, `PerceptualIdlePolicyShould` 5, `LayoutSettleLoopShould` 38) and the 10 new tests bring the counts to 16 / 14 and 98 total; no production class outside the three named files changes; no existing test method changes.
- Tests `T2`, `T3`, `T4` and `T9` are valid only on the relationship-free reference fixture (all node prominence scales exactly `1.0`); do not reuse them on fixtures with relationship edges.

## Task 1: Make anchor repulsion symmetric and add the shared reference fixture

**Implementer tier:** Standard
**Lane:** graph-repulsion

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:140-142`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java:84-95`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:289-323`

**Interfaces:**
- Consumes: the private `Map<String, Boolean> anchorFlags` of `TypedSpringBox`, written by `void configureParticle(String id, double radius, boolean pinned, boolean anchor)`; `double TypedSpringBox.baseSeparationRadius()`; `boolean TypedSpringBox.hasCrossMapLink(TypedNodeParticle particle)`; `String TypedNodeParticle.getId()`; `GraphProjection GraphProjection.projected(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<RelationshipResolution> relationshipResolutions, List<PinProjection> pins)`; `ProjectedNode ProjectedNode.of(ProjectedNodeKey key, SafeNodeLabel label, String mapName, boolean graphGroup)`; `ProjectedEnclosure ProjectedEnclosure.of(EnclosureHullKey hullKey, List<EnclosureKey> endpointKeys, List<SafeNodeLabel> labels, String mapName, Optional<EnclosureHullKey> parentHull, List<ProjectedNodeKey> directNodes, List<EnclosureHullKey> directEnclosures, boolean mapRoot, BoundaryTier boundaryTier)`; `SourceNodeKey SourceNodeKey.persisted(NodeReference reference)`; `NodeReference NodeReference.of(MapReferenceId map, PersistedNodeId nodeId)`; `PersistedNodeId PersistedNodeId.of(String value)`; `WorkspaceId WorkspaceId.of(String value)`; `MapReferenceId MapReferenceId.of(String value)`; the existing private helpers of `TypedForcesShould` (`ProjectedNodeKey key(MapReferenceId, String)`, `GraphProjection projection(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>)`).
- Produces: `boolean TypedSpringBox.isAnchorParticle(String id)`; the replaced `void TypedNodeParticle.scaleRepulsion(Vector3 before)`; `ReferenceRepulsionFixture` with `static final WorkspaceId WORKSPACE`, `static final MapReferenceId MAP`, `static final double CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING`, `SIBLING_GAP`, `FRAME_CLEARANCE`, `MAX_RENDERED_NODE_RADIUS`, `static ProjectedNodeKey regularityNode()`, `replacementNode()`, `choiceNode()`, `theoremNode()`, `static EnclosureHullKey rootHull()`, `zfcHull()`, `axiomsHull()`, `definitionsHull()`, `static GraphProjection referenceProjection(long generation)`, `static GraphProjection reparentedProjection(long generation)`, `static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull)`; `TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift()`.

- [ ] **Step 1: Create the shared reference fixture**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java` with exactly this content. The `Sizes` class is a test-local copy of the production `GraphStreamLayoutEngine.BoundarySizes` formulas (production `BoundarySizes` is not visible from the test package) and must not filter any tier.

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

final class ReferenceRepulsionFixture {
    static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
    static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
    static final double BOUNDARY_PADDING = 8.0;
    static final double SIBLING_GAP = 8.0;
    static final double FRAME_CLEARANCE = 16.0;
    static final double MAX_RENDERED_NODE_RADIUS = 14.0;

    private static final String NODE_REGULARITY = "n1";
    private static final String NODE_REPLACEMENT = "n2";
    private static final String NODE_CHOICE = "n3";
    private static final String NODE_THEOREM = "n4";
    private static final String HULL_ROOT = "root";
    private static final String HULL_ZFC = "zfc";
    private static final String HULL_AXIOMS = "axioms";
    private static final String HULL_DEFINITIONS = "defs";

    private ReferenceRepulsionFixture() {
    }

    static ProjectedNodeKey regularityNode() {
        return nodeKey(NODE_REGULARITY);
    }

    static ProjectedNodeKey replacementNode() {
        return nodeKey(NODE_REPLACEMENT);
    }

    static ProjectedNodeKey choiceNode() {
        return nodeKey(NODE_CHOICE);
    }

    static ProjectedNodeKey theoremNode() {
        return nodeKey(NODE_THEOREM);
    }

    static EnclosureHullKey rootHull() {
        return hull(HULL_ROOT);
    }

    static EnclosureHullKey zfcHull() {
        return hull(HULL_ZFC);
    }

    static EnclosureHullKey axiomsHull() {
        return hull(HULL_AXIOMS);
    }

    static EnclosureHullKey definitionsHull() {
        return hull(HULL_DEFINITIONS);
    }

    static GraphProjection referenceProjection(long generation) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(HULL_ROOT, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull()), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure(HULL_ZFC, "ZFC", Optional.of(rootHull()),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(axiomsHull(), definitionsHull()), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure(HULL_AXIOMS, "Axioms", Optional.of(zfcHull()),
            Arrays.asList(regularityNode(), replacementNode(), choiceNode()),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE));
        enclosures.add(enclosure(HULL_DEFINITIONS, "Basic Definitions and Theorems", Optional.of(zfcHull()),
            Collections.singletonList(theoremNode()), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        return projection(generation, referenceNodes(), enclosures);
    }

    static GraphProjection reparentedProjection(long generation) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(HULL_ROOT, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull()), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure(HULL_ZFC, "ZFC", Optional.of(rootHull()),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(axiomsHull()), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure(HULL_AXIOMS, "Axioms", Optional.of(zfcHull()),
            Arrays.asList(regularityNode(), replacementNode(), choiceNode()),
            Collections.singletonList(definitionsHull()), false, BoundaryTier.SUBTLE));
        enclosures.add(enclosure(HULL_DEFINITIONS, "Basic Definitions and Theorems", Optional.of(axiomsHull()),
            Collections.singletonList(theoremNode()), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        return projection(generation, referenceNodes(), enclosures);
    }

    static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull) {
        return new Sizes(projection).boundaryRadius(hull);
    }

    private static List<ProjectedNode> referenceNodes() {
        return Arrays.asList(node(NODE_REGULARITY, "Fundation / Regularity"),
            node(NODE_REPLACEMENT, "Replacement Scheme"), node(NODE_CHOICE, "Axiom of Choice"),
            node(NODE_THEOREM, "Theorem"));
    }

    private static ProjectedNode node(String id, String label) {
        return ProjectedNode.of(nodeKey(id), SafeNodeLabel.of(label, label), "M", true);
    }

    private static ProjectedEnclosure enclosure(String hullId, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> directNodes,
            List<EnclosureHullKey> directEnclosures, boolean mapRoot, BoundaryTier tier) {
        EnclosureHullKey hull = hull(hullId);
        return ProjectedEnclosure.of(hull, Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(hullId))))),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "M", parent, directNodes,
            directEnclosures, mapRoot, tier);
    }

    private static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures) {
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static ProjectedNodeKey nodeKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static final class Sizes {
        private final Map<ProjectedNodeKey, ProjectedNode> nodesByKey =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        private final Map<EnclosureHullKey, double[]> sizes =
            new LinkedHashMap<EnclosureHullKey, double[]>();

        Sizes(GraphProjection projection) {
            for (ProjectedNode node : projection.nodes()) {
                nodesByKey.put(node.key(), node);
            }
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                enclosuresByHull.put(enclosure.hullKey(), enclosure);
            }
        }

        double boundaryRadius(EnclosureHullKey hull) {
            double[] size = sizeOf(hull);
            return 0.5 * Math.hypot(size[0], size[1]);
        }

        private double directNodeRingRadius(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            int count = directNodes.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (ProjectedNodeKey nodeKey : directNodes) {
                double[] box = nodeBox(nodesByKey.get(nodeKey));
                maxWidth = Math.max(maxWidth, box[0]);
                maxHeight = Math.max(maxHeight, box[1]);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        private double directNodeReach(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            if (directNodes.isEmpty()) {
                return 0.0;
            }
            double maxNodeRadius = 0.0;
            for (ProjectedNodeKey nodeKey : directNodes) {
                double[] box = nodeBox(nodesByKey.get(nodeKey));
                maxNodeRadius = Math.max(maxNodeRadius, 0.5 * Math.hypot(box[0], box[1]));
            }
            return directNodeRingRadius(hull) + maxNodeRadius;
        }

        private double ringRadius(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<EnclosureHullKey> children = enclosure.directEnclosures();
            int count = children.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (EnclosureHullKey child : children) {
                double[] size = sizeOf(child);
                maxWidth = Math.max(maxWidth, size[0]);
                maxHeight = Math.max(maxHeight, size[1]);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        private double childBoundaryReach(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<EnclosureHullKey> children = enclosure.directEnclosures();
            if (children.isEmpty()) {
                return 0.0;
            }
            double reach = 0.0;
            double radius = ringRadius(hull);
            for (EnclosureHullKey child : children) {
                reach = Math.max(reach, radius + reachOf(child));
            }
            return reach;
        }

        private double reachOf(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                double[] size = sizeOf(hull);
                return 0.5 * Math.hypot(size[0], size[1]);
            }
            return Math.max(directNodeReach(hull), childBoundaryReach(hull));
        }

        private double[] sizeOf(EnclosureHullKey hull) {
            double[] cached = sizes.get(hull);
            if (cached != null) {
                return cached;
            }
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            double directReach = directNodeReach(hull);
            double childReach = childBoundaryReach(hull);
            double contentReach = Math.max(directReach, childReach);
            double[] size;
            if (contentReach == 0.0 && enclosure.directNodes().isEmpty()
                    && enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                size = new double[] {width, height};
            }
            else {
                double side = 2.0 * (contentReach + FRAME_CLEARANCE);
                size = new double[] {side, side};
            }
            sizes.put(hull, size);
            return size;
        }

        private static double[] nodeBox(ProjectedNode node) {
            double width = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS,
                node.label().displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
            double height = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS,
                CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
            return new double[] {width, height};
        }
    }
}
```

- [ ] **Step 2: Write the failing momentum test**

Add the import `java.util.Map` to the existing import block of `TypedForcesShould.java`.

Insert this test method after the last existing test method (after `observeDirectNodeContainmentInfluenceOnEnclosureAnchorCoordinates`, before the `private static LayoutFrame frameAfterSteps` helper):

```java
    @Test
    public void nativeRepulsionDisplacementsSumToZeroWithoutDrift() {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList()));
            LayoutFrame before = engine.step();
            LayoutFrame after = engine.step();
            // Pins, cross-map budgeting, and Barnes-Hut aggregation are deliberately
            // outside this assertion: the fixture has <= 10 particles (single leaf
            // cell, exact pairwise pass), no pins, no relationship edges, no
            // cross-map links, and all pairwise springs are symmetric.
            double sumDx = 0.0;
            double sumDy = 0.0;
            for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : before.positions().nodes().entrySet()) {
                LayoutPoint from = entry.getValue();
                LayoutPoint to = after.positions().nodes().get(entry.getKey());
                sumDx += to.x() - from.x();
                sumDy += to.y() - from.y();
            }
            for (Map.Entry<EnclosureHullKey, LayoutPoint> entry : before.positions().anchors().entrySet()) {
                LayoutPoint from = entry.getValue();
                LayoutPoint to = after.positions().anchors().get(entry.getKey());
                sumDx += to.x() - from.x();
                sumDy += to.y() - from.y();
            }
            assertThat(Math.hypot(sumDx, sumDy)).isLessThanOrEqualTo(1.0e-9);
        }
    }
```

- [ ] **Step 3: Run the new test and confirm it fails**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL on `assertThat(Math.hypot(sumDx, sumDy)).isLessThanOrEqualTo(1.0e-9)`; the unfixed second interval measures ≈ `6.55`, because anchor particles scale their total native repulsion by `separationRadius / 8.0` (≈ 180x for hull radii of order 10^3) and break Newton's third law. The first interval (`apply()` to `step1`) must not be used: `NodeParticle.move` clamps each particle independently to `box.area / 2` (≈ 1.414 during seeding), so its sum is 0.1725 with and without the fix.

- [ ] **Step 4: Write the minimal implementation**

In `TypedSpringBox.java`, insert this package-private method directly after `baseSeparationRadius()` and before `addBoundaryRepulsion`:

```java
    boolean isAnchorParticle(final String id) {
        final Boolean flag = anchorFlags.get(id);
        return flag != null && flag.booleanValue();
    }
```

In `TypedNodeParticle.java`, replace the whole current `scaleRepulsion` method:

```java
    private void scaleRepulsion(final Vector3 before) {
        final Vector3 repulsion = new Vector3(disp);
        repulsion.sub(before);
        if (separationRadius != typedBox.baseSeparationRadius()) {
            repulsion.scalarMult(separationRadius / typedBox.baseSeparationRadius());
            disp.copy(before);
            disp.add(repulsion);
        }
        if (typedBox.hasCrossMapLink(this)) {
            rawBudgetedRepulsion.add(repulsion);
        }
    }
```

with exactly:

```java
    private void scaleRepulsion(final Vector3 before) {
        final Vector3 repulsion = new Vector3(disp);
        repulsion.sub(before);
        if (!typedBox.isAnchorParticle(getId().toString())
                && separationRadius != typedBox.baseSeparationRadius()) {
            repulsion.scalarMult(separationRadius / typedBox.baseSeparationRadius());
            disp.copy(before);
            disp.add(repulsion);
        }
        if (typedBox.hasCrossMapLink(this)) {
            rawBudgetedRepulsion.add(repulsion);
        }
    }
```

The cross-map capture stays in place: `rawBudgetedRepulsion` accumulates exactly the `repulsion` vector the particle applies, so a cross-map-linked anchor now contributes its true native repulsion magnitude instead of the ≈ 180x scaled delta. The `CROSS_MAP_DISPLACEMENT_LIMIT = 0.005` cap and the once-per-particle budget contract are preserved. `isAnchorParticle` reads `anchorFlags`, which `configureParticle` writes during `synchronize()`; `scaleRepulsion` runs later in `step()`, so the flags are current and no invalidation logic is added.

- [ ] **Step 5: Run the focused tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift" -PTestLoggingFull --rerun-tasks
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.*" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. The momentum assertion is ≈ `9.9e-14` on the second interval (third interval ≈ `1.0e-13`). The layout package run passes 51 tests (50 existing plus this test); `TypedForcesShould` now reports 14 tests. No existing test method changed.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git commit -m "Keep native repulsion symmetric for anchor particles [2026-09-11-graph-node-repulsion]"
```

## Task 2: Exclude ancestor anchors from boundary repulsion

**Implementer tier:** Standard
**Lane:** graph-repulsion

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:138-146`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:204-216`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:786-800`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:29-30`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:52-58`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:73-76`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:144-179`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java:220-271`

**Interfaces:**
- Consumes: `ReferenceRepulsionFixture` from Task 1 with `static final WorkspaceId WORKSPACE`, `static ProjectedNodeKey regularityNode()`, `replacementNode()`, `choiceNode()`, `theoremNode()`, `static EnclosureHullKey rootHull()`, `zfcHull()`, `axiomsHull()`, `definitionsHull()`, `static GraphProjection referenceProjection(long generation)`, `static GraphProjection reparentedProjection(long generation)`, `static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull)`; `GraphStreamLayoutEngine.desired : LinkedHashMap<String, DesiredParticle>`, `GraphStreamLayoutEngine.anchorIds : Map<EnclosureHullKey, String>`, `ProjectedEnclosure.parentHull() : Optional<EnclosureHullKey>`; `boolean TypedSpringBox.isAnchorParticle(String id)` from Task 1; `MapTierCorrection`; the existing private helpers `LayoutFrame BoundarySeparationShould.settle(WorkspaceId workspace, GraphProjection projection)`, `void BoundarySeparationShould.assertNoSiblingOverlap(LayoutFrame frame, List<EnclosureHullKey> hulls, List<SafeNodeLabel> labels)`, `double BoundarySeparationShould.distance(LayoutPoint first, LayoutPoint second)`, `GraphProjection BoundarySeparationShould.projection(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>)`.
- Produces: `String GraphStreamLayoutEngine.DesiredParticle.parentAnchorId` (nullable, non-final); `void GraphStreamLayoutEngine.topology` second ancestry phase; `void TypedSpringBox.configureParticle(String id, double radius, boolean pinned, boolean anchor, String parentAnchorId)` (replacing the four-argument method); `void TypedSpringBox.forgetParticle(String id)` also removing `parentOf`; `boolean TypedSpringBox.isAncestorPair(String first, String second)`; `BoundarySeparationShould.directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling()`, `siblingAnchorsRemainSeparatedAfterSettling()`, `grandchildAnchorsAreExcludedFromBoundaryRepulsion()`, `coincidentParentChildAnchorsStayExcluded()`.

- [ ] **Step 1: Write the four failing tests**

Insert these four test methods into `BoundarySeparationShould.java` after the last existing test method (after `directNodesLieInsideTheirParentHullAfterGeometryComputation`, before the `private static ProjectedNode node` helper). No new imports are needed.

```java
    @Test
    public void directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling() {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        LayoutFrame frame = settle(ReferenceRepulsionFixture.WORKSPACE, projection);
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (!enclosure.parentHull().isPresent()) {
                continue;
            }
            EnclosureHullKey childHull = enclosure.hullKey();
            EnclosureHullKey parentHull = enclosure.parentHull().get();
            assertThat(distance(frame.positions().anchors().get(childHull),
                frame.positions().anchors().get(parentHull)))
                .as("distance from %s to parent %s", childHull, parentHull)
                .isLessThanOrEqualTo(
                    ReferenceRepulsionFixture.boundaryRadius(projection, parentHull) - 16.0);
        }
    }

    @Test
    public void siblingAnchorsRemainSeparatedAfterSettling() {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        LayoutFrame frame = settle(ReferenceRepulsionFixture.WORKSPACE, projection);
        assertNoSiblingOverlap(frame,
            Arrays.asList(ReferenceRepulsionFixture.axiomsHull(),
                ReferenceRepulsionFixture.definitionsHull()),
            Arrays.asList(SafeNodeLabel.of("Axioms", "Axioms"),
                SafeNodeLabel.of("Basic Definitions and Theorems", "Basic Definitions and Theorems")));
        List<EnclosureHullKey> hulls = Arrays.asList(ReferenceRepulsionFixture.rootHull(),
            ReferenceRepulsionFixture.zfcHull(), ReferenceRepulsionFixture.axiomsHull(),
            ReferenceRepulsionFixture.definitionsHull());
        double maximum = 0.0;
        for (int first = 0; first < hulls.size(); first++) {
            for (int second = first + 1; second < hulls.size(); second++) {
                maximum = Math.max(maximum, distance(frame.positions().anchors().get(hulls.get(first)),
                    frame.positions().anchors().get(hulls.get(second))));
            }
        }
        assertThat(maximum).isLessThanOrEqualTo(1000.0);
    }

    @Test
    public void grandchildAnchorsAreExcludedFromBoundaryRepulsion() {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        LayoutFrame frame = settle(ReferenceRepulsionFixture.WORKSPACE, projection);
        assertThat(distance(frame.positions().anchors().get(ReferenceRepulsionFixture.rootHull()),
            frame.positions().anchors().get(ReferenceRepulsionFixture.axiomsHull()))).isLessThan(1000.0);
    }

    @Test
    public void coincidentParentChildAnchorsStayExcluded() {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        LayoutFrame frame = settle(ReferenceRepulsionFixture.WORKSPACE, projection);
        assertThat(distance(frame.positions().anchors().get(ReferenceRepulsionFixture.rootHull()),
            frame.positions().anchors().get(ReferenceRepulsionFixture.zfcHull())))
            .isLessThanOrEqualTo(ReferenceRepulsionFixture.boundaryRadius(projection,
                ReferenceRepulsionFixture.rootHull()) - 16.0);
    }
```

- [ ] **Step 2: Run the new tests and confirm they fail**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL for all four new tests. With Task 1 in place but no ancestor exclusion, `addBoundaryRepulsion` still pushes nested hulls apart: root-zfc ≈ `2810.58` > `1425.73` (T3 and T9 fail), zfc-axioms ≈ `1984.77` > `1425.73`, zfc-definitions ≈ `1522.81` > `1425.73`, axioms-definitions ≈ `3485.41` with maximum pairwise anchor distance ≈ `3814.17` > `1000` (T4 fails), root-axioms ≈ `2043.08` >= `1000` (T8 fails). The unfixed T9 root-zfc pair seeds at the same point because the root has a single direct enclosure so `Seeds.center` yields `ringRadius = 0`; the zero-distance fallback then resolves the coincident pair by pushing it apart.

- [ ] **Step 3: Write the minimal implementation**

In `GraphStreamLayoutEngine.java`, in `synchronize()`, replace the four-argument call:

```java
            springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null);
```

with:

```java
            springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null,
                desired.parentAnchorId);
```

In `GraphStreamLayoutEngine.topology(...)`, insert this second loop immediately after the existing anchor-creation loop and before `final List<ForceLink> result = new ArrayList<ForceLink>();`:

```java
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final DesiredParticle particle = desired.get(anchorIds.get(enclosure.hullKey()));
            particle.parentAnchorId = anchorIds.get(enclosure.parentHull().orElse(null));
        }
```

Phase 2 strictly follows phase 1, so `anchorIds` already contains every hull. `anchorIds.get(null)` returns `null`, so map roots and any enclosure with an empty `parentHull()` receive `null`; a `parentHull()` absent from the projection's enclosure list also yields `null` and must not throw. The second loop only mutates `DesiredParticle` fields and does not insert, remove, or reorder entries of `desired`. Link construction, `enclosureDepths`, `hierarchyRestLength`, and `addHierarchyLink` are untouched.

In `GraphStreamLayoutEngine.java`, in `DesiredParticle`, add the nullable non-final field directly after `radius`:

```java
        private final EnclosureHullKey anchorKey;
        private final double radius;
        private String parentAnchorId;
```

The constructor and the `node(...)`/`anchor(...)` factory signatures must not change; node particles never receive a non-null value.

In `TypedSpringBox.java`, add this field directly after the existing `anchorFlags` field:

```java
    private final Map<String, String> parentOf = new LinkedHashMap<String, String>();
```

Null values are permitted; an absent key and a null value are equivalent to "no parent".

In `TypedSpringBox.java`, replace the whole `configureParticle` method:

```java
    void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor) {
```

with:

```java
    void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor,
            final String parentAnchorId) {
```

and add `parentOf.put(id, parentAnchorId);` as the first statement of the body. The rest of the body (`anchorFlags.put`, the particle lookup, `particle.configure`, `freezeNode`) is unchanged:

```java
    void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor,
            final String parentAnchorId) {
        parentOf.put(id, parentAnchorId);
        anchorFlags.put(id, Boolean.valueOf(anchor));
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle != null) {
            particle.configure(radius, pinned);
            freezeNode(id, pinned);
        }
    }
```

In `TypedSpringBox.java`, replace `forgetParticle`:

```java
    void forgetParticle(final String id) {
        anchorFlags.remove(id);
        typedParticles.remove(id);
    }
```

with:

```java
    void forgetParticle(final String id) {
        anchorFlags.remove(id);
        parentOf.remove(id);
        typedParticles.remove(id);
    }
```

`clearLinks()` must not clear `parentOf`: in `synchronize()` the per-particle `configureParticle` loop runs before `replaceLinks(...)` (which calls `clearLinks()`), so clearing `parentOf` there would discard the ancestry written moments earlier. `parentOf` is owned exclusively by `configureParticle` and `forgetParticle`.

In `TypedSpringBox.addBoundaryRepulsion`, after the existing self-skip:

```java
            if (other == particle) {
                continue;
            }
```

insert:

```java
            if (isAncestorPair(particle.getId().toString(), other.getId().toString())) {
                continue;
            }
```

The skip is applied before the zero-distance fallback and before the penetration computation, so a coincident parent/child pair is excluded instead of being resolved by the fallback direction. Nothing else in `addBoundaryRepulsion` changes: the `extent` formula (`boundaryRadius + boundaryRadius + SIBLING_GAP`), the `penetration <= 0.0` early-out, the direction vector, and `BOUNDARY_REPULSION_FACTOR = 0.5` are untouched.

In `TypedSpringBox.java`, add this private method at the end of the class, after `addBoundaryRepulsion`:

```java
    private boolean isAncestorPair(final String first, final String second) {
        for (String current = parentOf.get(first); current != null; current = parentOf.get(current)) {
            if (current.equals(second)) {
                return true;
            }
        }
        for (String current = parentOf.get(second); current != null; current = parentOf.get(current)) {
            if (current.equals(first)) {
                return true;
            }
        }
        return false;
    }
```

It returns `true` iff `first` is an ancestor of `second` or `second` is an ancestor of `first`, covering parent-child and grandparent-grandchild at every depth. A null value or an absent key terminates a walk; no recursion, no exceptions, and no cycle detection (a cyclic chain already fails earlier in `GraphStreamLayoutEngine.enclosureDepths`).

- [ ] **Step 4: Run the focused tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.*" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. `BoundarySeparationShould` reports 11 tests (7 existing plus the four new) and the layout package reports 55 tests. The four new tests measure root-zfc `100.23` (bound `1425.73`), zfc-axioms `355.37`, zfc-definitions `355.11`, axioms-definitions `710.48` (contact ≈ `719.32`), maximum pairwise anchor distance ≤ `1000.0`, and root-axioms `455.60 < 1000.0`. `forgetParticle` hygiene (`R10`/`R17`) keeps `parentOf` bounded by the live particle set, and the existing `TypedForcesShould.returnToBaselineCoverageAfterOneHundredAddRemoveCycles` exercises the configure/forget path unchanged.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "Exclude ancestor anchors from boundary repulsion [2026-09-11-graph-node-repulsion]"
```

## Task 3: Prove pin, unpin, and nested-settle idle behavior

**Implementer tier:** Standard
**Lane:** graph-repulsion

**Files:**
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:289-323`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java:220-271`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java:84-95` (temporary falsifiability probe only; restored before commit)

**Interfaces:**
- Consumes: `ReferenceRepulsionFixture` from Task 1 with `static final WorkspaceId WORKSPACE`, `static ProjectedNodeKey replacementNode()`, `static GraphProjection referenceProjection(long generation)`; `LayoutWorker(LayoutCalibration calibration)`; `CompletionStage<LayoutFrame> LayoutWorker.submit(LayoutRequest request)`; `CompletionStage<LayoutFrame> LayoutWorker.step()`; `void LayoutWorker.close()`; `LayoutRequest LayoutRequest.of(WorkspaceId workspace, GraphProjection projection, ProjectionDiff diff, List<PinProjection> pins)`; `ProjectionDiff ProjectionDiff.between(GraphProjection before, GraphProjection after)`; `PinRecord PinRecord.of(NodeReference node, double x, double y, List<UnknownXml> attributes)`; `PinProjection PinProjection.active(PinRecord record, ProjectedNodeKey node)`; `boolean PerceptualIdlePolicy.IdleMeasurement.idle()`, `double rms()`, `double max()`; production from Tasks 1-2: `boolean TypedSpringBox.isAnchorParticle(String id)`, `void TypedSpringBox.configureParticle(String id, double radius, boolean pinned, boolean anchor, String parentAnchorId)`, `boolean TypedSpringBox.isAncestorPair(String first, String second)`, and the fixed `void TypedNodeParticle.scaleRepulsion(Vector3 before)`; existing private helpers `double BoundarySeparationShould.distance(LayoutPoint first, LayoutPoint second)` and `LayoutFrame BoundarySeparationShould.settle(WorkspaceId workspace, GraphProjection projection)`.
- Produces: `private static LayoutFrame await(CompletionStage<LayoutFrame> stage)` in both test classes; `TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle()`; `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged()`; `BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased()`.

- [ ] **Step 1: Write the three tests and the await helpers**

In `TypedForcesShould.java`, add the imports `java.util.concurrent.CompletionStage` and `java.util.concurrent.TimeUnit`. Insert this `await` helper next to the other private helpers (for example directly before `private static LayoutFrame frameAfterSteps`):

```java
    private static LayoutFrame await(CompletionStage<LayoutFrame> stage) throws Exception {
        return stage.toCompletableFuture().get(5L, TimeUnit.SECONDS);
    }
```

Insert this test method after the last existing test method (after `observeDirectNodeContainmentInfluenceOnEnclosureAnchorCoordinates`, before the helper block):

```java
    @Test
    public void settleRelationshipFreeNestedBoundaryProjectionToIdle() throws Exception {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())));
            LayoutFrame firstIdle = null;
            int firstIdleStep = 0;
            for (int step = 1; step <= 2000; step++) {
                LayoutFrame frame = await(worker.step());
                if (frame.idle().idle()) {
                    firstIdle = frame;
                    firstIdleStep = step;
                    break;
                }
            }
            assertThat(firstIdle).isNotNull();
            assertThat(firstIdleStep).isLessThanOrEqualTo(2000);
        }
        finally {
            worker.close();
        }
    }
```

In `BoundarySeparationShould.java`, add the imports `java.util.concurrent.CompletionStage` and `java.util.concurrent.TimeUnit`. Insert the same `await` helper next to the other private helpers (for example directly before `private static ProjectedNode node`):

```java
    private static LayoutFrame await(CompletionStage<LayoutFrame> stage) throws Exception {
        return stage.toCompletableFuture().get(5L, TimeUnit.SECONDS);
    }
```

Insert these two test methods after the tests added in Task 2 (still before the first `private static` helper, with the `await` helper placed among the private helpers):

```java
    @Test
    public void pinnedFixtureSettlesWithPinPositionUnchanged() throws Exception {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        ProjectedNodeKey pinned = ReferenceRepulsionFixture.replacementNode();
        PinRecord record = PinRecord.of(pinned.source().persistedReference().get(), 1059.0, -145.0,
            Collections.<org.freeplane.plugin.graph.workspace.model.UnknownXml>emptyList());
        List<PinProjection> pins = Collections.singletonList(PinProjection.active(record, pinned));
        LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins)));
            LayoutFrame firstIdle = null;
            int firstIdleStep = 0;
            for (int step = 1; step <= 10000; step++) {
                LayoutFrame frame = await(worker.step());
                if (frame.idle().idle()) {
                    firstIdle = frame;
                    firstIdleStep = step;
                    break;
                }
            }
            assertThat(firstIdle).isNotNull();
            assertThat(firstIdleStep).isLessThanOrEqualTo(10000);
            assertThat(firstIdle.positions().nodes().get(pinned)).isEqualTo(LayoutPoint.of(1059.0, -145.0));
            double worstRms = 0.0;
            double worstMax = 0.0;
            LayoutFrame last = firstIdle;
            for (int step = 0; step < 100; step++) {
                last = await(worker.step());
                worstRms = Math.max(worstRms, last.idle().rms());
                worstMax = Math.max(worstMax, last.idle().max());
            }
            assertThat(worstRms).isLessThanOrEqualTo(0.0505);
            assertThat(worstMax).isLessThanOrEqualTo(0.10);
            assertThat(last.positions().nodes().get(pinned)).isEqualTo(LayoutPoint.of(1059.0, -145.0));
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void unpinTransitionSettlesAfterFormerPinReleased() throws Exception {
        GraphProjection projection = ReferenceRepulsionFixture.referenceProjection(1L);
        ProjectedNodeKey pinned = ReferenceRepulsionFixture.replacementNode();
        PinRecord record = PinRecord.of(pinned.source().persistedReference().get(), 1059.0, -145.0,
            Collections.<org.freeplane.plugin.graph.workspace.model.UnknownXml>emptyList());
        List<PinProjection> pins = Collections.singletonList(PinProjection.active(record, pinned));
        LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins)));
            for (int step = 0; step < 1500; step++) {
                await(worker.step());
            }
            await(worker.submit(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())));
            LayoutFrame firstIdle = null;
            int firstIdleStep = 0;
            for (int step = 1; step <= 2000; step++) {
                LayoutFrame frame = await(worker.step());
                if (frame.idle().idle()) {
                    firstIdle = frame;
                    firstIdleStep = step;
                    break;
                }
            }
            assertThat(firstIdle).isNotNull();
            assertThat(firstIdleStep).isLessThanOrEqualTo(2000);
            assertThat(distance(firstIdle.positions().nodes().get(pinned), LayoutPoint.of(1059.0, -145.0)))
                .isGreaterThan(10.0);
        }
        finally {
            worker.close();
        }
    }
```

Step counting convention for all three tests: the request is submitted once, then `step()` calls are counted starting at 1; a cap of N means at most N `step()` calls after the submit, stopping at the first frame whose `idle().idle()` is `true`.

- [ ] **Step 2: Run the tests against the pre-fix mechanism and confirm they fail**

These are acceptance tests for the fix completed in Tasks 1 and 2, so the working tree already contains the implementation. Obtain the red evidence deterministically by temporarily removing only the anchor-scaling guard, which is the mechanism under test: in `TypedNodeParticle.scaleRepulsion`, replace

```java
        if (!typedBox.isAnchorParticle(getId().toString())
                && separationRadius != typedBox.baseSeparationRadius()) {
```

with

```java
        if (separationRadius != typedBox.baseSeparationRadius()) {
```

then run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle" --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged" --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL for all three. `settleRelationshipFreeNestedBoundaryProjectionToIdle` fails `assertThat(firstIdle).isNotNull()` because the asymmetric anchor repulsion keeps the layout moving (spec steady-state rms ≈ `0.184`); `pinnedFixtureSettlesWithPinPositionUnchanged` fails the same assertion within 10000 steps; `unpinTransitionSettlesAfterFormerPinReleased` fails the same assertion within 2000 steps after unpin.

Restore the fixed implementation exactly (replace the probe condition back with the two-line anchor guard shown above), then verify the production file is byte-identical to its committed state:

```bash
git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java
```

Expected: exit status `0` (no output, no diff). Do not commit the probe state.

- [ ] **Step 3: Run the tests with the fix in place and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle" --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged" --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased" -PTestLoggingFull --rerun-tasks
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.*" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. `TypedForcesShould` reports 15 tests, `BoundarySeparationShould` reports 13 tests, and the layout package reports 58 tests. The reference fixture reaches its first idle frame at step 457 (rms 0.0472, max 0.0939); the pinned fixture reaches its first idle frame at step 2471 (rms `0.049654`) and stays under `rms <= 0.0505` / `max <= 0.10` for the following 100 frames with the pin exactly at `(1059.0, -145.0)`; after unpin the layout idles 292 steps later with the formerly pinned node `108.97` units from the pin (assertion bound `> 10.0`).

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "Prove pinned and unpinned nested boundaries settle [2026-09-11-graph-node-repulsion]"
```

## Task 4: Prove reparenting, two-map settling, and the full suite

**Implementer tier:** Standard
**Lane:** graph-repulsion

**Files:**
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java:220-271`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:289-323`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:52-58` (temporary falsifiability probe only; restored before commit)

**Interfaces:**
- Consumes: `ReferenceRepulsionFixture` from Task 1 with `static final WorkspaceId WORKSPACE`, `static final MapReferenceId MAP`, `static GraphProjection referenceProjection(long generation)`, `static GraphProjection reparentedProjection(long generation)`, `static EnclosureHullKey axiomsHull()`, `definitionsHull()`, `static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull)`; `LayoutEngine GraphStreamLayoutFactory.create(LayoutCalibration calibration)`; `LayoutFrame LayoutEngine.apply(LayoutRequest request)`; `LayoutFrame LayoutEngine.step()`; `ProjectionDiff ProjectionDiff.between(GraphProjection before, GraphProjection after)`; `LayoutWorker(LayoutCalibration)`; `CompletionStage<LayoutFrame> LayoutWorker.submit(LayoutRequest)`; `CompletionStage<LayoutFrame> LayoutWorker.step()`; `LayoutRequest LayoutRequest.of(WorkspaceId workspace, GraphProjection projection, ProjectionDiff diff, List<PinProjection> pins)`; production from Task 2: `void TypedSpringBox.configureParticle(String id, double radius, boolean pinned, boolean anchor, String parentAnchorId)`, `boolean TypedSpringBox.isAncestorPair(String first, String second)`, `String GraphStreamLayoutEngine.DesiredParticle.parentAnchorId`; the existing private helpers `TypedForcesShould.key(MapReferenceId map, String id)`, `hull(MapReferenceId map, String id)`, `enclosureKey(MapReferenceId map, String id)`, `source(MapReferenceId map, String id)`, `projection(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges)`, `BoundarySeparationShould.distance(LayoutPoint first, LayoutPoint second)`.
- Produces: `BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion()`; `TypedForcesShould.twoMapWorkspaceSettlesToIdle()` and `private static GraphProjection twoMapProjection(long generation)`; verification evidence `V1`-`V4`.

- [ ] **Step 1: Write the reparenting test and the two-map test**

Insert this test method into `BoundarySeparationShould.java` after the tests added in Tasks 2 and 3, before the first `private static` helper:

```java
    @Test
    public void reparentedBoundaryRefreshesAncestorExclusion() {
        GraphProjection original = ReferenceRepulsionFixture.referenceProjection(1L);
        GraphProjection reparented = ReferenceRepulsionFixture.reparentedProjection(2L);
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, original,
                ProjectionDiff.between(original, original), Collections.<PinProjection>emptyList()));
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            LayoutRequest reparentRequest = LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, reparented,
                ProjectionDiff.between(original, reparented), Collections.<PinProjection>emptyList());
            engine.apply(reparentRequest);
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            LayoutFrame frame = engine.apply(reparentRequest);
            assertThat(distance(frame.positions().anchors().get(ReferenceRepulsionFixture.axiomsHull()),
                frame.positions().anchors().get(ReferenceRepulsionFixture.definitionsHull())))
                .isLessThanOrEqualTo(ReferenceRepulsionFixture.boundaryRadius(reparented,
                    ReferenceRepulsionFixture.axiomsHull()) - 16.0);
        }
    }
```

`ProjectionDiff.between(original, reparented)` is the honest request description and guarantees `synchronize()`; the empty-diff fast path is taken only when the diff is empty and its `beforeGeneration()` equals the last synchronized generation, so a real reparent always re-synchronizes and the trailing `apply(reparentRequest)` is position-idempotent. `ReferenceRepulsionFixture.reparentedProjection(2L)` moves `"Basic Definitions and Theorems"` from `"ZFC"` to `"Axioms"` with `zfc.directEnclosures = [axioms]`, `axioms.directEnclosures = [definitions]`, and `definitions.parentHull = axioms`, so the pair under assertion becomes a direct parent/child pair.

In `TypedForcesShould.java`, insert this test method after the tests added in Tasks 1 and 3, before the first `private static` helper:

```java
    @Test
    public void twoMapWorkspaceSettlesToIdle() throws Exception {
        GraphProjection projection = twoMapProjection(1L);
        LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(LayoutRequest.of(ReferenceRepulsionFixture.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())));
            LayoutFrame firstIdle = null;
            int firstIdleStep = 0;
            for (int step = 1; step <= 2000; step++) {
                LayoutFrame frame = await(worker.step());
                if (frame.idle().idle()) {
                    firstIdle = frame;
                    firstIdleStep = step;
                    break;
                }
            }
            assertThat(firstIdle).isNotNull();
            assertThat(firstIdleStep).isLessThanOrEqualTo(2000);
            for (int step = 0; step < 100; step++) {
                LayoutFrame frame = await(worker.step());
                assertThat(frame.idle().rms()).isLessThanOrEqualTo(0.05);
                assertThat(frame.idle().max()).isLessThanOrEqualTo(0.10);
            }
        }
        finally {
            worker.close();
        }
    }
```

Insert this private helper next to the other fixture helpers of `TypedForcesShould` (directly before `private static LayoutRequest request`):

```java
    private static GraphProjection twoMapProjection(long generation) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO};
        for (int mapIndex = 0; mapIndex < maps.length; mapIndex++) {
            MapReferenceId map = maps[mapIndex];
            String prefix = mapIndex == 0 ? "a" : "b";
            EnclosureHullKey rootHull = hull(map, prefix + "-root");
            List<EnclosureHullKey> subHulls = new ArrayList<EnclosureHullKey>();
            for (int sub = 0; sub < 3; sub++) {
                String subId = prefix + "-sub-" + sub;
                EnclosureHullKey subHull = hull(map, subId);
                List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
                for (int nodeIndex = 0; nodeIndex < 4; nodeIndex++) {
                    ProjectedNodeKey nodeKey = key(map, prefix + sub + "_" + nodeIndex);
                    String nodeLabel = "Boundary " + sub + " node " + nodeIndex;
                    nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(nodeLabel, nodeLabel), "Map", true));
                    subNodes.add(nodeKey);
                }
                String subLabel = (mapIndex == 0 ? "A-sub " : "B-sub ") + sub;
                enclosures.add(ProjectedEnclosure.of(subHull, Collections.singletonList(
                    EnclosureKey.of(source(map, subId))),
                    Collections.singletonList(SafeNodeLabel.of(subLabel, subLabel)), "Map",
                    Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                    BoundaryTier.SUBTLE));
                subHulls.add(subHull);
            }
            String rootLabel = mapIndex == 0 ? "Axiomatic Set Theory" : "Topology";
            BoundaryTier rootTier = mapIndex == 0 ? BoundaryTier.SUPPRESSED : BoundaryTier.EMPHATIC;
            enclosures.add(ProjectedEnclosure.of(rootHull, Collections.singletonList(
                EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of(rootLabel, rootLabel)), "Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), subHulls, true,
                rootTier));
        }
        for (int i = 0; i < 4; i++) {
            long sequence = i + 1L;
            GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(String.format("20000000-0000-0000-0000-%012d", Long.valueOf(sequence))),
                sequence,
                key(MAP_ONE, "a0_" + i).source().persistedReference().get(),
                key(MAP_TWO, "b0_" + i).source().persistedReference().get(),
                RelationshipDirection.FORWARD,
                Collections.<UnknownXml>emptyList());
            ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofNode(key(MAP_ONE, "a0_" + i));
            ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(key(MAP_TWO, "b0_" + i));
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
                Collections.singletonList(EdgeContributor.graphRelationship(relationship, sourceEndpoint,
                    targetEndpoint))));
        }
        return projection(generation, nodes, enclosures, edges);
    }
```

This fixture has 24 nodes and 8 anchors (32 particles) and 4 cross-map relationship edges; the enclosure list order is `[a-sub-0, a-sub-1, a-sub-2, a-root, b-sub-0, b-sub-1, b-sub-2, b-root]` because each sub-boundary is appended before its root, and the node list order is map A first (sub ascending, node ascending), then map B.

- [ ] **Step 2: Run the reparenting test against a stale ancestry and confirm it fails**

The implementation already exists, so obtain the red evidence for the reparent-refresh contract deterministically: in `TypedSpringBox.configureParticle`, temporarily remove the first statement

```java
        parentOf.put(id, parentAnchorId);
```

then run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL on the `isLessThanOrEqualTo(... - 16.0)` assertion; with no `parentOf` entries the reparented pair keeps receiving boundary repulsion and settles at ≈ `699.88`, above the `577.87` bound. Restore the line as the first statement of `configureParticle` and verify the production file is byte-identical to its committed state:

```bash
git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java
```

Expected: exit status `0` (no output, no diff). Do not commit the probe state.

- [ ] **Step 3: Run the two new tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion" --tests "org.freeplane.plugin.graph.layout.TypedForcesShould.twoMapWorkspaceSettlesToIdle" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. The reparented pair settles at `61.84`, within the `577.87` bound, because `configureParticle` overwrites `parentOf` on every accepted request; the two-map workspace reaches its first idle frame at step 419 (rms 0.0468, max 0.0648) and stays under `rms <= 0.05` / `max <= 0.10` for the following 100 frames. `TypedForcesShould` reports 16 tests and `BoundarySeparationShould` reports 14 tests.

- [ ] **Step 4: Run the full plugin suite (`V1`/`V2`)**

Run:

```bash
gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Expected: BUILD SUCCESSFUL. The run includes the existing 88 layout/settle-loop tests green (`TypedForcesShould` 13 existing + 3 new = 16, `BoundarySeparationShould` 7 existing + 7 new = 14, `GraphStreamBoundaryShould` 5, `LayoutWorkerShould` 13, `MapTierCorrectionShould` 7, `PerceptualIdlePolicyShould` 5, `LayoutSettleLoopShould` 38) plus the 10 new tests; layout package 50 -> 60 and layout/settle-loop total 88 -> 98. Confirm the counts in the generated test report; if a count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 5: Verify scope (`V3`) and record manual acceptance (`V4`)**

Run:

```bash
git status --porcelain
git diff --name-only
git diff --cached --name-only
```

Expected: only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`, `TypedSpringBox.java`, `TypedNodeParticle.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`, `BoundarySeparationShould.java`, and the new `ReferenceRepulsionFixture.java` appear across the whole plan; no existing test method was modified (`git diff` on the two existing test files must contain only added methods and imports). No production class outside the three `graphstream` files changed, and the non-finite position guards, `LayoutWorker` coverage validation, and `LayoutWorker`/`LayoutSettleLoop` failed-frame recovery are untouched.

`V4` is manual acceptance and is not part of the automated gate: after a full build, run `BIN/freeplane.sh`, open the user's `math.fpg`, unpin a node, and observe that the layout freezes with CPU returning to idle; confirm that sibling boundaries and multiple map roots still appear visually separated. Record the observation in the task report; do not change layout code to satisfy it.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git commit -m "Prove reparenting and two-map workspace settling [2026-09-11-graph-node-repulsion]"
```

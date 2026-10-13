package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.NodeSeparationProjection;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

/**
 * Acceptance fixtures A-H (specification section 7.2) and the exact initial-frame
 * assertions of section 7.1.3.
 */
public class ClusterProximityShould {
    static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    static final MapReferenceId MAP_THREE = MapReferenceId.of("00000000-0000-0000-0000-000000000003");
    static final MapReferenceId MAP_FOUR = MapReferenceId.of("00000000-0000-0000-0000-000000000004");

    private static final char[] FIXTURE_NAMES =
        new char[] {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'};
    private static final char[] UNPINNED_FIXTURE_NAMES =
        new char[] {'A', 'B', 'D', 'E', 'F', 'G', 'H'};

    // ---------------------------------------------------------------- fixtures

    static GraphProjection fixture(char name) {
        switch (name) {
            case 'A':
                return referenceProjection(1L);
            case 'B':
                return twoMapProjection(1L);
            case 'C':
            case 'D':
                return relationshipProjection(1L);
            case 'E':
                return dualProjection(1L);
            case 'F':
                return fourMapProjection(1L);
            case 'G':
                return fourSiblingProjection(1L);
            case 'H':
                return asymmetricSiblingProjection(1L);
            default:
                throw new IllegalArgumentException("unknown fixture " + name);
        }
    }

    static List<PinProjection> fixturePins(char name) {
        if (name == 'C') {
            return Collections.singletonList(pin(nodeKey(MAP_TWO, "b-n0"), 60.0, 20.0));
        }
        return Collections.<PinProjection>emptyList();
    }

    /** Fixture A, re-created identically to {@code ReferenceRepulsionFixture.referenceProjection(1L)}. */
    private static GraphProjection referenceProjection(long generation) {
        final List<ProjectedNode> nodes = Arrays.asList(
            node(MAP_ONE, "n1", "Fundation / Regularity", "M", true),
            node(MAP_ONE, "n2", "Replacement Scheme", "M", true),
            node(MAP_ONE, "n3", "Axiom of Choice", "M", true),
            node(MAP_ONE, "n4", "Theorem", "M", true));
        final EnclosureHullKey root = hull(MAP_ONE, "root");
        final EnclosureHullKey zfc = hull(MAP_ONE, "zfc");
        final EnclosureHullKey axioms = hull(MAP_ONE, "axioms");
        final EnclosureHullKey definitions = hull(MAP_ONE, "defs");
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(root, "Axiomatic Set Theory",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.singletonList(zfc), true, BoundaryTier.SUPPRESSED, "M"));
        enclosures.add(enclosure(zfc, "ZFC", Optional.of(root),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(axioms, definitions), false,
            BoundaryTier.EMPHATIC, "M"));
        enclosures.add(enclosure(axioms, "Axioms", Optional.of(zfc),
            Arrays.asList(nodeKey(MAP_ONE, "n1"), nodeKey(MAP_ONE, "n2"), nodeKey(MAP_ONE, "n3")),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "M"));
        enclosures.add(enclosure(definitions, "Basic Definitions and Theorems", Optional.of(zfc),
            Collections.singletonList(nodeKey(MAP_ONE, "n4")),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "M"));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection twoMapProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO};
        for (int m = 0; m < maps.length; m++) {
            final MapReferenceId map = maps[m];
            final String prefix = m == 0 ? "a" : "b";
            final int nodeCount = m == 0 ? 6 : 2;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                final ProjectedNodeKey key = nodeKey(map, prefix + "-n" + i);
                nodes.add(node(map, prefix + "-n" + i,
                    "Theorem: " + prefix + " statement " + i + " with a long label", "Map", false));
                subNodes.add(key);
            }
            enclosures.add(enclosure(subHull, prefix + " fundamentals", Optional.of(rootHull), subNodes,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
            enclosures.add(enclosure(rootHull, "Map " + prefix, Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(subHull), true,
                BoundaryTier.EMPHATIC, "Map"));
        }
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection relationshipProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int m = 0; m < 2; m++) {
            final MapReferenceId map = m == 0 ? MAP_ONE : MAP_TWO;
            final String prefix = m == 0 ? "a" : "b";
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 4; i++) {
                final ProjectedNodeKey key = nodeKey(map, prefix + "-n" + i);
                nodes.add(node(map, prefix + "-n" + i,
                    "Theorem: " + prefix + " statement " + i + " with a long label", "Map", false));
                subNodes.add(key);
            }
            enclosures.add(enclosure(subHull, prefix + " fundamentals", Optional.of(rootHull), subNodes,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
            enclosures.add(enclosure(rootHull, "Map " + prefix, Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(subHull), true,
                BoundaryTier.EMPHATIC, "Map"));
        }
        for (int i = 0; i < 3; i++) {
            final long sequence = i + 1L;
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(String.format("20000000-0000-0000-0000-%012d", Long.valueOf(sequence))),
                sequence,
                nodeKey(MAP_ONE, "a-n" + i).source().persistedReference().get(),
                nodeKey(MAP_TWO, "b-n" + i).source().persistedReference().get(),
                RelationshipDirection.FORWARD,
                Collections.<UnknownXml>emptyList());
            final ProjectedEndpointKey sourceEndpoint =
                ProjectedEndpointKey.ofNode(nodeKey(MAP_ONE, "a-n" + i));
            final ProjectedEndpointKey targetEndpoint =
                ProjectedEndpointKey.ofNode(nodeKey(MAP_TWO, "b-n" + i));
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
                Collections.singletonList(
                    EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint))));
        }
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static GraphProjection dualProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node(MAP_ONE, "r0", "Theorem: root statement zero with a long label", "Map", false));
        nodes.add(node(MAP_ONE, "r1", "Theorem: root statement one with a long label", "Map", false));
        final List<ProjectedNodeKey> childNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 3; i++) {
            childNodes.add(nodeKey(MAP_ONE, "c" + i));
            nodes.add(node(MAP_ONE, "c" + i,
                "Theorem: child statement " + i + " with a long label", "Map", false));
        }
        final EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        final EnclosureHullKey childHull = hull(MAP_ONE, "child");
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(childHull, "child group", Optional.of(rootHull), childNodes,
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
        enclosures.add(enclosure(rootHull, "Root map", Optional.<EnclosureHullKey>empty(),
            Arrays.asList(nodeKey(MAP_ONE, "r0"), nodeKey(MAP_ONE, "r1")),
            Collections.singletonList(childHull), true, BoundaryTier.SUPPRESSED, "Map"));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection fourMapProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO, MAP_THREE, MAP_FOUR};
        for (int m = 0; m < maps.length; m++) {
            final MapReferenceId map = maps[m];
            final String prefix = "m" + m;
            final int nodeCount = 2 + m;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                final ProjectedNodeKey key = nodeKey(map, prefix + "-n" + i);
                nodes.add(node(map, prefix + "-n" + i,
                    "Theorem: " + prefix + " statement " + i + " with a long label", "Map", false));
                subNodes.add(key);
            }
            enclosures.add(enclosure(subHull, prefix + " fundamentals", Optional.of(rootHull), subNodes,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
            enclosures.add(enclosure(rootHull, "Map " + prefix, Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(subHull), true,
                BoundaryTier.EMPHATIC, "Map"));
        }
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection fourSiblingProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "g-root");
        final List<EnclosureHullKey> childHulls = new ArrayList<EnclosureHullKey>();
        for (int g = 0; g < 4; g++) {
            final EnclosureHullKey childHull = hull(MAP_ONE, "g" + g);
            childHulls.add(childHull);
            final List<ProjectedNodeKey> childNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 2; i++) {
                final ProjectedNodeKey key = nodeKey(MAP_ONE, "g" + g + "-n" + i);
                nodes.add(node(MAP_ONE, "g" + g + "-n" + i,
                    "Theorem: g" + g + " statement " + i + " with a long label", "Map", false));
                childNodes.add(key);
            }
            enclosures.add(enclosure(childHull, "group " + g, Optional.of(rootHull), childNodes,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
        }
        enclosures.add(enclosure(rootHull, "Map g", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), childHulls, true, BoundaryTier.EMPHATIC, "Map"));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection asymmetricSiblingProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "h-root");
        final EnclosureHullKey childOneHull = hull(MAP_ONE, "h-c1");
        final EnclosureHullKey grandHull = hull(MAP_ONE, "h-g1");
        final EnclosureHullKey childTwoHull = hull(MAP_ONE, "h-c2");
        final List<ProjectedNodeKey> rootNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = nodeKey(MAP_ONE, "r" + i);
            nodes.add(node(MAP_ONE, "r" + i,
                "Theorem: root statement " + i + " with a long label", "Map", false));
            rootNodes.add(key);
        }
        final List<ProjectedNodeKey> childOneNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = nodeKey(MAP_ONE, "c1-" + i);
            nodes.add(node(MAP_ONE, "c1-" + i,
                "Theorem: child one statement " + i + " with a long label", "Map", false));
            childOneNodes.add(key);
        }
        final List<ProjectedNodeKey> grandNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = nodeKey(MAP_ONE, "g1-" + i);
            nodes.add(node(MAP_ONE, "g1-" + i,
                "Theorem: grandchild statement " + i + " with a long label", "Map", false));
            grandNodes.add(key);
        }
        final List<ProjectedNodeKey> childTwoNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = nodeKey(MAP_ONE, "c2-" + i);
            nodes.add(node(MAP_ONE, "c2-" + i,
                "Theorem: child two statement " + i + " with a long label", "Map", false));
            childTwoNodes.add(key);
        }
        enclosures.add(enclosure(grandHull, "grandchild group", Optional.of(childOneHull), grandNodes,
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
        enclosures.add(enclosure(childOneHull, "child one group", Optional.of(rootHull),
            childOneNodes, Collections.singletonList(grandHull), false, BoundaryTier.EMPHATIC, "Map"));
        enclosures.add(enclosure(childTwoHull, "child two group", Optional.of(rootHull), childTwoNodes,
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE, "Map"));
        enclosures.add(enclosure(rootHull, "Root map", Optional.<EnclosureHullKey>empty(), rootNodes,
            Arrays.asList(childOneHull, childTwoHull), true, BoundaryTier.SUPPRESSED, "Map"));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    static GraphProjection wideProjection(long generation) {
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "wide-root");
        final List<EnclosureHullKey> children = new ArrayList<EnclosureHullKey>();
        for (int index = 0; index < 200; index++) {
            final EnclosureHullKey childHull = hull(MAP_ONE, "wide-" + index);
            children.add(childHull);
            enclosures.add(enclosure(childHull, "wide-" + index, Optional.of(rootHull),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE, "Map"));
        }
        enclosures.add(enclosure(rootHull, "Root", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), children, true, BoundaryTier.EMPHATIC, "Map"));
        return GraphProjection.projected(generation, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    // ------------------------------------------------------------- composition

    static Map<EnclosureHullKey, LayoutPoint> composeAnchors(final GraphProjection projection) {
        final GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection);
        final Map<EnclosureHullKey, LayoutPoint> result =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            result.put(enclosure.hullKey(), composedAnchor(sizes, enclosure.hullKey()));
        }
        return result;
    }

    private static LayoutPoint composedAnchor(final GraphStreamLayoutEngine.BoundarySizes sizes,
            final EnclosureHullKey key) {
        final ProjectedEnclosure enclosure = sizes.enclosure(key);
        if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
            final ClusterPacking.ClusterPlacement placement = sizes.topLevelPlacementOf(key);
            return placement == null ? LayoutPoint.of(0.0, 0.0)
                : LayoutPoint.of(placement.x + placement.item.anchorX,
                                 placement.y + placement.item.anchorY);
        }
        final EnclosureHullKey parentKey = enclosure.parentHull().get();
        final LayoutPoint parentCenter = composedAnchor(sizes, parentKey);
        final ClusterPacking.ClusterPlacement placement = sizes.placementOf(parentKey, key);
        return placement == null ? parentCenter
            : LayoutPoint.of(parentCenter.x() + placement.x + placement.item.anchorX,
                             parentCenter.y() + placement.y + placement.item.anchorY);
    }

    static LayoutFrame engineFrame(final GraphProjection projection, final List<PinProjection> pins) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            return engine.apply(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins));
        }
    }

    static LayoutFrame workerFrame(final GraphProjection projection, final List<PinProjection> pins)
            throws Exception {
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            return worker.submit(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins)).toCompletableFuture()
                .get(5L, TimeUnit.SECONDS);
        }
        finally {
            worker.close();
        }
    }

    // ------------------------------------------------------------------- tests

    @Test
    public void initialFrameEqualsTheDeterministicPacking() {
        for (final char name : FIXTURE_NAMES) {
            final GraphProjection projection = fixture(name);
            final List<PinProjection> pins = fixturePins(name);
            final GraphStreamLayoutEngine.BoundarySizes sizes =
                new GraphStreamLayoutEngine.BoundarySizes(projection);
            final LayoutFrame frame = engineFrame(projection, pins);
            assertEngineFrameMatchesPacking(name, projection, pins, sizes, frame.positions());
            final LayoutFrame repeated = engineFrame(projection, pins);
            assertThat(repeated.positions()).as("fixture %s determinism", Character.valueOf(name))
                .isEqualTo(frame.positions());
        }
    }

    @Test
    public void generatedProjectionsComposeIdenticallyAndDoNotOverlap() {
        final Random random = new Random(20261013L);
        for (int round = 0; round < 400; round++) {
            final GraphProjection projection = generatedProjection(random, round);
            final LayoutFrame frame = engineFrame(projection, Collections.<PinProjection>emptyList());
            final Map<EnclosureHullKey, LayoutPoint> expected = composeAnchors(projection);
            for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : expected.entrySet()) {
                assertThat(frame.positions().anchors().get(entry.getKey()))
                    .as("round %s anchor %s", Integer.valueOf(round), entry.getKey())
                    .isEqualTo(entry.getValue());
            }
            assertPackedNonOverlap(projection, frame.positions(),
                new GraphStreamLayoutEngine.BoundarySizes(projection));
        }
    }

    @Test
    public void initialFrameThroughWorkerKeepsSeedsWithoutPins() throws Exception {
        final List<PinProjection> pins = Collections.<PinProjection>emptyList();
        for (final char name : UNPINNED_FIXTURE_NAMES) {
            final GraphProjection projection = fixture(name);
            final GraphStreamLayoutEngine.BoundarySizes sizes =
                new GraphStreamLayoutEngine.BoundarySizes(projection);
            final LayoutFrame engine = engineFrame(projection, pins);
            final LayoutFrame worker = workerFrame(projection, pins);
            // The worker applies BoundarySeparationCorrection, whose first stage is the
            // documented NodeSeparationProjection. It rebuilds node positions, so on the exact
            // MIN_GAP tie produced by contentRingRadius the last ulp can change on some fixtures;
            // anchors are never moved for an unpinned seed and no hull displacement is recorded.
            // Assert both stages exactly instead of relaxing an equality to a tolerance.
            assertThat(worker.positions().anchors())
                .as("fixture %s worker anchors equal engine anchors", Character.valueOf(name))
                .isEqualTo(engine.positions().anchors());
            assertThat(worker.positions().nodes())
                .as("fixture %s worker nodes equal the node-separation projection of the engine frame",
                    Character.valueOf(name))
                .isEqualTo(new NodeSeparationProjection().project(projection, engine.positions(),
                    Collections.<ProjectedNodeKey>emptySet()).positions().nodes());
            assertPackedNonOverlap(projection, worker.positions(), sizes);
            assertContactGutters(sizes);
        }
    }

    @Test
    public void initialFrameThroughWorkerWithPinOnlyChecksNonOverlap() throws Exception {
        final GraphProjection projection = fixture('C');
        final List<PinProjection> pins = fixturePins('C');
        final GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection);
        final LayoutFrame frame = workerFrame(projection, pins);
        assertPackedNonOverlap(projection, frame.positions(), sizes);
        assertContactGutters(sizes);
        assertThat(frame.positions().nodes().get(nodeKey(MAP_TWO, "b-n0")))
            .as("fixture C pinned b-n0")
            .isEqualTo(LayoutPoint.of(60.0, 20.0));
    }

    // --------------------------------------------------------------- assertions

    private static void assertEngineFrameMatchesPacking(final char name, final GraphProjection projection,
            final List<PinProjection> pins, final GraphStreamLayoutEngine.BoundarySizes sizes,
            final LayoutPositions positions) {
        final Map<EnclosureHullKey, LayoutPoint> expected = composeAnchors(projection);
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : expected.entrySet()) {
            assertThat(positions.anchors().get(entry.getKey()))
                .as("fixture %s anchor %s", Character.valueOf(name), entry.getKey())
                .isEqualTo(entry.getValue());
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey key = enclosure.hullKey();
            final List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            final LayoutPoint parentAnchor = positions.anchors().get(key);
            final double radius = sizes.contentRingRadius(key);
            for (int index = 0; index < directNodes.size(); index++) {
                final ProjectedNodeKey nodeKey = directNodes.get(index);
                final LayoutPoint actual = positions.nodes().get(nodeKey);
                final PinProjection pin = pinFor(pins, nodeKey);
                if (pin != null) {
                    assertThat(actual).as("fixture %s pinned node %s", Character.valueOf(name), nodeKey)
                        .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
                    continue;
                }
                final double angle = 2.0 * Math.PI * Math.max(0, index)
                    / Math.max(1, directNodes.size());
                final LayoutPoint expectedNode = LayoutPoint.of(
                    parentAnchor.x() + radius * Math.cos(angle),
                    parentAnchor.y() + radius * Math.sin(angle));
                assertThat(actual)
                    .as("fixture %s node %s at ring index %d", Character.valueOf(name), nodeKey,
                        Integer.valueOf(index))
                    .isEqualTo(expectedNode);
            }
            if (!directNodes.isEmpty()) {
                final List<ClusterPacking.ClusterPlacement> placements = sizes.placements(key);
                assertThat(placements).as("fixture %s content assembly %s", Character.valueOf(name), key)
                    .isNotEmpty();
                final ClusterPacking.ClusterPlacement first = placements.get(0);
                assertThat(first.item.anchorKey)
                    .as("fixture %s content item first for %s", Character.valueOf(name), key)
                    .isNull();
                assertThat(first.x).isEqualTo(0.0);
                assertThat(first.y).isEqualTo(0.0);
            }
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final boolean expectedTop = enclosure.mapRoot() || !enclosure.parentHull().isPresent();
            assertThat(sizes.topLevelPlacementOf(enclosure.hullKey()) != null)
                .as("fixture %s top-level membership of %s", Character.valueOf(name), enclosure.hullKey())
                .isEqualTo(expectedTop);
        }
        assertPackedNonOverlap(projection, positions, sizes);
        assertContactGutters(sizes);
    }

    private static void assertPackedNonOverlap(final GraphProjection projection,
            final LayoutPositions positions, final GraphStreamLayoutEngine.BoundarySizes sizes) {
        final List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>(positions.anchors().keySet());
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                final EnclosureHullKey firstKey = keys.get(first);
                final EnclosureHullKey secondKey = keys.get(second);
                if (related(projection, firstKey, secondKey)) {
                    continue;
                }
                final GraphStreamLayoutEngine.BoundarySizes.Footprint firstFootprint =
                    sizes.footprint(firstKey);
                final GraphStreamLayoutEngine.BoundarySizes.Footprint secondFootprint =
                    sizes.footprint(secondKey);
                final LayoutPoint firstAnchor = positions.anchors().get(firstKey);
                final LayoutPoint secondAnchor = positions.anchors().get(secondKey);
                final double centreX1 = firstAnchor.x() - firstFootprint.anchorX;
                final double centreY1 = firstAnchor.y() - firstFootprint.anchorY;
                final double centreX2 = secondAnchor.x() - secondFootprint.anchorX;
                final double centreY2 = secondAnchor.y() - secondFootprint.anchorY;
                final double boxGapX = Math.abs(centreX1 - centreX2)
                    - (firstFootprint.width + secondFootprint.width) * 0.5;
                final double boxGapY = Math.abs(centreY1 - centreY2)
                    - (firstFootprint.height + secondFootprint.height) * 0.5;
                assertThat(Math.max(boxGapX, boxGapY))
                    .as("packed boxes of %s and %s", firstKey, secondKey)
                    .isGreaterThanOrEqualTo(-ClusterPacking.CANDIDATE_EPSILON);
            }
        }
    }

    private static void assertContactGutters(final GraphStreamLayoutEngine.BoundarySizes sizes) {
        for (final ProjectedEnclosure enclosure : sizes.enclosures()) {
            final EnclosureHullKey key = enclosure.hullKey();
            final List<ClusterPacking.ClusterPlacement> placements = sizes.placements(key);
            for (final ClusterPacking.ClusterPlacement placement : placements) {
                for (final ClusterPacking.ClusterItem contact : placement.contacts) {
                    final ClusterPacking.ClusterPlacement other = placementFor(placements, contact);
                    assertThat(other).as("contact placement of %s under %s", contact.anchorKey, key)
                        .isNotNull();
                    final double boxGapX = Math.abs(placement.x - other.x)
                        - (placement.item.width + other.item.width) * 0.5;
                    final double boxGapY = Math.abs(placement.y - other.y)
                        - (placement.item.height + other.item.height) * 0.5;
                    assertThat(Math.max(boxGapX, boxGapY))
                        .as("contact gutter %s <-> %s under %s",
                            placement.item.anchorKey, other.item.anchorKey, key)
                        .isBetween(GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP
                                - ClusterPacking.CANDIDATE_EPSILON,
                            GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP
                                + ClusterPacking.CONTACT_GAP_TOLERANCE);
                }
            }
        }
    }

    private static ClusterPacking.ClusterPlacement placementFor(
            final List<ClusterPacking.ClusterPlacement> placements, final ClusterPacking.ClusterItem item) {
        for (final ClusterPacking.ClusterPlacement placement : placements) {
            if (placement.item == item) {
                return placement;
            }
        }
        return null;
    }

    static boolean related(final GraphProjection projection, final EnclosureHullKey first,
            final EnclosureHullKey second) {
        return isAncestor(projection, first, second) || isAncestor(projection, second, first);
    }

    private static boolean isAncestor(final GraphProjection projection, final EnclosureHullKey ancestor,
            final EnclosureHullKey child) {
        Optional<EnclosureHullKey> parent = parentOf(projection, child);
        while (parent.isPresent()) {
            if (parent.get().equals(ancestor)) {
                return true;
            }
            parent = parentOf(projection, parent.get());
        }
        return false;
    }

    private static Optional<EnclosureHullKey> parentOf(final GraphProjection projection,
            final EnclosureHullKey key) {
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.hullKey().equals(key)) {
                return enclosure.parentHull();
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------- generated projections

    private static GraphProjection generatedProjection(final Random random, final int round) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final int mapCount = 1 + random.nextInt(6);
        for (int m = 0; m < mapCount; m++) {
            final MapReferenceId map = generatedMap(m);
            final String prefix = "r" + round + "m" + m;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
            final int nodeCount = random.nextInt(7);
            for (int n = 0; n < nodeCount; n++) {
                final ProjectedNodeKey key = nodeKey(map, prefix + "-n" + n);
                nodes.add(node(map, prefix + "-n" + n, repeated(random, 'x', 1 + random.nextInt(24)),
                    "Map", random.nextBoolean()));
                directNodes.add(key);
            }
            final List<EnclosureHullKey> children = new ArrayList<EnclosureHullKey>();
            addGeneratedChildren(random, map, 0, 3, rootHull, prefix, nodes, enclosures, children);
            enclosures.add(enclosure(rootHull, "Root " + prefix,
                Optional.<EnclosureHullKey>empty(), directNodes, children, true, BoundaryTier.EMPHATIC,
                "Map"));
        }
        return GraphProjection.projected(1L, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static void addGeneratedChildren(final Random random, final MapReferenceId map,
            final int depth, final int maxDepth, final EnclosureHullKey parentHull, final String prefix,
            final List<ProjectedNode> nodes, final List<ProjectedEnclosure> enclosures,
            final List<EnclosureHullKey> children) {
        if (depth >= maxDepth) {
            return;
        }
        final int childCount = random.nextInt(6);
        for (int c = 0; c < childCount; c++) {
            final String id = prefix + "-c" + depth + "-" + c;
            final EnclosureHullKey childHull = hull(map, id);
            final List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
            if (!random.nextBoolean()) {
                final int nodeCount = random.nextInt(7);
                for (int n = 0; n < nodeCount; n++) {
                    final ProjectedNodeKey key = nodeKey(map, id + "-n" + n);
                    nodes.add(node(map, id + "-n" + n, repeated(random, 'x', 1 + random.nextInt(24)),
                        "Map", random.nextBoolean()));
                    directNodes.add(key);
                }
            }
            final List<EnclosureHullKey> grandchildren = new ArrayList<EnclosureHullKey>();
            addGeneratedChildren(random, map, depth + 1, maxDepth, childHull, id, nodes, enclosures,
                grandchildren);
            enclosures.add(enclosure(childHull, "Child " + id, Optional.of(parentHull), directNodes,
                grandchildren, false, BoundaryTier.SUBTLE, "Map"));
            children.add(childHull);
        }
    }

    private static MapReferenceId generatedMap(final int index) {
        switch (index) {
            case 0:
                return MAP_ONE;
            case 1:
                return MAP_TWO;
            case 2:
                return MAP_THREE;
            case 3:
                return MAP_FOUR;
            default:
                return MapReferenceId.of(String.format("00000000-0000-0000-0000-%012d",
                    Long.valueOf(100 + index)));
        }
    }

    private static String repeated(final Random random, final char value, final int count) {
        final StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    // ----------------------------------------------------------------- helpers

    private static PinProjection pinFor(final List<PinProjection> pins, final ProjectedNodeKey nodeKey) {
        for (final PinProjection pin : pins) {
            if (pin.active() && pin.projectedNode().isPresent()
                    && pin.projectedNode().get().equals(nodeKey)) {
                return pin;
            }
        }
        return null;
    }

    private static ProjectedNodeKey nodeKey(final MapReferenceId map, final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))));
    }

    static EnclosureHullKey hull(final MapReferenceId map, final String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }

    private static ProjectedNode node(final MapReferenceId map, final String id, final String label,
            final String mapName, final boolean graphGroup) {
        return ProjectedNode.of(nodeKey(map, id), SafeNodeLabel.of(label, label), mapName, graphGroup);
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey,
            final String label, final Optional<EnclosureHullKey> parentHull,
            final List<ProjectedNodeKey> directNodes, final List<EnclosureHullKey> directEnclosures,
            final boolean mapRoot, final BoundaryTier tier, final String mapName) {
        return ProjectedEnclosure.of(hullKey, hullKey.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), mapName, parentHull, directNodes,
            directEnclosures, mapRoot, tier);
    }

    private static PinProjection pin(final ProjectedNodeKey node, final double x, final double y) {
        final PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }
}

package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
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
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Headless label-readability probe (T010). Runs against either the baseline or the
 * post-change layout classes because it copies the A-H fixture builders and uses only
 * unchanged projection/geometry APIs.
 */
public final class LabelGateProbe {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE = MapReferenceId.of("00000000-0000-0000-0000-000000000003");
    private static final MapReferenceId MAP_FOUR = MapReferenceId.of("00000000-0000-0000-0000-000000000004");
    private static final char[] FIXTURE_NAMES = new char[] {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'};

    public static void main(final String[] args) throws Exception {
        final GeometryTextMetrics metrics = new AwtGeometryTextMetrics(
            new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));
        final LabelFonts fonts = LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
        for (final char name : FIXTURE_NAMES) {
            final GraphProjection projection = fixture(name);
            final GraphGeometry geometry;
            final LayoutPositions endpoint;
            final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
            try {
                LayoutFrame frame = await(worker.submit(LayoutRequest.of(WORKSPACE, projection,
                    ProjectionDiff.between(projection, projection), fixturePins(name))));
                for (int step = 1; step <= 2000; step++) {
                    frame = await(worker.step());
                    if (frame.idle().idle()) {
                        break;
                    }
                }
                for (int step = 0; step < 200; step++) {
                    frame = await(worker.step());
                }
                endpoint = frame.positions();
            }
            finally {
                worker.close();
            }
            geometry = new GraphGeometryEngine().computeHulls(projection, endpoint, metrics);
            final double[] center = worldCenter(endpoint);
            for (final double zoom : new double[] {1.0, 4.0}) {
                final Rectangle2D area = new Rectangle2D.Double(0.0, 0.0, 1440.0, 900.0);
                final LabelPlacementRequest request = LabelPlacementRequest.of(projection, geometry,
                    endpoint, zoom, center[0], center[1], area,
                    Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL);
                final List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null, fonts,
                    Collections.<Rectangle2D>emptyList());
                printLine(name, zoom, placed, geometry);
            }
        }
    }

    private static LayoutFrame await(final CompletionStage<LayoutFrame> stage) throws Exception {
        return stage.toCompletableFuture().get(10L, TimeUnit.SECONDS);
    }

    private static double[] worldCenter(final LayoutPositions positions) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : positions.nodes().values()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        for (final LayoutPoint point : positions.anchors().values()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        if (!Double.isFinite(minX)) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {(minX + maxX) * 0.5, (minY + maxY) * 0.5};
    }

    private static void printLine(final char name, final double zoom, final List<PlacedLabel> placed,
            final GraphGeometry geometry) {
        int overlappingPairs = 0;
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                if (placed.get(i).bounds().intersects(placed.get(j).bounds())) {
                    overlappingPairs++;
                }
            }
        }
        int leaders = 0;
        int footprintLeaders = 0;
        double maxLeader = 0.0;
        double sumLeader = 0.0;
        double sumRatio = 0.0;
        for (final PlacedLabel label : placed) {
            if (!label.leader().isPresent()) {
                continue;
            }
            final LeaderLine line = label.leader().get();
            final double length = Math.hypot(line.end().x() - line.start().x(),
                line.end().y() - line.start().y());
            leaders++;
            maxLeader = Math.max(maxLeader, length);
            sumLeader += length;
            final double[] footprint = footprint(geometry, label.endpoint());
            if (footprint != null && footprint[0] > 0.0 && footprint[1] > 0.0) {
                footprintLeaders++;
                sumRatio += length / Math.min(footprint[0], footprint[1]);
            }
        }
        final double meanLeader = leaders == 0 ? 0.0 : sumLeader / leaders;
        final double ratio = footprintLeaders == 0 ? 0.0 : sumRatio / footprintLeaders;
        System.out.printf("LABEL-GATE fixture=%s zoom=%.1f labels=%d overlappingLabelPairs=%d "
                + "maxLeaderLength=%.1f meanLeaderLength=%.1f leaderLengthOverFootprint=%.2f%n",
            Character.valueOf(name), Double.valueOf(zoom), Integer.valueOf(placed.size()),
            Integer.valueOf(overlappingPairs), Double.valueOf(maxLeader), Double.valueOf(meanLeader),
            Double.valueOf(ratio));
    }

    private static double[] footprint(final GraphGeometry geometry, final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            return node == null ? null : new double[] {2.0 * node.radius(), 2.0 * node.radius()};
        }
        final EnclosureKey enclosure = endpoint.enclosure().get();
        for (final Map.Entry<EnclosureHullKey, HullGeometry> entry : geometry.hulls().entrySet()) {
            if (entry.getKey().endpointKeys().contains(enclosure)) {
                final HullGeometry hull = entry.getValue();
                return new double[] {hull.maxX() - hull.minX(), hull.maxY() - hull.minY()};
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- fixtures

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

    // ---------------------------------------------------------------- helpers

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

    private static List<PinProjection> fixturePins(final char name) {
        if (name == 'C') {
            return Collections.singletonList(pin(nodeKey(MAP_TWO, "b-n0"), 60.0, 20.0));
        }
        return Collections.<PinProjection>emptyList();
    }

    private static GraphProjection fixture(final char name) {
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
}

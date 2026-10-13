package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
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

/** Scratch probe: packed placements vs settled anchors, node drift, painted hulls. */
public final class SkeletonProbe {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    public static void main(String[] args) throws Exception {
        System.out.println("proto.packingMultiplier=" + System.getProperty("proto.packingMultiplier", "<default>")
            + " proto.boundary=" + System.getProperty("proto.boundary", "box"));
        run("SCENARIO A (root > zfc > axioms + definitions)",
            ReferenceRepulsionFixture.referenceProjection(1L));
        run("SCENARIO B (two maps)", twoMapProjection(1L));
        run("SCENARIO C (two maps, cross-map relationships, node pin at (60,20))", relationshipProjection(1L),
            Collections.singletonList(pin(ProjectedNodeKey.of(source(MAP_TWO, "b-n0")), 60.0, 20.0)));
        run("SCENARIO D (two maps, cross-map relationships, no pin)", relationshipProjection(1L));
    }

    private static void run(String title, GraphProjection projection) throws Exception {
        run(title, projection, Collections.<PinProjection>emptyList());
    }

    private static void run(String title, GraphProjection projection, List<PinProjection> pins)
            throws Exception {
        System.out.println();
        System.out.println("### " + title);
        final Object sizes = boundarySizes(projection);
        System.out.printf("%-34s %10s %10s%n", "estimated footprint", "w", "h");
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            final Object size = invoke(sizes, "sizeOf", EnclosureHullKey.class, enclosure.hullKey());
            System.out.printf("%-34s %10.1f %10.1f%n", label(enclosure),
                field(size, "width"), field(size, "height"));
        }
        try {
            System.out.println("packed child offsets (from parent anchor origin):");
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                final List<?> placements =
                    (List<?>) invoke(sizes, "placements", EnclosureHullKey.class, enclosure.hullKey());
                for (final Object placement : placements) {
                    final Object item = objectField(placement, "item");
                    final Object anchorKey = objectField(item, "anchorKey");
                    System.out.printf("   %-34s %-26s (%7.1f, %7.1f)%n", label(enclosure),
                        anchorKey == null ? "<content>" : compact(anchorKey.toString()),
                        field(placement, "x"), field(placement, "y"));
                }
            }
            final List<?> tops = (List<?>) invokeNoArg(sizes, "topLevelPlacements");
            for (final Object placement : tops) {
                final Object item = objectField(placement, "item");
                System.out.printf("   %-34s %-26s (%7.1f, %7.1f)%n", "<top level>",
                    compact(String.valueOf(objectField(item, "anchorKey"))),
                    field(placement, "x"), field(placement, "y"));
            }
        }
        catch (final Exception exception) {
            System.out.println("packed placements: n/a (" + exception.getClass().getSimpleName() + ")");
        }

        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            CompletionStage<LayoutFrame> submitted = worker.submit(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins));
            LayoutFrame frame = submitted.toCompletableFuture().get(5L, TimeUnit.SECONDS);
            int idleStep = -1;
            for (int step = 1; step <= 2000; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
                if (frame.idle().idle()) {
                    idleStep = step;
                    break;
                }
            }
            for (int step = 0; step < 200; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
            }
            System.out.println("idle at step " + idleStep + " (+200 extra steps observed)");
            final LayoutPositions positions = frame.positions();
            final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(projection.enclosures());
            System.out.println("settled anchor distances:");
            for (int first = 0; first < enclosures.size(); first++) {
                for (int second = first + 1; second < enclosures.size(); second++) {
                    final LayoutPoint a = positions.anchors().get(enclosures.get(first).hullKey());
                    final LayoutPoint b = positions.anchors().get(enclosures.get(second).hullKey());
                    System.out.printf("   %-26s <-> %-26s %8.1f%n", label(enclosures.get(first)),
                        label(enclosures.get(second)), Math.hypot(a.x() - b.x(), a.y() - b.y()));
                }
            }
            System.out.println("direct node drift (settled distance from content centre vs ideal ring):");
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                if (enclosure.directNodes().isEmpty()) {
                    continue;
                }
                double centerX = positions.anchors().get(enclosure.hullKey()).x();
                double centerY = positions.anchors().get(enclosure.hullKey()).y();
                try {
                    final Object content = invoke(sizes, "contentPlacement", EnclosureHullKey.class,
                        enclosure.hullKey());
                    if (content != null) {
                        centerX += field(content, "x");
                        centerY += field(content, "y");
                    }
                }
                catch (final Exception ignored) {
                    // baseline
                }
                final double ring = (Double) invoke(sizes, "contentRingRadius", EnclosureHullKey.class,
                    enclosure.hullKey());
                double max = 0.0;
                for (ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                    final LayoutPoint node = positions.nodes().get(nodeKey);
                    max = Math.max(max, Math.hypot(node.x() - centerX, node.y() - centerY));
                }
                System.out.printf("   %-30s ideal %6.1f  max settled %6.1f  drift %+6.1f%n",
                    label(enclosure), ring, max, max - ring);
            }
            final GeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
                new FontRenderContext(null, true, true));
            final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, metrics);
            final Map<EnclosureHullKey, double[]> painted = new LinkedHashMap<EnclosureHullKey, double[]>();
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                painted.put(enclosure.hullKey(), bbox(geometry.hulls().get(enclosure.hullKey())));
            }
            System.out.println("painted hull sizes and gaps:");
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                final double[] box = painted.get(enclosure.hullKey());
                System.out.printf("   %-30s w %7.1f h %7.1f%n", label(enclosure), box[2] - box[0],
                    box[3] - box[1]);
            }
            for (int first = 0; first < enclosures.size(); first++) {
                for (int second = first + 1; second < enclosures.size(); second++) {
                    System.out.printf("   gap %-26s <-> %-26s %8.1f%n", label(enclosures.get(first)),
                        label(enclosures.get(second)),
                        gap(painted.get(enclosures.get(first).hullKey()),
                            painted.get(enclosures.get(second).hullKey())));
                }
            }
        }
        finally {
            worker.close();
        }
    }

    private static String label(ProjectedEnclosure enclosure) {
        final StringBuilder result = new StringBuilder();
        for (SafeNodeLabel label : enclosure.labels()) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(label.displayText());
        }
        return result.toString();
    }

    private static String compact(String value) {
        if (value.length() <= 24) {
            return value;
        }
        return value.substring(0, 21) + "...";
    }

    private static double[] bbox(HullGeometry hull) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (LayoutPoint point : hull.exactPolygon()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    private static double gap(double[] first, double[] second) {
        final double dx = Math.max(first[0] - second[2], second[0] - first[2]);
        final double dy = Math.max(first[1] - second[3], second[1] - first[3]);
        return Math.max(dx, dy);
    }

    private static Object boundarySizes(GraphProjection projection) throws Exception {
        final Class<?> type = Class.forName(
            "org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutEngine$BoundarySizes");
        final Constructor<?> constructor = type.getDeclaredConstructor(GraphProjection.class);
        constructor.setAccessible(true);
        return constructor.newInstance(projection);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument)
            throws Exception {
        final Method method = target.getClass().getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        return method.invoke(target, argument);
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        final Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static double field(Object target, String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static Object objectField(Object target, String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                final String nodeLabel = "Theorem: " + prefix + " statement " + i + " with a long label";
                nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(nodeLabel, nodeLabel), "Map", false));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)),
                "Map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
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
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                final String nodeLabel = "Theorem: " + prefix + " statement " + i + " with a long label";
                nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(nodeLabel, nodeLabel), "Map", false));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)),
                "Map", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
        }
        for (int i = 0; i < 3; i++) {
            final long sequence = i + 1L;
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(String.format("20000000-0000-0000-0000-%012d", Long.valueOf(sequence))),
                sequence,
                source(MAP_ONE, "a-n" + i).persistedReference().get(),
                source(MAP_TWO, "b-n" + i).persistedReference().get(),
                RelationshipDirection.FORWARD,
                Collections.<UnknownXml>emptyList());
            final ProjectedEndpointKey sourceEndpoint =
                ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP_ONE, "a-n" + i)));
            final ProjectedEndpointKey targetEndpoint =
                ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP_TWO, "b-n" + i)));
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
                Collections.singletonList(
                    EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint))));
        }
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        final PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private SkeletonProbe() {
    }
}

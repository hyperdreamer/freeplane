package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Scratch measurement probe: estimated footprint (BoundarySizes.sizeOf) versus painted hull
 * geometry after the layout settles. Not part of the product.
 */
public final class ClusterProbe {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    public static void main(String[] args) throws Exception {
        System.out.println("### SCENARIO A: reference fixture (root > zfc > axioms + definitions)");
        run(ReferenceRepulsionFixture.referenceProjection(1L),
            pair("Axioms", "Definitions"));

        System.out.println();
        System.out.println("### SCENARIO B: two maps with heterogeneous subgroup sizes");
        run(twoMapProjection(1L),
            pair("fundamentals", "fundamentals"),
            pair("Map a", "Map b"));
    }

    private static void run(GraphProjection projection, String[]... pairs) throws Exception {
        System.out.printf("%-24s %10s %10s %10s%n", "enclosure", "estW", "estH", "estRad");
        Object sizes = boundarySizes(projection);
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            Object size = invoke(sizes, "sizeOf", EnclosureHullKey.class, enclosure.hullKey());
            double w = field(size, "width");
            double h = field(size, "height");
            double r = (Double) invoke(sizes, "boundaryRadius", EnclosureHullKey.class, enclosure.hullKey());
            System.out.printf("%-24s %10.1f %10.1f %10.1f%n", label(enclosure), w, h, r);
        }

        LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            CompletionStage<LayoutFrame> submitted = worker.submit(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList()));
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

            LayoutPositions positions = frame.positions();
            GeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
                new FontRenderContext(null, true, true));
            GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, metrics);

            Map<EnclosureHullKey, double[]> painted = new LinkedHashMap<EnclosureHullKey, double[]>();
            System.out.printf("%-24s %10s %10s %22s%n", "painted hull", "w", "h", "bbox center");
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                double[] box = bbox(geometry.hulls().get(enclosure.hullKey()));
                painted.put(enclosure.hullKey(), box);
                System.out.printf("%-24s %10.1f %10.1f %22s%n", label(enclosure),
                    box[2] - box[0], box[3] - box[1],
                    String.format("(%.1f, %.1f)", (box[0] + box[2]) / 2.0, (box[1] + box[3]) / 2.0));
            }

            System.out.println("painted hull gaps for requested pairs:");
            for (String[] p : pairs) {
                double[] first = findBox(projection, painted, p[0]);
                double[] second = findBox(projection, painted, p[1]);
                System.out.printf("  %-24s gap = %8.1f units%n",
                    p[0] + " <-> " + p[1], gap(first, second));
                System.out.printf("      first  box x[%.1f..%.1f] y[%.1f..%.1f]%n", first[0], first[2], first[1], first[3]);
                System.out.printf("      second box x[%.1f..%.1f] y[%.1f..%.1f]%n", second[0], second[2], second[1], second[3]);
            }
        }
        finally {
            worker.close();
        }
    }

    private static double[] findBox(GraphProjection projection, Map<EnclosureHullKey, double[]> painted, String needle) {
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (label(enclosure).toLowerCase().contains(needle.toLowerCase())) {
                return painted.get(enclosure.hullKey());
            }
        }
        throw new IllegalArgumentException("unknown enclosure " + needle);
    }

    private static String label(ProjectedEnclosure enclosure) {
        StringBuilder result = new StringBuilder();
        for (SafeNodeLabel label : enclosure.labels()) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(label.displayText());
        }
        return result.toString();
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
        double dx = Math.max(first[0] - second[2], second[0] - first[2]);
        double dy = Math.max(first[1] - second[3], second[1] - first[3]);
        return Math.max(dx, dy);
    }

    private static Object boundarySizes(GraphProjection projection) throws Exception {
        Class<?> type = Class.forName(
            "org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutEngine$BoundarySizes");
        Constructor<?> constructor = type.getDeclaredConstructor(GraphProjection.class);
        constructor.setAccessible(true);
        return constructor.newInstance(projection);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        return method.invoke(target, argument);
    }

    private static double field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static String[] pair(String first, String second) {
        return new String[] {first, second};
    }

    private static GraphProjection twoMapProjection(long generation) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO};
        for (int m = 0; m < maps.length; m++) {
            MapReferenceId map = maps[m];
            String prefix = m == 0 ? "a" : "b";
            int nodeCount = m == 0 ? 6 : 2;
            EnclosureHullKey rootHull = hull(map, prefix + "-root");
            EnclosureHullKey subHull = hull(map, prefix + "-sub");
            List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                String nodeLabel = "Theorem: " + prefix + " statement " + i + " with a long label";
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

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private ClusterProbe() {
    }
}

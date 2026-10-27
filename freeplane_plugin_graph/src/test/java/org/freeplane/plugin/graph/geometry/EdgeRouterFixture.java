package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

/**
 * Shared routing fixture. The drawn radii are pinned to the values the specification's
 * exact router fixtures assume (§8.1-§8.3): 14.0 world units, the largest drawn radius
 * ({@code BASE_RADIUS * MAX_SCALE}), and 12.0/14.0 for the fan-crossing fixture. The
 * engine's prominence-derived radii are not used because the fixture's undirected
 * connectors give every node the base scale.
 */
final class EdgeRouterFixture {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final double PINNED_RADIUS = 14.0;
    private static final double FAN_NODE_RADIUS = 12.0;
    private static final double FAN_OBSTACLE_RADIUS = 14.0;

    final GraphProjection projection;
    final LayoutPositions positions;
    final GraphGeometry geometry;
    final Set<ProjectedEndpointKey> visibleEndpoints;

    private EdgeRouterFixture(final GraphProjection projection, final LayoutPositions positions,
            final Map<ProjectedNodeKey, Double> radii) {
        this(projection, positions, radii, Collections.<EnclosureHullKey, HullGeometry>emptyMap());
    }

    private EdgeRouterFixture(final GraphProjection projection, final LayoutPositions positions,
            final Map<ProjectedNodeKey, Double> radii,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        this.projection = projection;
        this.positions = positions;
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (final ProjectedNode node : projection.nodes()) {
            final LayoutPoint center = positions.nodes().get(node.key());
            if (center == null) {
                continue;
            }
            final Double radius = radii.get(node.key());
            nodeGeometry.put(node.key(), NodeGeometry.of(center,
                radius == null ? PINNED_RADIUS : radius.doubleValue()));
        }
        this.geometry = GraphGeometry.of(nodeGeometry, hulls);
        this.visibleEndpoints = ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(),
            projection.enclosures());
    }

    static EdgeRouterFixture empty() {
        return new EdgeRouterFixture(GraphProjection.structure(1L,
            Collections.<ProjectedNode>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList()),
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    static EdgeRouterFixture reportedConvexQuad() {
        final ProjectedNodeKey a = node("ID_A");
        final ProjectedNodeKey b = node("ID_B");
        final ProjectedNodeKey c = node("ID_C");
        final ProjectedNodeKey d = node("ID_D");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, c, d), Arrays.asList(
            edge(a, c), edge(b, d))), LayoutPositions.of(
            points(a, 272.0, 170.0, b, 307.0, 202.0, c, 275.0, 240.0, d, 240.0, 208.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    static EdgeRouterFixture laterChordBow() {
        final ProjectedNodeKey one = node("ID_1");
        final ProjectedNodeKey two = node("ID_2");
        final ProjectedNodeKey three = node("ID_3");
        final ProjectedNodeKey four = node("ID_4");
        return new EdgeRouterFixture(projection(Arrays.asList(one, two, three, four), Arrays.asList(
            edge(one, two), edge(three, four))), LayoutPositions.of(
            points(one, -90.0, 0.0, two, 90.0, 0.0, three, 0.0, -25.0, four, 0.0, 25.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    static EdgeRouterFixture fanCrossing() {
        final ProjectedNodeKey x = node("X");
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey o = node("O");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        radii.put(x, Double.valueOf(FAN_NODE_RADIUS));
        radii.put(a, Double.valueOf(FAN_NODE_RADIUS));
        radii.put(b, Double.valueOf(FAN_NODE_RADIUS));
        radii.put(o, Double.valueOf(FAN_OBSTACLE_RADIUS));
        return new EdgeRouterFixture(projection(Arrays.asList(x, a, b, o), Arrays.asList(
            edge(x, a), edge(x, b))), LayoutPositions.of(
            points(x, 0.0, 0.0, a, 12.997, 48.089, b, 38.39, 23.412, o, 4.808, 23.714),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    static EdgeRouterFixture offMidpointObstacle() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey n = node("N");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        for (final ProjectedNodeKey key : Arrays.asList(a, b, n)) {
            radii.put(key, Double.valueOf(8.0));
        }
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, n),
            Collections.singletonList(edge(a, b))), LayoutPositions.of(
            points(a, -18.091, 0.0, b, 18.091, 0.0, n, -5.0455, 0.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    static EdgeRouterFixture midpointOrdering() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey n = node("N");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        for (final ProjectedNodeKey key : Arrays.asList(a, b, n)) {
            radii.put(key, Double.valueOf(8.0));
        }
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, n),
            Collections.singletonList(edge(a, b))), LayoutPositions.of(
            points(a, -50.0, 0.0, b, 50.0, 0.0, n, 0.0, 0.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    static EdgeRouterFixture maxBendObstacle() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey n = node("N");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        for (final ProjectedNodeKey key : Arrays.asList(a, b, n)) {
            radii.put(key, Double.valueOf(8.0));
        }
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, n),
            Collections.singletonList(edge(a, b))), LayoutPositions.of(
            points(a, -100.0, 0.0, b, 100.0, 0.0, n, -84.5, 0.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    static EdgeRouterFixture controlPositionBarrier() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey n = node("N");
        final ProjectedNodeKey z0 = node("Z0");
        final ProjectedNodeKey z1 = node("Z1");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        for (final ProjectedNodeKey key : Arrays.asList(a, b, n, z0, z1)) {
            radii.put(key, Double.valueOf(8.0));
        }
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, n, z0, z1),
            Arrays.asList(edge(a, b), edge(z0, z1))), LayoutPositions.of(
            points(a, -18.091, 0.0, b, 18.091, 0.0, n, -5.0455, 0.0,
                z0, 0.0, -100.0, z1, 0.0, 100.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    /**
     * F-1 fixture: one edge whose straight chord and every live bow pass within the
     * clearance of one of two mirrored obstacle discs while the live same-side dogleg 0.30
     * keeps clear. The discs at {@code (60.44, +/-13.77)} with radius 12.0 put the 15.0
     * clearance threshold between the farthest blocked path (bow 0.50, 13.788) and the
     * accepted dogleg 0.30 (16.229), so no live bow or flatter dogleg can be selected and
     * an alternating-bend dogleg would cross a disc instead of clearing.
     */
    static EdgeRouterFixture doglegAccepting() {
        final ProjectedNodeKey a = node("ID_A");
        final ProjectedNodeKey b = node("ID_B");
        final ProjectedNodeKey upper = node("ID_OBSTACLE_UPPER");
        final ProjectedNodeKey lower = node("ID_OBSTACLE_LOWER");
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        radii.put(upper, Double.valueOf(12.0));
        radii.put(lower, Double.valueOf(12.0));
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, upper, lower), Arrays.asList(
            edge(a, b))), LayoutPositions.of(
            points(a, 0.0, 0.0, b, 100.0, 0.0, upper, 60.44, 13.77, lower, 60.44, -13.77),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
    }

    /**
     * F-2 fixture: a two-edge fan sharing X whose straight rungs are 2.786 world units
     * apart at their shared-X attachments, below {@code EdgeRouter.EDGE_CLEARANCE = 3.0},
     * so only the spec 6.4 fan exemption accepts them as straight instead of falling back
     * or detouring.
     */
    static EdgeRouterFixture tightFan() {
        final ProjectedNodeKey x = node("X");
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        return new EdgeRouterFixture(projection(Arrays.asList(x, a, b), Arrays.asList(
            edge(x, a), edge(x, b))), LayoutPositions.of(
            points(x, 0.0, 0.0, a, 100.0, -10.0, b, 100.0, 10.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    /**
     * The superseded reference-scale shape: {@code chords} diameters of a radius-600000 circle.
     * The adaptive query grid keeps the index and obstacle counters bounded while the straight
     * chords' pair tests reach the router pair-check cap.
     */
    static EdgeRouterFixture referenceScaleFan(final int chords) {
        return scaledDiameters(chords, 600000.0);
    }

    /** The same wide-span diameters with every rim disc examined by every straight chord. */
    static EdgeRouterFixture denseObstacleFan(final int chords) {
        final int side = (int) Math.ceil(Math.sqrt((double) chords));
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEdgeKey> edges = new ArrayList<ProjectedEdgeKey>();
        final List<Object> coordinates = new ArrayList<Object>();
        final double spacing = 40.0;
        int created = 0;
        for (int row = 0; row < side && created < chords; row++) {
            for (int column = 0; column < side && created < chords; column++) {
                final ProjectedNodeKey first = node("D" + created + "A");
                final ProjectedNodeKey second = node("D" + created + "B");
                keys.add(first);
                keys.add(second);
                edges.add(edge(first, second));
                final double x = column * spacing;
                final double y = row * spacing;
                coordinates.add(first);
                coordinates.add(Double.valueOf(x));
                coordinates.add(Double.valueOf(y));
                coordinates.add(second);
                coordinates.add(Double.valueOf(x));
                coordinates.add(Double.valueOf(y + 5.0));
                created++;
            }
        }
        for (int index = 0; index < chords; index++) {
            final ProjectedNodeKey key = node("O" + index);
            keys.add(key);
            coordinates.add(key);
            coordinates.add(Double.valueOf(1000.0 + (index % side) * spacing));
            coordinates.add(Double.valueOf((index / side) * spacing));
        }
        final ProjectedNodeKey far = node("Z_FAR");
        keys.add(far);
        coordinates.add(far);
        coordinates.add(Double.valueOf(243000.0));
        coordinates.add(Double.valueOf(243000.0));
        return new EdgeRouterFixture(projection(keys, edges),
            LayoutPositions.of(points(coordinates.toArray()),
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    private static EdgeRouterFixture scaledDiameters(final int chords, final double radius) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEdgeKey> edges = new ArrayList<ProjectedEdgeKey>();
        final Object[] coordinates = new Object[chords * 6];
        int cursor = 0;
        for (int index = 0; index < chords; index++) {
            final double theta = Math.PI * index / (double) chords;
            final ProjectedNodeKey first = node("C" + index + "A");
            final ProjectedNodeKey second = node("C" + index + "B");
            keys.add(first);
            keys.add(second);
            edges.add(edge(first, second));
            coordinates[cursor++] = first;
            coordinates[cursor++] = Double.valueOf(radius * Math.cos(theta));
            coordinates[cursor++] = Double.valueOf(radius * Math.sin(theta));
            coordinates[cursor++] = second;
            coordinates[cursor++] = Double.valueOf(-radius * Math.cos(theta));
            coordinates[cursor++] = Double.valueOf(-radius * Math.sin(theta));
        }
        return new EdgeRouterFixture(projection(keys, edges), LayoutPositions.of(points(coordinates),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    static EdgeRouterFixture oneResolvableEdgeAndOneBrokenEdge() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey missing = node("MISSING");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b), Arrays.asList(
            edge(a, b), edge(a, missing))), LayoutPositions.of(
            points(a, 0.0, 0.0, b, 100.0, 0.0), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    /**
     * F-1 index fixture: antipodal node pairs whose chords all meet exactly at the frame
     * centre, which is a query-grid cell corner (extent {@code 828 = 128 * 6.46875}, origin
     * {@code -414}); the traversal must keep the fifteen crossings.
     */
    static EdgeRouterFixture cellCornerFan() {
        final double[][] directions = {
            {400.0, 0.0}, {0.0, 400.0}, {300.0, 400.0},
            {400.0, -300.0}, {200.0, -380.0}, {380.0, 200.0}
        };
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEdgeKey> edges = new ArrayList<ProjectedEdgeKey>();
        final Object[] coordinates = new Object[directions.length * 6];
        int cursor = 0;
        for (int index = 0; index < directions.length; index++) {
            final ProjectedNodeKey positive = node("D" + index + "P");
            final ProjectedNodeKey negative = node("D" + index + "N");
            keys.add(positive);
            keys.add(negative);
            edges.add(edge(positive, negative));
            coordinates[cursor++] = positive;
            coordinates[cursor++] = Double.valueOf(directions[index][0]);
            coordinates[cursor++] = Double.valueOf(directions[index][1]);
            coordinates[cursor++] = negative;
            coordinates[cursor++] = Double.valueOf(-directions[index][0]);
            coordinates[cursor++] = Double.valueOf(-directions[index][1]);
        }
        return new EdgeRouterFixture(projection(keys, edges), LayoutPositions.of(
            points(coordinates), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    /**
     * F-1 proximity fixture: two parallel chords 2.9 world units apart, less than
     * {@code EDGE_CLEARANCE} but in adjacent cells of a one-unit grid; only a traversal
     * widened by the clearance ring sees the other path, so the first chord cannot stay
     * straight. Its endpoints are more than the disc clearance away from the other chord, so
     * the path rule is the only rule that can reject its straight rung.
     */
    static EdgeRouterFixture closeParallelPaths() {
        final ProjectedNodeKey a = node("A");
        final ProjectedNodeKey b = node("B");
        final ProjectedNodeKey c = node("C");
        final ProjectedNodeKey d = node("D");
        return new EdgeRouterFixture(projection(Arrays.asList(a, b, c, d), Arrays.asList(
            edge(a, b), edge(c, d))), LayoutPositions.of(
            points(a, 0.0, 0.0, b, 300.0, 0.0, c, -50.0, 2.9, d, 350.0, 2.9),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap());
    }

    /**
     * F-2 positive fixture: one hand-built hull the only edge's chord properly crosses
     * while neither endpoint is contained by it.
     */
    static EdgeRouterFixture hullCrossing() {
        return hullCrossing(Collections.<ProjectedEnclosure>emptyList());
    }

    /**
     * F-2 negative fixture: the same crossing chord, but the first endpoint is a direct child
     * of the crossed hull's enclosure, so spec 6.9 clause (b) marks it contained.
     */
    static EdgeRouterFixture hullCrossingWithContainedEndpoint() {
        final EnclosureHullKey hull = hull("HULL");
        final ProjectedEnclosure enclosure = ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("hull", "hull")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(node("ID_A")),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
        return hullCrossing(Collections.singletonList(enclosure));
    }

    private static EdgeRouterFixture hullCrossing(final List<ProjectedEnclosure> enclosures) {
        final ProjectedNodeKey a = node("ID_A");
        final ProjectedNodeKey b = node("ID_B");
        final EnclosureHullKey hull = hull("HULL");
        final Map<EnclosureHullKey, HullGeometry> hulls =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hull, HullGeometry.of(Arrays.asList(LayoutPoint.of(-30.0, -20.0),
            LayoutPoint.of(30.0, -20.0), LayoutPoint.of(30.0, 20.0),
            LayoutPoint.of(-30.0, 20.0)), LayoutPoint.of(0.0, 0.0)));
        return new EdgeRouterFixture(projection(Arrays.asList(a, b), Arrays.asList(edge(a, b)),
            enclosures), LayoutPositions.of(points(a, -100.0, 0.0, b, 100.0, 0.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            Collections.<ProjectedNodeKey, Double>emptyMap(), hulls);
    }

    /**
     * The same projection content with the edges list reversed, for the input-order
     * determinism test. The geometry stays keyed by the unchanged node keys.
     */
    GraphProjection permutedProjection() {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            keys.add(node.key());
        }
        final List<ProjectedEdgeKey> edges = new ArrayList<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : projection.edges()) {
            edges.add(edge.key());
        }
        Collections.reverse(edges);
        return projection(keys, edges);
    }

    static ProjectedEdgeKey exitEdge(final String first, final String second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(node(first)),
            ProjectedEndpointKey.ofNode(node(second)));
    }

    static ProjectedNodeKey node(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    /** Signed area (cross product) of two edge vectors; used to pin the dogleg side rule. */
    static double side(final LayoutPoint first, final LayoutPoint second, final LayoutPoint third) {
        return (second.x() - first.x()) * (third.y() - first.y())
            - (second.y() - first.y()) * (third.x() - first.x());
    }

    static double distance(final LayoutPoint first, final LayoutPoint second) {
        return Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    /**
     * The largest deviation between the true published quadratic and the sampled polyline,
     * evaluated at the parameter midpoint of every sample segment.
     */
    static double maxSagitta(final EdgePath path) {
        final List<LayoutPoint> points = path.definingPoints();
        final List<LayoutPoint> samples = path.samples();
        if (points.size() != 3 || samples.size() < 2) {
            return 0.0;
        }
        final LayoutPoint start = points.get(0);
        final LayoutPoint control = points.get(1);
        final LayoutPoint end = points.get(2);
        final int segments = samples.size() - 1;
        double worst = 0.0;
        for (int index = 0; index < segments; index++) {
            final double t = (index + 0.5) / (double) segments;
            final double oneMinusT = 1.0 - t;
            final LayoutPoint curve = LayoutPoint.of(
                oneMinusT * oneMinusT * start.x() + 2.0 * oneMinusT * t * control.x()
                    + t * t * end.x(),
                oneMinusT * oneMinusT * start.y() + 2.0 * oneMinusT * t * control.y()
                    + t * t * end.y());
            final LayoutPoint chordMidpoint = LayoutPoint.of(
                (samples.get(index).x() + samples.get(index + 1).x()) / 2.0,
                (samples.get(index).y() + samples.get(index + 1).y()) / 2.0);
            worst = Math.max(worst, distance(curve, chordMidpoint));
        }
        return worst;
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> keys,
            final List<ProjectedEdgeKey> edges) {
        return projection(keys, edges, Collections.<ProjectedEnclosure>emptyList());
    }

    private static GraphProjection projection(final List<ProjectedNodeKey> keys,
            final List<ProjectedEdgeKey> edges, final List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : keys) {
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(key.source().toString(),
                key.source().toString()), "map", false));
        }
        final List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        for (final ProjectedEdgeKey edge : edges) {
            final SourceNodeKey sourceKey = edge.first().node().get().source();
            final NodeReference targetReference = NodeReference.of(
                edge.second().node().get().source().mapReferenceId(),
                edge.second().node().get().source().persistedReference().get().nodeId());
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(sourceKey, targetReference,
                false, false, "source", "middle", "target");
            projectedEdges.add(ProjectedEdge.of(edge, Collections.singletonList(
                EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), edge.first(),
                    edge.second()))));
        }
        return GraphProjection.projected(1L, nodes, enclosures, projectedEdges,
            Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList());
    }

    private static EnclosureHullKey hull(final String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static ProjectedEdgeKey edge(final ProjectedNodeKey first, final ProjectedNodeKey second) {
        return ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(first), ProjectedEndpointKey.ofNode(second));
    }

    private static Map<ProjectedNodeKey, LayoutPoint> points(final Object... values) {
        final Map<ProjectedNodeKey, LayoutPoint> result = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < values.length; index += 3) {
            result.put((ProjectedNodeKey) values[index],
                LayoutPoint.of(((Double) values[index + 1]).doubleValue(),
                    ((Double) values[index + 2]).doubleValue()));
        }
        return result;
    }
}

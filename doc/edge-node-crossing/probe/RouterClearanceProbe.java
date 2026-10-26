package org.freeplane.plugin.graph.probe;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.EdgePath;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.EdgeRouter;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Decisive control for the disputed claim: on an isolated anchor chord of the reported length with a
 * radius-8 disc at its midpoint, does any legal EdgeRouter rung clear the disc?
 *
 * <p>Three discs marked as graph groups: A, B (the edge endpoints) and N (the obstruction). The
 * obstacle sits on the drawn chord, not on the anchor chord, because that is what a visible crossing
 * requires. Several obstacle placements are swept so the answer is not an artefact of one choice.
 *
 * <p>Test-only investigation source. Not part of the delivered bundle.
 */
public final class RouterClearanceProbe {
    private static final List<UnknownXml> NO_UNKNOWN_XML = Collections.emptyList();
    private static final int SAMPLES = 4000;

    private RouterClearanceProbe() {
    }

    public static void main(final String[] args) {
        final MapReferenceId mapId = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(
            new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

        // Anchor chord values measured on the reported graph, centre to centre.
        final double[] chords = {36.182, 33.596, 41.170, 44.120};
        for (final double chord : chords) {
            System.out.printf(Locale.ROOT, "=== anchor chord %.3f wu, radius 8 endpoints ===%n", Double.valueOf(chord));
            for (int variant = 0; variant < 3; variant++) {
                // variant 0: obstacle at the midpoint of the drawn chord (disc boundary midpoints)
                // variant 1: obstacle at the centroid of the anchor chord
                // variant 2: obstacle 25% along the drawn chord
                final double[] obstacle = obstacleFor(chord, variant);
                final Outcome outcome = run(mapId, metrics, chord, obstacle);
                System.out.printf(Locale.ROOT,
                    "  obstacle at (%.3f, %.3f) [%s]: shape=%s fallback=%s clearance=%s%n",
                    Double.valueOf(obstacle[0]), Double.valueOf(obstacle[1]), labelOf(variant),
                    outcome.shape, Boolean.valueOf(outcome.fallback),
                    outcome.clearance == null ? "n/a" : String.format(Locale.ROOT, "%.6f", outcome.clearance));
            }
        }
    }

    private static String labelOf(final int variant) {
        switch (variant) {
            case 0: return "midpoint of drawn chord";
            case 1: return "anchor midpoint";
            default: return "quarter along drawn chord";
        }
    }

    private static double[] obstacleFor(final double chord, final int variant) {
        final double half = chord / 2.0;
        final double drawnStart = -half + 8.0;
        final double drawnEnd = half - 8.0;
        switch (variant) {
            case 0: return new double[] {(drawnStart + drawnEnd) / 2.0, 0.0};
            case 1: return new double[] {0.0, 0.0};
            default: return new double[] {drawnStart + 0.25 * (drawnEnd - drawnStart), 0.0};
        }
    }

    private static Outcome run(final MapReferenceId mapId, final AwtGeometryTextMetrics metrics,
            final double chord, final double[] obstacle) {
        final WorkspaceId workspaceId = WorkspaceId.of(UUID.nameUUIDFromBytes(
            "router-clearance-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        final Map<String, double[]> centres = new LinkedHashMap<String, double[]>();
        centres.put("ID_A", new double[] {-chord / 2.0, 0.0});
        centres.put("ID_B", new double[] {chord / 2.0, 0.0});
        centres.put("ID_N", obstacle);

        final List<NodeSnapshot> children = new ArrayList<NodeSnapshot>();
        final Set<PersistedNodeId> ids = new LinkedHashSet<PersistedNodeId>();
        for (final String id : centres.keySet()) {
            final NodeReference reference = NodeReference.of(mapId, PersistedNodeId.of(id));
            children.add(NodeSnapshot.of(SourceNodeKey.persisted(reference),
                SafeNodeLabel.of(id, id), true, true, false,
                new ArrayList<NodeSnapshot>()));
            ids.add(PersistedNodeId.of(id));
        }
        final NodeReference rootReference = NodeReference.of(mapId, PersistedNodeId.of("ID_ROOT"));
        ids.add(PersistedNodeId.of("ID_ROOT"));
        final NodeSnapshot root = NodeSnapshot.of(SourceNodeKey.persisted(rootReference),
            SafeNodeLabel.of("root", "root"), false, false, false, children);

        final List<ConnectorSnapshot> connectors = new ArrayList<ConnectorSnapshot>();
        connectors.add(ConnectorSnapshot.of(0, ConnectorDescriptor.of(
            SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of("ID_A"))),
            NodeReference.of(mapId, PersistedNodeId.of("ID_B")), false, true, "", "", "")));

        final MapSnapshot snapshot = MapSnapshot.of(mapId, 1, "probe", root, ids, false)
            .withConnectors(connectors);
        final List<MapReference> registrations = new ArrayList<MapReference>();
        registrations.add(MapReference.of(mapId, 1L, java.net.URI.create("maps/probe.mm"), true,
            "#4E79A7", NO_UNKNOWN_XML));
        final Map<MapReferenceId, MapAvailability> availability =
            new LinkedHashMap<MapReferenceId, MapAvailability>();
        availability.put(mapId, MapAvailability.AVAILABLE);
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(workspaceId).toBuilder()
            .maps(registrations)
            .relationships(new ArrayList<GraphRelationshipRecord>())
            .pins(new ArrayList<PinRecord>())
            .viewport(Viewport.of(0.0, 0.0, 1.0, NO_UNKNOWN_XML))
            .displaySettings(DisplaySettings.of(true, DisplaySettings.CanvasTheme.LIGHT, true, true,
                DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, NO_UNKNOWN_XML))
            .unknownXml(NO_UNKNOWN_XML)
            .build();

        final GraphProjection projection = new ProjectionEngine()
            .project(ProjectionInput.of(1L, document, Collections.singletonList(snapshot), availability));

        final Map<ProjectedNodeKey, LayoutPoint> nodePositions =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final ProjectedNode node : projection.nodes()) {
            final String id = node.key().source().persistedReference().get().nodeId().toString();
            final double[] centre = centres.get(id);
            nodePositions.put(node.key(), LayoutPoint.of(centre[0], centre[1]));
        }
        final Map<org.freeplane.plugin.graph.projection.EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<org.freeplane.plugin.graph.projection.EnclosureHullKey, LayoutPoint>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), LayoutPoint.of(0.0, 0.0));
        }
        final LayoutPositions positions = LayoutPositions.of(nodePositions, anchors);
        final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions,
            metrics);
        final Set<org.freeplane.plugin.graph.projection.ProjectedEndpointKey> visible =
            org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility.visibleEndpoints(
                projection.nodes(), projection.enclosures());
        final EdgePaths paths = new EdgeRouter().route(projection, positions, geometry, visible);

        final Outcome outcome = new Outcome();
        for (final ProjectedEdge edge : projection.edges()) {
            final EdgePath path = paths.path(edge.key()).orElse(null);
            outcome.shape = path == null ? "MISSING" : path.shape().name();
            outcome.fallback = paths.diagnostics().edgesFallback() > 0;
            double minimum = Double.MAX_VALUE;
            for (final ProjectedNode node : projection.nodes()) {
                final NodeGeometry disc = geometry.nodes().get(node.key());
                if (disc == null) {
                    continue;
                }
                final String id = node.key().source().persistedReference().get().nodeId().toString();
                if ("ID_A".equals(id) || "ID_B".equals(id)) {
                    continue;
                }
                minimum = Math.min(minimum, distance(path, disc));
            }
            outcome.clearance = minimum == Double.MAX_VALUE ? null : Double.valueOf(minimum);
        }
        return outcome;
    }

    /** Closest approach between the path and the disc surface, sampled densely on the path. */
    private static double distance(final EdgePath path, final NodeGeometry disc) {
        final List<LayoutPoint> samples = path.samples();
        double best = Double.MAX_VALUE;
        for (int index = 1; index < samples.size(); index++) {
            best = Math.min(best, segmentDistance(disc.center(), samples.get(index - 1),
                samples.get(index)));
        }
        final Double dense = polylineExtremes(path);
        if (dense != null) {
            best = Math.min(best, dense.doubleValue());
        }
        return best - disc.radius();
    }

    private static Double polylineExtremes(final EdgePath path) {
        // Denser resampling of the same defining points, to make sure the sampled list is not the limit.
        final List<LayoutPoint> defining = path.definingPoints();
        if (defining.size() == 3) {
            final LayoutPoint p0 = defining.get(0);
            final LayoutPoint c = defining.get(1);
            final LayoutPoint p2 = defining.get(2);
            double best = Double.MAX_VALUE;
            for (int i = 0; i <= SAMPLES; i++) {
                final double t = i / (double) SAMPLES;
                final double mt = 1.0 - t;
                final double x = mt * mt * p0.x() + 2 * mt * t * c.x() + t * t * p2.x();
                final double y = mt * mt * p0.y() + 2 * mt * t * c.y() + t * t * p2.y();
                best = Math.min(best, Math.hypot(x, y));
            }
            return Double.valueOf(best);
        }
        return null;
    }

    private static double segmentDistance(final LayoutPoint point, final LayoutPoint first,
            final LayoutPoint second) {
        final double dx = second.x() - first.x();
        final double dy = second.y() - first.y();
        final double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        double t = ((point.x() - first.x()) * dx + (point.y() - first.y()) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(point.x() - (first.x() + t * dx), point.y() - (first.y() + t * dy));
    }

    private static final class Outcome {
        private String shape = "n/a";
        private boolean fallback;
        private Double clearance;
    }
}

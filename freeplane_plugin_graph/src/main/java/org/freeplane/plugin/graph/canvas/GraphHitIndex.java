package org.freeplane.plugin.graph.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;

final class GraphHitIndex {
    private final List<EndpointEntry> nodes;
    private final List<EndpointEntry> hulls;
    private final List<EdgeEntry> edges;

    private GraphHitIndex(final List<EndpointEntry> nodes, final List<EndpointEntry> hulls,
            final List<EdgeEntry> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<EndpointEntry>(nodes));
        this.hulls = Collections.unmodifiableList(new ArrayList<EndpointEntry>(hulls));
        this.edges = Collections.unmodifiableList(new ArrayList<EdgeEntry>(edges));
    }

    static GraphHitIndex empty() {
        return new GraphHitIndex(Collections.<EndpointEntry>emptyList(),
            Collections.<EndpointEntry>emptyList(), Collections.<EdgeEntry>emptyList());
    }

    static GraphHitIndex from(final CanvasState state) {
        Objects.requireNonNull(state, "state");
        final GraphGeometry geometry = state.geometry();
        final List<EndpointEntry> nodeEntries = new ArrayList<EndpointEntry>();
        final List<EndpointEntry> hullEntries = new ArrayList<EndpointEntry>();
        final Map<ProjectedEndpointKey, LayoutPoint> centers =
            new HashMap<ProjectedEndpointKey, LayoutPoint>();

        for (final ProjectedNode node : state.projection().nodes()) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry == null) {
                continue;
            }
            final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofNode(node.key());
            nodeEntries.add(EndpointEntry.forNode(endpoint, nodeGeometry));
            centers.put(endpoint, nodeGeometry.center());
        }

        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            for (final EnclosureKey endpointKey : enclosure.endpointKeys()) {
                final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(endpointKey);
                hullEntries.add(EndpointEntry.forHull(endpoint, hull));
                final LayoutPoint anchor = enclosureAnchor(state, enclosure, hull);
                if (anchor != null) {
                    centers.put(endpoint, anchor);
                }
            }
        }

        final List<EdgeEntry> edgeEntries = new ArrayList<EdgeEntry>();
        for (final ProjectedEdge edge : state.projection().edges()) {
            final LayoutPoint firstCenter = centers.get(edge.first());
            final LayoutPoint secondCenter = centers.get(edge.second());
            if (firstCenter == null || secondCenter == null) {
                continue;
            }
            try {
                final LayoutPoint first = geometry.edgeAttachment(edge.first(), secondCenter);
                final LayoutPoint second = geometry.edgeAttachment(edge.second(), firstCenter);
                if (isFinite(first) && isFinite(second)) {
                    edgeEntries.add(new EdgeEntry(edge.key(), first, second));
                }
            }
            catch (final IllegalArgumentException exception) {
                // A partially rendered endpoint cannot provide a hit-testable edge.
            }
        }
        return new GraphHitIndex(nodeEntries, hullEntries, edgeEntries);
    }

    Optional<ProjectedEndpointKey> endpointAt(final LayoutPoint point) {
        Objects.requireNonNull(point, "point");
        ProjectedEndpointKey match = matchingEndpoint(nodes, point);
        if (match != null) {
            return Optional.of(match);
        }
        match = matchingEndpoint(hulls, point);
        return match == null ? Optional.<ProjectedEndpointKey>empty() : Optional.of(match);
    }

    Optional<ProjectedEdgeKey> edgeAt(final LayoutPoint point, final double worldTolerance) {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(worldTolerance) || worldTolerance < 0.0) {
            throw new IllegalArgumentException("worldTolerance must be finite and non-negative");
        }
        double bestDistance = Double.POSITIVE_INFINITY;
        ProjectedEdgeKey best = null;
        for (final EdgeEntry edge : edges) {
            final double distance = pointToSegmentDistance(point, edge.first, edge.second);
            if (!Double.isFinite(distance) || distance > worldTolerance) {
                continue;
            }
            if (best == null || distance < bestDistance
                    || (Double.compare(distance, bestDistance) == 0
                        && edge.key.compareTo(best) < 0)) {
                best = edge.key;
                bestDistance = distance;
            }
        }
        return best == null ? Optional.<ProjectedEdgeKey>empty() : Optional.of(best);
    }

    private static double pointToSegmentDistance(final LayoutPoint point,
            final LayoutPoint first, final LayoutPoint second) {
        final double scale = Math.max(Math.max(Math.abs(first.x()), Math.abs(first.y())),
            Math.max(Math.max(Math.abs(second.x()), Math.abs(second.y())),
                Math.max(Math.abs(point.x()), Math.abs(point.y()))));
        if (scale == 0.0) {
            return 0.0;
        }
        final double firstX = first.x() / scale;
        final double firstY = first.y() / scale;
        final double secondX = second.x() / scale;
        final double secondY = second.y() / scale;
        final double pointX = point.x() / scale;
        final double pointY = point.y() / scale;
        final double deltaX = secondX - firstX;
        final double deltaY = secondY - firstY;
        final double offsetX = pointX - firstX;
        final double offsetY = pointY - firstY;
        final double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (lengthSquared == 0.0) {
            return scale * Math.hypot(offsetX, offsetY);
        }
        final double projection = (offsetX * deltaX + offsetY * deltaY) / lengthSquared;
        final double position = Math.max(0.0, Math.min(1.0, projection));
        final double closestX = firstX + position * deltaX;
        final double closestY = firstY + position * deltaY;
        return scale * Math.hypot(pointX - closestX, pointY - closestY);
    }

    private static ProjectedEndpointKey matchingEndpoint(final List<EndpointEntry> entries,
            final LayoutPoint point) {
        ProjectedEndpointKey match = null;
        for (final EndpointEntry entry : entries) {
            if (entry.contains(point)
                    && (match == null || entry.endpoint.compareTo(match) < 0)) {
                match = entry.endpoint;
            }
        }
        return match;
    }

    private static LayoutPoint enclosureAnchor(final CanvasState state,
            final ProjectedEnclosure enclosure, final HullGeometry hull) {
        final LayoutPoint layoutAnchor = state.layout().positions().anchors().get(enclosure.hullKey());
        if (isFinite(layoutAnchor)) {
            return layoutAnchor;
        }
        final LayoutPoint labelAnchor = hull.labelAnchor();
        return isFinite(labelAnchor) ? labelAnchor : null;
    }

    private static boolean isFinite(final LayoutPoint point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    private static final class EndpointEntry {
        private final ProjectedEndpointKey endpoint;
        private final NodeGeometry node;
        private final HullGeometry hull;

        private EndpointEntry(final ProjectedEndpointKey endpoint, final NodeGeometry node,
                final HullGeometry hull) {
            this.endpoint = endpoint;
            this.node = node;
            this.hull = hull;
        }

        private static EndpointEntry forNode(final ProjectedEndpointKey endpoint,
                final NodeGeometry node) {
            return new EndpointEntry(endpoint, node, null);
        }

        private static EndpointEntry forHull(final ProjectedEndpointKey endpoint,
                final HullGeometry hull) {
            return new EndpointEntry(endpoint, null, hull);
        }

        private boolean contains(final LayoutPoint point) {
            return node == null ? hull.contains(point) : node.contains(point);
        }
    }

    private static final class EdgeEntry {
        private final ProjectedEdgeKey key;
        private final LayoutPoint first;
        private final LayoutPoint second;

        private EdgeEntry(final ProjectedEdgeKey key, final LayoutPoint first,
                final LayoutPoint second) {
            this.key = key;
            this.first = first;
            this.second = second;
        }
    }
}

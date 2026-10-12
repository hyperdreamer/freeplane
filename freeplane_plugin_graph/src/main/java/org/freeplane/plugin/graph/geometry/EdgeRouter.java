package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

/**
 * Publish-time bounded edge routing (spec 6). This scaffold publishes the canonical
 * straight-only route: every resolvable edge is drawn as its straight attachment chord and
 * the diagnostics report the straight-chord crossings it leaves behind. A later task
 * replaces the straight emission with the candidate ladder while this signature stays.
 */
public final class EdgeRouter {
    public EdgeRouter() {
    }

    public EdgePaths route(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        final Map<EnclosureKey, EnclosureHullKey> hullKeys = enclosureHullKeys(projection, geometry);
        final Map<String, ProjectedEdge> orderedEdges = new TreeMap<String, ProjectedEdge>();
        for (final ProjectedEdge edge : projection.edges()) {
            orderedEdges.put(CanonicalKeys.edge(edge.key()), edge);
        }
        final Map<ProjectedEdgeKey, EdgePath> byEdge = new LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        for (final ProjectedEdge edge : orderedEdges.values()) {
            final EdgePath path = straightPath(edge, hullKeys, positions, geometry, visibleEndpoints);
            if (path != null) {
                byEdge.put(edge.key(), path);
            }
        }
        final Set<String> crossingKeys = crossingPairKeys(byEdge);
        final EdgeRouteDiagnostics diagnostics = new EdgeRouteDiagnostics(byEdge.size(), 0, 0, 0,
            crossingKeys.size(), new ArrayList<String>(crossingKeys), 0);
        return EdgePaths.of(byEdge, diagnostics, new EdgeRouteTimings(0L, 0L, 0L));
    }

    public static boolean properlyCross(final EdgePath first, final EdgePath second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        final List<LayoutPoint> firstSamples = first.samples();
        final List<LayoutPoint> secondSamples = second.samples();
        for (int a = 0; a + 1 < firstSamples.size(); a++) {
            for (int b = 0; b + 1 < secondSamples.size(); b++) {
                if (properlyCrossSegments(firstSamples.get(a), firstSamples.get(a + 1),
                        secondSamples.get(b), secondSamples.get(b + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static EdgePath straightPath(final ProjectedEdge edge,
            final Map<EnclosureKey, EnclosureHullKey> hullKeys, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        if (!visibleEndpoints.contains(edge.first()) || !visibleEndpoints.contains(edge.second())) {
            return null;
        }
        final LayoutPoint firstAnchor = anchor(edge.first(), hullKeys, positions, geometry);
        final LayoutPoint secondAnchor = anchor(edge.second(), hullKeys, positions, geometry);
        if (firstAnchor == null || secondAnchor == null) {
            return null;
        }
        try {
            final LayoutPoint firstAttachment = geometry.edgeAttachment(edge.first(), secondAnchor);
            final LayoutPoint secondAttachment = geometry.edgeAttachment(edge.second(), firstAnchor);
            if (isFinite(firstAttachment) && isFinite(secondAttachment)) {
                return EdgePath.straight(firstAttachment, secondAttachment);
            }
        }
        catch (final IllegalArgumentException exception) {
            // A partially resolved endpoint has no drawable attachment.
        }
        return null;
    }

    private static LayoutPoint anchor(final ProjectedEndpointKey endpoint,
            final Map<EnclosureKey, EnclosureHullKey> hullKeys, final LayoutPositions positions,
            final GraphGeometry geometry) {
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            return node == null ? null : node.center();
        }
        final EnclosureHullKey hullKey = hullKeys.get(endpoint.enclosure().get());
        if (hullKey == null) {
            return null;
        }
        final LayoutPoint layoutAnchor = positions.anchors().get(hullKey);
        if (isFinite(layoutAnchor)) {
            return layoutAnchor;
        }
        final HullGeometry hull = geometry.hulls().get(hullKey);
        final LayoutPoint labelAnchor = hull == null ? null : hull.labelAnchor();
        return isFinite(labelAnchor) ? labelAnchor : null;
    }

    private static Map<EnclosureKey, EnclosureHullKey> enclosureHullKeys(final GraphProjection projection,
            final GraphGeometry geometry) {
        final Map<EnclosureKey, EnclosureHullKey> hullKeys =
            new LinkedHashMap<EnclosureKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey hullKey = enclosure.hullKey();
            if (geometry.hulls().get(hullKey) == null) {
                continue;
            }
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                hullKeys.put(endpoint, hullKey);
            }
        }
        return hullKeys;
    }

    private static Set<String> crossingPairKeys(final Map<ProjectedEdgeKey, EdgePath> byEdge) {
        final List<ProjectedEdgeKey> keys = new ArrayList<ProjectedEdgeKey>(byEdge.keySet());
        final Set<String> crossings = new TreeSet<String>();
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                if (properlyCross(byEdge.get(keys.get(first)), byEdge.get(keys.get(second)))) {
                    crossings.add(CanonicalKeys.edgePair(keys.get(first), keys.get(second)));
                }
            }
        }
        return crossings;
    }

    private static int orientationSign(final int exponent, final LayoutPoint p, final LayoutPoint q,
            final LayoutPoint r) {
        final double px = Math.scalb(p.x(), -exponent);
        final double py = Math.scalb(p.y(), -exponent);
        final double qx = Math.scalb(q.x(), -exponent);
        final double qy = Math.scalb(q.y(), -exponent);
        final double rx = Math.scalb(r.x(), -exponent);
        final double ry = Math.scalb(r.y(), -exponent);
        final double cross = (qx - px) * (ry - py) - (qy - py) * (rx - px);
        return cross == 0.0 ? 0 : Double.compare(cross, 0.0);
    }

    /**
     * The exponent of the dominant coordinate magnitude: every scaled coordinate then has
     * magnitude below 2, so no scaled product can overflow. Zeros carry no magnitude and
     * must not sink the exponent to {@code Math.getExponent(0.0) == -1023}.
     */
    private static int commonExponent(final LayoutPoint... points) {
        int exponent = Integer.MIN_VALUE;
        for (final LayoutPoint point : points) {
            exponent = Math.max(exponent, coordinateExponent(point.x()));
            exponent = Math.max(exponent, coordinateExponent(point.y()));
        }
        return exponent == Integer.MIN_VALUE ? 0 : exponent;
    }

    private static int coordinateExponent(final double value) {
        return value == 0.0 ? Integer.MIN_VALUE : Math.getExponent(value);
    }

    private static boolean properlyCrossSegments(final LayoutPoint a, final LayoutPoint b,
            final LayoutPoint c, final LayoutPoint d) {
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
    }

    private static boolean isFinite(final LayoutPoint point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y());
    }
}

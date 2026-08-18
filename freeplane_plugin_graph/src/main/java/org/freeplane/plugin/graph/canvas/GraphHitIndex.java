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
        final ScaledValue tolerance = ScaledValue.of(worldTolerance);
        ScaledValue bestDistance = null;
        ProjectedEdgeKey best = null;
        for (final EdgeEntry edge : edges) {
            final ScaledValue distance = pointToSegmentDistance(point, edge.first, edge.second);
            if (!distance.isFinite() || distance.compareTo(tolerance) > 0) {
                continue;
            }
            if (best == null || distance.compareTo(bestDistance) < 0
                    || (distance.compareTo(bestDistance) == 0
                        && edge.key.compareTo(best) < 0)) {
                best = edge.key;
                bestDistance = distance;
            }
        }
        return best == null ? Optional.<ProjectedEdgeKey>empty() : Optional.of(best);
    }

    private static ScaledValue pointToSegmentDistance(final LayoutPoint point,
            final LayoutPoint first, final LayoutPoint second) {
        final ScaledValue deltaX = ScaledValue.difference(second.x(), first.x());
        final ScaledValue deltaY = ScaledValue.difference(second.y(), first.y());
        final ScaledValue offsetX = ScaledValue.difference(point.x(), first.x());
        final ScaledValue offsetY = ScaledValue.difference(point.y(), first.y());
        final ScaledValue lengthSquared = deltaX.multiply(deltaX).add(deltaY.multiply(deltaY));
        if (lengthSquared.isZero()) {
            return ScaledValue.hypot(offsetX, offsetY);
        }

        final ScaledValue projectionNumerator = offsetX.multiply(deltaX)
            .add(offsetY.multiply(deltaY));
        if (projectionNumerator.signum() <= 0) {
            return ScaledValue.hypot(offsetX, offsetY);
        }
        final ScaledValue endOffsetX = ScaledValue.difference(point.x(), second.x());
        final ScaledValue endOffsetY = ScaledValue.difference(point.y(), second.y());
        final ScaledValue endProjectionNumerator = endOffsetX.multiply(deltaX)
            .add(endOffsetY.multiply(deltaY));
        if (endProjectionNumerator.signum() >= 0) {
            return ScaledValue.hypot(endOffsetX, endOffsetY);
        }

        final ScaledValue crossProduct = offsetX.multiply(deltaY)
            .subtract(offsetY.multiply(deltaX));
        return crossProduct.abs().divide(lengthSquared.squareRoot());
    }

    private static final class ScaledValue {
        private final double significand;
        private final int exponent;

        private ScaledValue(final double significand, final int exponent) {
            this.significand = significand;
            this.exponent = exponent;
        }

        private static ScaledValue difference(final double first, final double second) {
            return of(first).subtract(of(second));
        }

        private static ScaledValue of(final double value) {
            if (value == 0.0) {
                return new ScaledValue(0.0, 0);
            }
            final int exponent = binaryExponent(value);
            return new ScaledValue(Math.scalb(value, -exponent), exponent);
        }

        private static ScaledValue normalize(final double value, final int exponent) {
            if (value == 0.0) {
                return new ScaledValue(0.0, 0);
            }
            final int valueExponent = binaryExponent(value);
            return new ScaledValue(Math.scalb(value, -valueExponent), exponent + valueExponent);
        }

        private static ScaledValue hypot(final ScaledValue first, final ScaledValue second) {
            final int scale = Math.max(first.exponent, second.exponent);
            final double firstValue = first.isZero() ? 0.0
                : Math.scalb(first.significand, first.exponent - scale);
            final double secondValue = second.isZero() ? 0.0
                : Math.scalb(second.significand, second.exponent - scale);
            return normalize(Math.hypot(firstValue, secondValue), scale);
        }

        private ScaledValue add(final ScaledValue other) {
            if (isZero()) {
                return other;
            }
            if (other.isZero()) {
                return this;
            }
            final int scale = Math.max(exponent, other.exponent);
            final double value = Math.scalb(significand, exponent - scale)
                + Math.scalb(other.significand, other.exponent - scale);
            return normalize(value, scale);
        }

        private ScaledValue subtract(final ScaledValue other) {
            return add(other.negate());
        }

        private ScaledValue multiply(final ScaledValue other) {
            if (isZero() || other.isZero()) {
                return new ScaledValue(0.0, 0);
            }
            return normalize(significand * other.significand, exponent + other.exponent);
        }

        private ScaledValue divide(final ScaledValue other) {
            if (isZero()) {
                return new ScaledValue(0.0, 0);
            }
            return normalize(significand / other.significand, exponent - other.exponent);
        }

        private ScaledValue squareRoot() {
            if (isZero()) {
                return this;
            }
            int adjustedExponent = exponent;
            double adjustedSignificand = significand;
            if ((adjustedExponent & 1) != 0) {
                adjustedSignificand *= 2.0;
                adjustedExponent--;
            }
            return normalize(Math.sqrt(adjustedSignificand), adjustedExponent / 2);
        }

        private ScaledValue abs() {
            return significand < 0.0 ? negate() : this;
        }

        private ScaledValue negate() {
            return new ScaledValue(-significand, exponent);
        }

        private boolean isZero() {
            return significand == 0.0;
        }

        private int signum() {
            return significand < 0.0 ? -1 : isZero() ? 0 : 1;
        }

        private int compareTo(final ScaledValue other) {
            final int firstSign = signum();
            final int secondSign = other.signum();
            if (firstSign != secondSign) {
                return Integer.compare(firstSign, secondSign);
            }
            if (firstSign == 0) {
                return 0;
            }
            final int magnitude = exponent != other.exponent
                ? Integer.compare(exponent, other.exponent)
                : Double.compare(Math.abs(significand), Math.abs(other.significand));
            return firstSign * magnitude;
        }

        private boolean isFinite() {
            return Double.isFinite(finiteValue());
        }

        private double finiteValue() {
            return Math.scalb(significand, exponent);
        }

        private static int binaryExponent(final double value) {
            final double magnitude = Math.abs(value);
            final int exponent = Math.getExponent(magnitude);
            if (exponent != Double.MIN_EXPONENT - 1) {
                return exponent;
            }
            final long fraction = Double.doubleToRawLongBits(magnitude)
                & 0x000fffffffffffffL;
            return 63 - Long.numberOfLeadingZeros(fraction) - 1074;
        }
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

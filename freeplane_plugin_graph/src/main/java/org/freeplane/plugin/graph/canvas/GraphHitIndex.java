package org.freeplane.plugin.graph.canvas;

import java.math.BigInteger;
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
        final ExactValue toleranceSquared = ExactValue.of(worldTolerance).square();
        DistanceValue bestDistance = null;
        ProjectedEdgeKey best = null;
        for (final EdgeEntry edge : edges) {
            final DistanceValue distance = pointToSegmentDistance(point, edge.first, edge.second);
            if (!distance.isFinite() || distance.compareToSquared(toleranceSquared) > 0) {
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

    private static DistanceValue pointToSegmentDistance(final LayoutPoint point,
            final LayoutPoint first, final LayoutPoint second) {
        if (!isFinite(point)) {
            return DistanceValue.nonFinite();
        }
        final ExactValue deltaX = ExactValue.difference(second.x(), first.x());
        final ExactValue deltaY = ExactValue.difference(second.y(), first.y());
        final ExactValue offsetX = ExactValue.difference(point.x(), first.x());
        final ExactValue offsetY = ExactValue.difference(point.y(), first.y());
        final ExactValue lengthSquared = deltaX.square().add(deltaY.square());
        if (lengthSquared.signum() == 0) {
            return DistanceValue.squared(offsetX.square().add(offsetY.square()));
        }

        final ExactValue projectionNumerator = offsetX.multiply(deltaX)
            .add(offsetY.multiply(deltaY));
        if (projectionNumerator.signum() <= 0) {
            return DistanceValue.squared(offsetX.square().add(offsetY.square()));
        }
        final ExactValue endOffsetX = ExactValue.difference(point.x(), second.x());
        final ExactValue endOffsetY = ExactValue.difference(point.y(), second.y());
        final ExactValue endProjectionNumerator = endOffsetX.multiply(deltaX)
            .add(endOffsetY.multiply(deltaY));
        if (endProjectionNumerator.signum() >= 0) {
            return DistanceValue.squared(endOffsetX.square().add(endOffsetY.square()));
        }

        final ExactValue crossProduct = offsetX.multiply(deltaY)
            .subtract(offsetY.multiply(deltaX));
        return DistanceValue.ratio(crossProduct.square(), lengthSquared);
    }

    private static final class DistanceValue {
        private static final ExactValue ONE = ExactValue.of(1.0);
        private static final ExactValue MAX_DISTANCE_SQUARED = ExactValue.of(Double.MAX_VALUE).square();

        private final ExactValue numerator;
        private final ExactValue denominator;
        private final boolean finite;

        private DistanceValue(final ExactValue numerator, final ExactValue denominator,
                final boolean finite) {
            this.numerator = numerator;
            this.denominator = denominator;
            this.finite = finite;
        }

        private static DistanceValue nonFinite() {
            return new DistanceValue(null, null, false);
        }

        private static DistanceValue squared(final ExactValue value) {
            return new DistanceValue(value, ONE, true);
        }

        private static DistanceValue ratio(final ExactValue numerator, final ExactValue denominator) {
            return new DistanceValue(numerator, denominator, true);
        }

        private boolean isFinite() {
            return finite && compareToSquared(MAX_DISTANCE_SQUARED) <= 0;
        }

        private int compareToSquared(final ExactValue value) {
            return numerator.compareTo(value.multiply(denominator));
        }

        private int compareTo(final DistanceValue other) {
            return numerator.multiply(other.denominator)
                .compareTo(other.numerator.multiply(denominator));
        }
    }

    private static final class ExactValue {
        private final BigInteger significand;
        private final int exponent;

        private ExactValue(final BigInteger significand, final int exponent) {
            if (significand.signum() == 0) {
                this.significand = BigInteger.ZERO;
                this.exponent = 0;
                return;
            }
            final int trailingZeros = significand.abs().getLowestSetBit();
            this.significand = trailingZeros == 0 ? significand : significand.shiftRight(trailingZeros);
            this.exponent = exponent + trailingZeros;
        }

        private static ExactValue of(final double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("ExactValue requires a finite value");
            }
            if (value == 0.0) {
                return new ExactValue(BigInteger.ZERO, 0);
            }
            final long bits = Double.doubleToRawLongBits(value);
            final boolean negative = (bits & Long.MIN_VALUE) != 0L;
            final int biasedExponent = (int) ((bits >>> 52) & 0x7ffL);
            final long fraction = bits & 0x000fffffffffffffL;
            final BigInteger magnitude;
            final int exponent;
            if (biasedExponent == 0) {
                magnitude = BigInteger.valueOf(fraction);
                exponent = -1074;
            }
            else {
                magnitude = BigInteger.valueOf((1L << 52) | fraction);
                exponent = biasedExponent - 1023 - 52;
            }
            return new ExactValue(negative ? magnitude.negate() : magnitude, exponent);
        }

        private static ExactValue difference(final double first, final double second) {
            return of(first).subtract(of(second));
        }

        private ExactValue add(final ExactValue other) {
            if (significand.signum() == 0) {
                return other;
            }
            if (other.significand.signum() == 0) {
                return this;
            }
            final int commonExponent = Math.min(exponent, other.exponent);
            final BigInteger first = significand.shiftLeft(exponent - commonExponent);
            final BigInteger second = other.significand.shiftLeft(other.exponent - commonExponent);
            return new ExactValue(first.add(second), commonExponent);
        }

        private ExactValue subtract(final ExactValue other) {
            return add(other.negate());
        }

        private ExactValue multiply(final ExactValue other) {
            if (significand.signum() == 0 || other.significand.signum() == 0) {
                return new ExactValue(BigInteger.ZERO, 0);
            }
            return new ExactValue(significand.multiply(other.significand), exponent + other.exponent);
        }

        private ExactValue square() {
            return multiply(this);
        }

        private ExactValue abs() {
            return significand.signum() < 0 ? negate() : this;
        }

        private ExactValue negate() {
            return new ExactValue(significand.negate(), exponent);
        }

        private int signum() {
            return significand.signum();
        }

        private int compareTo(final ExactValue other) {
            final int firstSign = signum();
            final int secondSign = other.signum();
            if (firstSign != secondSign) {
                return Integer.compare(firstSign, secondSign);
            }
            if (firstSign == 0) {
                return 0;
            }
            final int magnitude = compareMagnitude(other);
            return firstSign < 0 ? -magnitude : magnitude;
        }

        private int compareMagnitude(final ExactValue other) {
            final BigInteger firstMagnitude = significand.abs();
            final BigInteger secondMagnitude = other.significand.abs();
            final int firstTopExponent = exponent + firstMagnitude.bitLength();
            final int secondTopExponent = other.exponent + secondMagnitude.bitLength();
            if (firstTopExponent != secondTopExponent) {
                return Integer.compare(firstTopExponent, secondTopExponent);
            }
            final int commonExponent = Math.min(exponent, other.exponent);
            final BigInteger first = firstMagnitude.shiftLeft(exponent - commonExponent);
            final BigInteger second = secondMagnitude.shiftLeft(other.exponent - commonExponent);
            return first.compareTo(second);
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

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
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;

public final class GraphTraversalOrder {
    public GraphTraversalOrder() {
    }

    public static List<ProjectedEndpointKey> tabOrder(final CanvasState state) {
        final Map<ProjectedEndpointKey, LayoutPoint> positions = positions(
            Objects.requireNonNull(state, "state"));
        final List<ProjectedEndpointKey> result = new ArrayList<ProjectedEndpointKey>(positions.keySet());
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public static Optional<ProjectedEndpointKey> nearest(final CanvasState state,
            final ProjectedEndpointKey from, final TraversalDirection direction) {
        final CanvasState value = Objects.requireNonNull(state, "state");
        final ProjectedEndpointKey source = Objects.requireNonNull(from, "from");
        final TraversalDirection requestedDirection = Objects.requireNonNull(direction, "direction");
        final Map<ProjectedEndpointKey, LayoutPoint> positions = positions(value);
        final LayoutPoint origin = positions.get(source);
        if (origin == null) {
            return Optional.empty();
        }
        ProjectedEndpointKey best = null;
        ExactValue bestDistance = null;
        for (Map.Entry<ProjectedEndpointKey, LayoutPoint> entry : positions.entrySet()) {
            final ProjectedEndpointKey candidate = entry.getKey();
            if (candidate.equals(source) || !isInDirection(origin, entry.getValue(), requestedDirection)) {
                continue;
            }
            final ExactValue distance = distanceSquared(origin, entry.getValue());
            if (best == null || distance.compareTo(bestDistance) < 0
                    || distance.compareTo(bestDistance) == 0 && candidate.compareTo(best) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? Optional.<ProjectedEndpointKey>empty() : Optional.of(best);
    }

    private static Map<ProjectedEndpointKey, LayoutPoint> positions(final CanvasState state) {
        final GraphGeometry geometry = state.geometry();
        final Map<ProjectedEndpointKey, LayoutPoint> positions =
            new HashMap<ProjectedEndpointKey, LayoutPoint>();
        for (ProjectedNode node : state.projection().nodes()) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry != null) {
                positions.put(ProjectedEndpointKey.ofNode(node.key()), nodeGeometry.center());
            }
        }
        for (ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            for (org.freeplane.plugin.graph.projection.EnclosureKey endpoint : enclosure.endpointKeys()) {
                positions.put(ProjectedEndpointKey.ofEnclosure(endpoint), hull.labelAnchor());
            }
        }
        return positions;
    }

    private static boolean isInDirection(final LayoutPoint origin, final LayoutPoint candidate,
            final TraversalDirection direction) {
        switch (direction) {
            case UP:
                return candidate.y() < origin.y();
            case DOWN:
                return candidate.y() > origin.y();
            case LEFT:
                return candidate.x() < origin.x();
            case RIGHT:
                return candidate.x() > origin.x();
            default:
                throw new IllegalArgumentException("Unknown traversal direction");
        }
    }

    private static ExactValue distanceSquared(final LayoutPoint first, final LayoutPoint second) {
        final ExactValue dx = ExactValue.difference(first.x(), second.x());
        final ExactValue dy = ExactValue.difference(first.y(), second.y());
        return dx.square().add(dy.square());
    }

    private static final class ExactValue implements Comparable<ExactValue> {
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
                throw new IllegalArgumentException("Traversal coordinates must be finite");
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

        private ExactValue square() {
            return new ExactValue(significand.multiply(significand), exponent * 2);
        }

        private ExactValue negate() {
            return new ExactValue(significand.negate(), exponent);
        }

        @Override
        public int compareTo(final ExactValue other) {
            final int firstSign = significand.signum();
            final int secondSign = other.significand.signum();
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
}

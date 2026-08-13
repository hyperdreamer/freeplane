package org.freeplane.plugin.graph.geometry;

import java.util.Objects;

public final class NodeGeometry {
    private final LayoutPoint center;
    private final double radius;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private NodeGeometry(final LayoutPoint center, final double radius) {
        this.center = Objects.requireNonNull(center, "center");
        if (!(radius > 0.0) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("Node radius must be finite and positive");
        }
        this.radius = radius;
        this.minX = center.x() - radius;
        this.minY = center.y() - radius;
        this.maxX = center.x() + radius;
        this.maxY = center.y() + radius;
    }

    public static NodeGeometry of(final LayoutPoint center, final double radius) {
        return new NodeGeometry(center, radius);
    }

    public LayoutPoint center() {
        return center;
    }

    public double radius() {
        return radius;
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public boolean contains(final LayoutPoint point) {
        Objects.requireNonNull(point, "point");
        final double dx = Math.abs(point.x() - center.x());
        final double dy = Math.abs(point.y() - center.y());
        final double largest = Math.max(dx, dy);
        if (largest == 0.0) {
            return true;
        }
        final double ux = dx / largest;
        final double uy = dy / largest;
        final double scaledRadius = radius / largest;
        return ux * ux + uy * uy <= scaledRadius * scaledRadius;
    }

    public LayoutPoint boundaryToward(final LayoutPoint toward) {
        Objects.requireNonNull(toward, "toward");
        final Difference dx = difference(toward.x(), center.x());
        final Difference dy = difference(toward.y(), center.y());
        if (dx.significand == 0.0 && dy.significand == 0.0) {
            return LayoutPoint.of(center.x() + radius, center.y());
        }
        final int dominantExponent = Math.max(dx.exponent, dy.exponent);
        final double unitX = Math.scalb(dx.significand, dx.exponent - dominantExponent);
        final double unitY = Math.scalb(dy.significand, dy.exponent - dominantExponent);
        final double length = Math.hypot(unitX, unitY);
        final int radiusExponent = Math.getExponent(radius);
        final double radiusSignificand = Math.scalb(radius, -radiusExponent);
        final double offsetX = boundaryOffset(dx, radiusSignificand, radiusExponent, unitX, length,
            dominantExponent);
        final double offsetY = boundaryOffset(dy, radiusSignificand, radiusExponent, unitY, length,
            dominantExponent);
        final int scaleExponent = radiusExponent - dominantExponent;
        final double residualX = residualDisplacement(dx.residual, dy.residual, unitX, unitY, length,
            radiusSignificand, scaleExponent);
        final double residualY = residualDisplacement(dy.residual, dx.residual, unitY, unitX, length,
            radiusSignificand, scaleExponent);
        return LayoutPoint.of(center.x() + offsetX + residualX, center.y() + offsetY + residualY);
    }

    private static double boundaryOffset(final Difference difference, final double radiusSignificand,
            final int radiusExponent, final double unit, final double length, final int dominantExponent) {
        if (difference.significand == 0.0) {
            return 0.0;
        }
        if (Math.abs(unit) >= Double.MIN_NORMAL) {
            return Math.scalb(radiusSignificand * (unit / length), radiusExponent);
        }
        return Math.scalb(radiusSignificand * (difference.significand / length),
            radiusExponent + difference.exponent - dominantExponent);
    }

    private static double residualDisplacement(final double ownResidual, final double otherResidual,
            final double ownUnit, final double otherUnit, final double length, final double radiusSignificand,
            final int scaleExponent) {
        if (ownResidual == 0.0 && otherResidual == 0.0) {
            return 0.0;
        }
        final double lengthSquared = length * length;
        final double ownFactor = 1.0 - ownUnit * ownUnit / lengthSquared;
        final double crossFactor = ownUnit * otherUnit / lengthSquared;
        final double net = ownResidual * ownFactor - otherResidual * crossFactor;
        if (net == 0.0) {
            return 0.0;
        }
        final int netExponent = Math.getExponent(net);
        final double netSignificand = Math.scalb(net, -netExponent);
        return Math.scalb(radiusSignificand * (netSignificand / length), scaleExponent + netExponent);
    }

    private static Difference difference(final double first, final double second) {
        if (subtractionIsFinite(first, second)) {
            final double delta = first - second;
            if (delta == 0.0) {
                return new Difference(0.0, 0, 0.0);
            }
            final double residual;
            if (Math.abs(first) >= Math.abs(second)) {
                residual = subtractionResidual(first, second, delta);
            }
            else {
                residual = -subtractionResidual(second, first, -delta);
            }
            return new Difference(Math.scalb(delta, -Math.getExponent(delta)), Math.getExponent(delta), residual);
        }
        final double halfFirst = first * 0.5;
        final double halfSecond = second * 0.5;
        final double half = halfFirst - halfSecond;
        final double residual;
        if (Math.abs(halfFirst) >= Math.abs(halfSecond)) {
            residual = subtractionResidual(halfFirst, halfSecond, half) * 2.0;
        }
        else {
            residual = -subtractionResidual(halfSecond, halfFirst, -half) * 2.0;
        }
        return new Difference(Math.scalb(half, -Math.getExponent(half)), Math.getExponent(half) + 1, residual);
    }

    private static double subtractionResidual(final double first, final double second, final double difference) {
        final double secondVirtual = first - difference;
        final double firstVirtual = difference + secondVirtual;
        final double secondRound = secondVirtual - second;
        final double firstRound = first - firstVirtual;
        return firstRound + secondRound;
    }

    private static final class Difference {
        private final double significand;
        private final int exponent;
        private final double residual;

        Difference(final double significand, final int exponent, final double residual) {
            this.significand = significand;
            this.exponent = exponent;
            this.residual = residual;
        }
    }

    private static boolean subtractionIsFinite(final double first, final double second) {
        return (first >= 0.0 && second >= 0.0) || (first <= 0.0 && second <= 0.0)
            || Math.abs(first) <= Double.MAX_VALUE - Math.abs(second);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeGeometry)) {
            return false;
        }
        final NodeGeometry that = (NodeGeometry) other;
        return Double.compare(radius, that.radius) == 0 && center.equals(that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radius);
    }

    @Override
    public String toString() {
        return "NodeGeometry{" + "center=" + center + ", radius=" + radius + '}';
    }
}

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
        return LayoutPoint.of(center.x() + offsetX, center.y() + offsetY);
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

    private static Difference difference(final double first, final double second) {
        if (subtractionIsFinite(first, second)) {
            final double delta = first - second;
            if (delta == 0.0) {
                return new Difference(0.0, 0);
            }
            return new Difference(Math.scalb(delta, -Math.getExponent(delta)), Math.getExponent(delta));
        }
        final double half = first * 0.5 - second * 0.5;
        return new Difference(Math.scalb(half, -Math.getExponent(half)), Math.getExponent(half) + 1);
    }

    private static final class Difference {
        private final double significand;
        private final int exponent;

        Difference(final double significand, final int exponent) {
            this.significand = significand;
            this.exponent = exponent;
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

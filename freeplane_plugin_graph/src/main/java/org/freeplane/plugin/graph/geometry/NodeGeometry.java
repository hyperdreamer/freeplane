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
        final double dx = toward.x() - center.x();
        final double dy = toward.y() - center.y();
        if (dx == 0.0 && dy == 0.0) {
            return LayoutPoint.of(center.x() + radius, center.y());
        }
        final double largest = Math.max(Math.abs(dx), Math.abs(dy));
        final double ux = dx / largest;
        final double uy = dy / largest;
        final double length = Math.sqrt(ux * ux + uy * uy);
        return LayoutPoint.of(center.x() + ux / length * radius, center.y() + uy / length * radius);
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

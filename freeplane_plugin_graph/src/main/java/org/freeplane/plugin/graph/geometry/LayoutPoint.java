package org.freeplane.plugin.graph.geometry;

import java.util.Objects;

public final class LayoutPoint {
    private final double x;
    private final double y;

    private LayoutPoint(final double x, final double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Layout coordinates must be finite");
        }
        this.x = x == 0.0 ? 0.0 : x;
        this.y = y == 0.0 ? 0.0 : y;
    }

    public static LayoutPoint of(final double x, final double y) {
        return new LayoutPoint(x, y);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LayoutPoint)) {
            return false;
        }
        final LayoutPoint that = (LayoutPoint) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "LayoutPoint{" + "x=" + x + ", y=" + y + '}';
    }
}

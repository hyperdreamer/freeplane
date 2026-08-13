package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HullIntersection {
    private static final double EPSILON = 1e-9;

    private HullIntersection() {
    }

    public static LayoutPoint minimumSeparatingTranslation(final HullGeometry first, final HullGeometry second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        final List<LayoutPoint> axes = new ArrayList<LayoutPoint>();
        collectAxes(first.exactPolygon(), axes);
        collectAxes(second.exactPolygon(), axes);
        final List<LayoutPoint> uniqueAxes = new ArrayList<LayoutPoint>();
        for (final LayoutPoint axis : axes) {
            boolean duplicate = false;
            for (final LayoutPoint kept : uniqueAxes) {
                if (Math.abs(axis.x() - kept.x()) < EPSILON && Math.abs(axis.y() - kept.y()) < EPSILON) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                uniqueAxes.add(axis);
            }
        }
        Collections.sort(uniqueAxes, new Comparator<LayoutPoint>() {
            @Override
            public int compare(final LayoutPoint left, final LayoutPoint right) {
                int result = Double.compare(right.x(), left.x());
                if (result != 0) {
                    return result;
                }
                return Double.compare(right.y(), left.y());
            }
        });
        double bestSquared = Double.POSITIVE_INFINITY;
        LayoutPoint bestTranslation = null;
        for (final LayoutPoint axis : uniqueAxes) {
            final double[] firstInterval = project(first.exactPolygon(), axis);
            final double[] secondInterval = project(second.exactPolygon(), axis);
            final double overlap = Math.min(firstInterval[1], secondInterval[1])
                - Math.max(firstInterval[0], secondInterval[0]);
            if (overlap <= EPSILON) {
                return LayoutPoint.of(0.0, 0.0);
            }
            final double positive = firstInterval[1] - secondInterval[0];
            final double negative = secondInterval[1] - firstInterval[0];
            final double magnitude;
            final double direction;
            if (positive <= negative + EPSILON) {
                magnitude = positive;
                direction = 1.0;
            }
            else {
                magnitude = negative;
                direction = -1.0;
            }
            final double squared = magnitude * magnitude;
            if (squared + EPSILON < bestSquared) {
                bestSquared = squared;
                bestTranslation = LayoutPoint.of(axis.x() * direction * magnitude,
                    axis.y() * direction * magnitude);
            }
        }
        return bestTranslation;
    }

    private static void collectAxes(final List<LayoutPoint> polygon, final List<LayoutPoint> axes) {
        final int count = polygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final double dx = next.x() - current.x();
            final double dy = next.y() - current.y();
            final double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0.0) {
                continue;
            }
            double axisX = -dy / length;
            double axisY = dx / length;
            if (axisX < 0.0 || (axisX == 0.0 && axisY < 0.0)) {
                axisX = -axisX;
                axisY = -axisY;
            }
            axes.add(LayoutPoint.of(axisX, axisY));
        }
    }

    private static double[] project(final List<LayoutPoint> polygon, final LayoutPoint axis) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : polygon) {
            final double projection = axis.x() * point.x() + axis.y() * point.y();
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        return new double[] {minimum, maximum};
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof HullIntersection;
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "HullIntersection{}";
    }
}

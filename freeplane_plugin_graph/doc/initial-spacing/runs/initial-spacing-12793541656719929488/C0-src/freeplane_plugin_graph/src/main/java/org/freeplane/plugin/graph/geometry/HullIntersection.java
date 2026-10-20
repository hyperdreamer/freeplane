package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HullIntersection {
    private static final double EPSILON = 1e-9;
    private static final int TARGET_EXPONENT = 500;

    private HullIntersection() {
    }

    public static boolean siblingOverlap(final HullGeometry first, final HullGeometry second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        // Two convex hull polygons overlap strictly iff no separating axis exists and neither
        // polygon is contained in the other. Containment and mere boundary contact are not
        // sibling overlap: the interiors must share at least one point.
        if (separatedByAnAxis(first, second)) {
            return false;
        }
        return !contained(first.exactPolygon(), second) && !contained(second.exactPolygon(), first);
    }

    private static boolean separatedByAnAxis(final HullGeometry first, final HullGeometry second) {
        final List<LayoutPoint> axes = uniqueAxes(first, second);
        final LayoutPoint origin = first.exactPolygon().get(0);
        final RelativeCoordinates firstRelative = RelativeCoordinates.of(first.exactPolygon(), origin);
        final RelativeCoordinates secondRelative = RelativeCoordinates.of(second.exactPolygon(), origin);
        final int scaleExponent = TARGET_EXPONENT
            - Math.max(firstRelative.maxExponent(), secondRelative.maxExponent());
        firstRelative.normalize(scaleExponent);
        secondRelative.normalize(scaleExponent);
        for (final LayoutPoint axis : axes) {
            final double[] firstInterval = project(firstRelative, axis);
            final double[] secondInterval = project(secondRelative, axis);
            if (firstInterval[1] <= secondInterval[0] || secondInterval[1] <= firstInterval[0]) {
                return true;
            }
        }
        return false;
    }

    private static boolean contained(final List<LayoutPoint> polygon, final HullGeometry hull) {
        for (final LayoutPoint vertex : polygon) {
            if (!hull.contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    private static List<LayoutPoint> uniqueAxes(final HullGeometry first, final HullGeometry second) {
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
        return uniqueAxes;
    }

    public static LayoutPoint minimumSeparatingTranslation(final HullGeometry first, final HullGeometry second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        final List<LayoutPoint> uniqueAxes = uniqueAxes(first, second);
        final LayoutPoint origin = first.exactPolygon().get(0);
        final RelativeCoordinates firstRelative = RelativeCoordinates.of(first.exactPolygon(), origin);
        final RelativeCoordinates secondRelative = RelativeCoordinates.of(second.exactPolygon(), origin);
        final int scaleExponent = TARGET_EXPONENT
            - Math.max(firstRelative.maxExponent(), secondRelative.maxExponent());
        firstRelative.normalize(scaleExponent);
        secondRelative.normalize(scaleExponent);
        final double epsilon = EPSILON * Math.scalb(1.0, scaleExponent);
        double bestMagnitude = Double.POSITIVE_INFINITY;
        LayoutPoint bestTranslation = null;
        for (final LayoutPoint axis : uniqueAxes) {
            final double[] firstInterval = project(firstRelative, axis);
            final double[] secondInterval = project(secondRelative, axis);
            final double overlap = Math.min(firstInterval[1], secondInterval[1])
                - Math.max(firstInterval[0], secondInterval[0]);
            if (overlap <= epsilon) {
                return LayoutPoint.of(0.0, 0.0);
            }
            final double positive = firstInterval[1] - secondInterval[0];
            final double negative = secondInterval[1] - firstInterval[0];
            final double magnitude;
            final double direction;
            if (positive <= negative + epsilon) {
                magnitude = positive;
                direction = 1.0;
            }
            else {
                magnitude = negative;
                direction = -1.0;
            }
            if (magnitude + epsilon < bestMagnitude) {
                bestMagnitude = magnitude;
                final double unscaledMagnitude = Math.scalb(magnitude, -scaleExponent);
                bestTranslation = LayoutPoint.of(axis.x() * direction * unscaledMagnitude,
                    axis.y() * direction * unscaledMagnitude);
            }
        }
        return bestTranslation == null ? LayoutPoint.of(0.0, 0.0) : bestTranslation;
    }

    private static void collectAxes(final List<LayoutPoint> polygon, final List<LayoutPoint> axes) {
        final int count = polygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final double dx;
            final double dy;
            if (subtractionIsFinite(next.x(), current.x()) && subtractionIsFinite(next.y(), current.y())) {
                dx = next.x() - current.x();
                dy = next.y() - current.y();
            }
            else {
                final double scale = Math.max(Math.max(Math.abs(current.x()), Math.abs(current.y())),
                    Math.max(Math.abs(next.x()), Math.abs(next.y())));
                dx = next.x() / scale - current.x() / scale;
                dy = next.y() / scale - current.y() / scale;
            }
            final double length = Math.hypot(dx, dy);
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

    private static double[] project(final RelativeCoordinates polygon, final LayoutPoint axis) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < polygon.x.length; index++) {
            final double projection = dotProduct(axis, polygon.x[index], polygon.y[index]);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        return new double[] {minimum, maximum};
    }

    private static double dotProduct(final LayoutPoint axis, final double x, final double y) {
        final double[] xTerm = twoProduct(axis.x(), x);
        final double[] yTerm = twoProduct(axis.y(), y);
        final double[] sum = twoSum(xTerm[0], yTerm[0]);
        final double residual = (xTerm[1] + yTerm[1]) + sum[1];
        return sum[0] + residual;
    }

    private static final class RelativeCoordinates {
        private final double[] x;
        private final double[] y;
        private final int[] xScale;
        private final int[] yScale;

        private RelativeCoordinates(final int count) {
            x = new double[count];
            y = new double[count];
            xScale = new int[count];
            yScale = new int[count];
        }

        static RelativeCoordinates of(final List<LayoutPoint> polygon, final LayoutPoint origin) {
            final RelativeCoordinates coordinates = new RelativeCoordinates(polygon.size());
            for (int index = 0; index < polygon.size(); index++) {
                final LayoutPoint point = polygon.get(index);
                final double[] xDifference = scaledDifference(point.x(), origin.x());
                final double[] yDifference = scaledDifference(point.y(), origin.y());
                coordinates.x[index] = xDifference[0];
                coordinates.y[index] = yDifference[0];
                coordinates.xScale[index] = (int) xDifference[1];
                coordinates.yScale[index] = (int) yDifference[1];
            }
            return coordinates;
        }

        int maxExponent() {
            int maximum = Integer.MIN_VALUE;
            for (int index = 0; index < x.length; index++) {
                maximum = Math.max(maximum, Math.getExponent(x[index]) + xScale[index]);
                maximum = Math.max(maximum, Math.getExponent(y[index]) + yScale[index]);
            }
            return maximum;
        }

        void normalize(final int exponent) {
            for (int index = 0; index < x.length; index++) {
                x[index] = Math.scalb(x[index], xScale[index] + exponent);
                y[index] = Math.scalb(y[index], yScale[index] + exponent);
            }
        }
    }

    private static double[] scaledDifference(final double coordinate, final double origin) {
        if (subtractionIsFinite(coordinate, origin)) {
            return new double[] {coordinate - origin, 0.0};
        }
        return new double[] {coordinate * 0.5 - origin * 0.5, 1.0};
    }

    private static double[] twoSum(final double first, final double second) {
        final double sum = first + second;
        final double secondVirtual = sum - first;
        final double firstResidual = first - (sum - secondVirtual);
        final double secondResidual = second - secondVirtual;
        return new double[] {sum, firstResidual + secondResidual};
    }

    private static double[] twoProduct(final double first, final double second) {
        final double splitter = 134217729.0;
        final double firstHigh = splitter * first - (splitter * first - first);
        final double firstLow = first - firstHigh;
        final double secondHigh = splitter * second - (splitter * second - second);
        final double secondLow = second - secondHigh;
        final double product = first * second;
        final double error = (firstHigh * secondHigh - product) + firstHigh * secondLow
            + firstLow * secondHigh + firstLow * secondLow;
        return new double[] {product, error};
    }

    private static boolean subtractionIsFinite(final double first, final double second) {
        return (first >= 0.0 && second >= 0.0) || (first <= 0.0 && second <= 0.0)
            || Math.abs(first) <= Double.MAX_VALUE - Math.abs(second);
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

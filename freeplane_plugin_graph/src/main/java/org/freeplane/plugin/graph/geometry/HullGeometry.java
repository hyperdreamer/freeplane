package org.freeplane.plugin.graph.geometry;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class HullGeometry {
    private static final double EPSILON = 1e-9;
    private static final double CORNER_SMOOTHING_TANGENT = 4.0;

    private final List<LayoutPoint> exactPolygon;
    private final LayoutPoint labelAnchor;
    private final Path2D.Double smoothPath;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private HullGeometry(final List<LayoutPoint> exactPolygon, final LayoutPoint labelAnchor) {
        this.exactPolygon = canonicalize(exactPolygon);
        this.labelAnchor = Objects.requireNonNull(labelAnchor, "labelAnchor");
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : this.exactPolygon) {
            minimumX = Math.min(minimumX, point.x());
            minimumY = Math.min(minimumY, point.y());
            maximumX = Math.max(maximumX, point.x());
            maximumY = Math.max(maximumY, point.y());
        }
        this.minX = minimumX;
        this.minY = minimumY;
        this.maxX = maximumX;
        this.maxY = maximumY;
        this.smoothPath = buildSmoothPath(this.exactPolygon);
    }

    public static HullGeometry of(final List<LayoutPoint> exactPolygon, final LayoutPoint labelAnchor) {
        return new HullGeometry(exactPolygon, labelAnchor);
    }

    public List<LayoutPoint> exactPolygon() {
        return exactPolygon;
    }

    public LayoutPoint labelAnchor() {
        return labelAnchor;
    }

    public Shape smoothPath() {
        return (Path2D.Double) smoothPath.clone();
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
        final int count = exactPolygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint start = exactPolygon.get(index);
            final LayoutPoint end = exactPolygon.get((index + 1) % count);
            final double cross = (end.x() - start.x()) * (point.y() - start.y())
                - (end.y() - start.y()) * (point.x() - start.x());
            if (cross < -EPSILON) {
                return false;
            }
        }
        return true;
    }

    public LayoutPoint nearestBoundaryPoint(final LayoutPoint toward) {
        Objects.requireNonNull(toward, "toward");
        LayoutPoint bestPoint = null;
        double bestSquared = Double.POSITIVE_INFINITY;
        final int count = exactPolygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint start = exactPolygon.get(index);
            final LayoutPoint end = exactPolygon.get((index + 1) % count);
            final double dx = end.x() - start.x();
            final double dy = end.y() - start.y();
            final double lengthSquared = dx * dx + dy * dy;
            double t = 0.0;
            if (lengthSquared > 0.0) {
                t = ((toward.x() - start.x()) * dx + (toward.y() - start.y()) * dy) / lengthSquared;
                t = Math.max(0.0, Math.min(1.0, t));
            }
            final double px = start.x() + t * dx;
            final double py = start.y() + t * dy;
            final double deltaX = toward.x() - px;
            final double deltaY = toward.y() - py;
            final double squared = deltaX * deltaX + deltaY * deltaY;
            if (squared < bestSquared - EPSILON) {
                bestSquared = squared;
                bestPoint = LayoutPoint.of(px, py);
            }
        }
        return bestPoint;
    }

    private static List<LayoutPoint> canonicalize(final List<LayoutPoint> polygon) {
        Objects.requireNonNull(polygon, "exactPolygon");
        final List<LayoutPoint> vertices = new ArrayList<LayoutPoint>(polygon.size());
        for (final LayoutPoint point : polygon) {
            vertices.add(Objects.requireNonNull(point, "exactPolygon entry"));
        }
        if (vertices.size() >= 2 && vertices.get(vertices.size() - 1).equals(vertices.get(0))) {
            vertices.remove(vertices.size() - 1);
        }
        boolean changed;
        do {
            changed = removeDegenerate(vertices);
        }
        while (changed);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A hull polygon needs at least three unique vertices");
        }
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (int index = 0; index < vertices.size(); index++) {
            final double cross = cross(vertices.get(index), vertices.get((index + 1) % vertices.size()),
                vertices.get((index + 2) % vertices.size()));
            if (cross > EPSILON) {
                hasPositive = true;
            }
            else if (cross < -EPSILON) {
                hasNegative = true;
            }
        }
        if (hasPositive && hasNegative) {
            throw new IllegalArgumentException("A hull polygon must be strictly convex");
        }
        if (!isSimple(vertices)) {
            throw new IllegalArgumentException("A hull polygon must not be self-intersecting");
        }
        if (hasNegative) {
            Collections.reverse(vertices);
        }
        int start = 0;
        for (int index = 1; index < vertices.size(); index++) {
            if (compare(vertices.get(index), vertices.get(start)) < 0) {
                start = index;
            }
        }
        if (start > 0) {
            Collections.rotate(vertices, -start);
        }
        return Collections.unmodifiableList(vertices);
    }

    private static boolean removeDegenerate(final List<LayoutPoint> vertices) {
        boolean changed = false;
        for (int index = vertices.size() - 1; index >= 0; index--) {
            if (vertices.size() < 3) {
                break;
            }
            final LayoutPoint previous = vertices.get((index + vertices.size() - 1) % vertices.size());
            final LayoutPoint current = vertices.get(index);
            final LayoutPoint next = vertices.get((index + 1) % vertices.size());
            if (current.equals(previous) || Math.abs(cross(previous, current, next)) <= EPSILON) {
                vertices.remove(index);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isSimple(final List<LayoutPoint> polygon) {
        final int count = polygon.size();
        for (int first = 0; first < count; first++) {
            final LayoutPoint a = polygon.get(first);
            final LayoutPoint b = polygon.get((first + 1) % count);
            for (int second = first + 1; second < count; second++) {
                if (second == first + 1 || (first == 0 && second == count - 1)) {
                    continue;
                }
                final LayoutPoint c = polygon.get(second);
                final LayoutPoint d = polygon.get((second + 1) % count);
                if (segmentsIntersect(a, b, c, d)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentsIntersect(final LayoutPoint a, final LayoutPoint b,
            final LayoutPoint c, final LayoutPoint d) {
        final double abC = cross(a, b, c);
        final double abD = cross(a, b, d);
        final double cdA = cross(c, d, a);
        final double cdB = cross(c, d, b);
        if (Math.abs(abC) <= EPSILON || Math.abs(abD) <= EPSILON || Math.abs(cdA) <= EPSILON
                || Math.abs(cdB) <= EPSILON) {
            return onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
        }
        return abC * abD < 0.0 && cdA * cdB < 0.0;
    }

    private static boolean onSegment(final LayoutPoint a, final LayoutPoint b, final LayoutPoint point) {
        return point.x() <= Math.max(a.x(), b.x()) + EPSILON && point.x() >= Math.min(a.x(), b.x()) - EPSILON
            && point.y() <= Math.max(a.y(), b.y()) + EPSILON && point.y() >= Math.min(a.y(), b.y()) - EPSILON;
    }

    private static Path2D.Double buildSmoothPath(final List<LayoutPoint> polygon) {
        final int count = polygon.size();
        final double[] cuts = new double[count];
        for (int index = 0; index < count; index++) {
            final LayoutPoint previous = polygon.get((index + count - 1) % count);
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final double inLength = distance(previous, current);
            final double outLength = distance(current, next);
            cuts[index] = Math.min(CORNER_SMOOTHING_TANGENT, Math.min(inLength * 0.5, outLength * 0.5));
        }
        final Path2D.Double path = new Path2D.Double();
        for (int index = 0; index < count; index++) {
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final LayoutPoint start = pointAlong(polygon.get((index + count - 1) % count), current, cuts[index]);
            final LayoutPoint end = pointAlong(current, next, cuts[index]);
            if (index == 0) {
                path.moveTo(start.x(), start.y());
            }
            else {
                path.lineTo(start.x(), start.y());
            }
            path.quadTo(current.x(), current.y(), end.x(), end.y());
        }
        path.closePath();
        return path;
    }

    private static double distance(final LayoutPoint first, final LayoutPoint second) {
        final double dx = second.x() - first.x();
        final double dy = second.y() - first.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static LayoutPoint pointAlong(final LayoutPoint from, final LayoutPoint to, final double distance) {
        final double dx = to.x() - from.x();
        final double dy = to.y() - from.y();
        final double length = Math.sqrt(dx * dx + dy * dy);
        return LayoutPoint.of(from.x() + dx / length * distance, from.y() + dy / length * distance);
    }

    private static double cross(final LayoutPoint previous, final LayoutPoint current, final LayoutPoint next) {
        return (current.x() - previous.x()) * (next.y() - current.y())
            - (current.y() - previous.y()) * (next.x() - current.x());
    }

    private static int compare(final LayoutPoint first, final LayoutPoint second) {
        int result = Double.compare(first.x(), second.x());
        if (result != 0) {
            return result;
        }
        return Double.compare(first.y(), second.y());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HullGeometry)) {
            return false;
        }
        final HullGeometry that = (HullGeometry) other;
        return exactPolygon.equals(that.exactPolygon) && labelAnchor.equals(that.labelAnchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exactPolygon, labelAnchor);
    }

    @Override
    public String toString() {
        return "HullGeometry{" + "vertexCount=" + exactPolygon.size() + '}';
    }
}

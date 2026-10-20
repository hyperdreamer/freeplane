package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable drawn geometry of one graph edge (spec 2.4, 6.5-6.7): the defining control
 * polygon, the single sampled polyline shared by clearance checking, hit-testing and
 * painting, and the terminal values the arrowhead and multiplicity cues derive from.
 */
public final class EdgePath {
    public enum Shape {
        STRAIGHT(2), BOW(3), DOGLEG(4);

        private final int definingPointCount;

        Shape(final int definingPointCount) {
            this.definingPointCount = definingPointCount;
        }
    }

    /** Maximum sagitta of a sampled bow polyline, in world units (spec 6.5). */
    public static final double BOW_SAMPLE_TOLERANCE = 0.75;

    private final Shape shape;
    private final List<LayoutPoint> definingPoints;
    private final List<LayoutPoint> samples;
    private final Optional<LayoutPoint> control;
    private final double length;

    private EdgePath(final Shape shape, final List<LayoutPoint> definingPoints,
            final List<LayoutPoint> samples) {
        this.shape = Objects.requireNonNull(shape, "shape");
        this.definingPoints = freeze(definingPoints, "definingPoints");
        requirePointCount(this.definingPoints, shape.definingPointCount, "definingPoints");
        this.samples = freeze(samples, "samples");
        if (this.samples.size() < 2) {
            throw new IllegalArgumentException("samples must contain at least two points");
        }
        this.control = shape == Shape.BOW ? Optional.of(this.definingPoints.get(1))
            : Optional.<LayoutPoint>empty();
        this.length = computeLength(this.samples);
    }

    public static EdgePath straight(final LayoutPoint start, final LayoutPoint end) {
        final List<LayoutPoint> points = Arrays.asList(Objects.requireNonNull(start, "start"),
            Objects.requireNonNull(end, "end"));
        return new EdgePath(Shape.STRAIGHT, points, points);
    }

    public static EdgePath bow(final LayoutPoint start, final LayoutPoint control, final LayoutPoint end) {
        final LayoutPoint from = Objects.requireNonNull(start, "start");
        final LayoutPoint through = Objects.requireNonNull(control, "control");
        final LayoutPoint to = Objects.requireNonNull(end, "end");
        return new EdgePath(Shape.BOW, Arrays.asList(from, through, to), sampleBow(from, through, to));
    }

    public static EdgePath dogleg(final LayoutPoint start, final LayoutPoint bend1, final LayoutPoint bend2,
            final LayoutPoint end) {
        final List<LayoutPoint> points = Arrays.asList(Objects.requireNonNull(start, "start"),
            Objects.requireNonNull(bend1, "bend1"), Objects.requireNonNull(bend2, "bend2"),
            Objects.requireNonNull(end, "end"));
        return new EdgePath(Shape.DOGLEG, points, points);
    }

    public Shape shape() {
        return shape;
    }

    public LayoutPoint start() {
        return definingPoints.get(0);
    }

    public LayoutPoint end() {
        return definingPoints.get(definingPoints.size() - 1);
    }

    public Optional<LayoutPoint> control() {
        return control;
    }

    public List<LayoutPoint> definingPoints() {
        return definingPoints;
    }

    public List<LayoutPoint> samples() {
        return samples;
    }

    public double length() {
        return length;
    }

    public LayoutPoint arcLengthMidpoint() {
        if (length == 0.0) {
            return samples.get(0);
        }
        final double half = length / 2.0;
        double accumulated = 0.0;
        for (int index = 1; index < samples.size(); index++) {
            final LayoutPoint previous = samples.get(index - 1);
            final LayoutPoint current = samples.get(index);
            final double segment = distance(previous, current);
            if (accumulated + segment >= half) {
                final double fraction = (half - accumulated) / segment;
                return LayoutPoint.of(previous.x() + fraction * (current.x() - previous.x()),
                    previous.y() + fraction * (current.y() - previous.y()));
            }
            accumulated += segment;
        }
        return samples.get(samples.size() - 1);
    }

    public LayoutPoint arrowAnchor(final boolean atStart) {
        switch (shape) {
            case STRAIGHT:
                return atStart ? end() : start();
            case BOW:
                return control.get();
            case DOGLEG:
                return atStart ? definingPoints.get(1) : definingPoints.get(2);
            default:
                throw new IllegalStateException("Unsupported shape: " + shape);
        }
    }

    public double terminalSegmentLength(final boolean atStart) {
        return distance(atStart ? start() : end(), arrowAnchor(atStart));
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EdgePath)) {
            return false;
        }
        final EdgePath that = (EdgePath) other;
        return shape == that.shape && definingPoints.equals(that.definingPoints)
            && samples.equals(that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shape, definingPoints, samples);
    }

    private static List<LayoutPoint> sampleBow(final LayoutPoint start, final LayoutPoint control,
            final LayoutPoint end) {
        final double chordLength = distance(start, end);
        final double sagitta = chordLength == 0.0 ? distance(control, start)
            : distanceToLine(control, start, end);
        final int segments = Math.max(1, (int) Math.ceil(
            Math.sqrt(sagitta / (2.0 * BOW_SAMPLE_TOLERANCE))));
        final List<LayoutPoint> samples = new ArrayList<LayoutPoint>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            final double t = (double) index / (double) segments;
            final double oneMinusT = 1.0 - t;
            final double x = oneMinusT * oneMinusT * start.x()
                + 2.0 * oneMinusT * t * control.x() + t * t * end.x();
            final double y = oneMinusT * oneMinusT * start.y()
                + 2.0 * oneMinusT * t * control.y() + t * t * end.y();
            samples.add(LayoutPoint.of(x, y));
        }
        return Collections.unmodifiableList(samples);
    }

    /** Exact perpendicular distance of the point from the infinite line through the chord. */
    private static double distanceToLine(final LayoutPoint point, final LayoutPoint lineStart,
            final LayoutPoint lineEnd) {
        final double lineX = lineEnd.x() - lineStart.x();
        final double lineY = lineEnd.y() - lineStart.y();
        final double lineLength = Math.hypot(lineX, lineY);
        if (lineLength == 0.0) {
            return distance(point, lineStart);
        }
        final double cross = lineX * (point.y() - lineStart.y()) - lineY * (point.x() - lineStart.x());
        return Math.abs(cross) / lineLength;
    }

    private static double distance(final LayoutPoint first, final LayoutPoint second) {
        return Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    private static double computeLength(final List<LayoutPoint> samples) {
        double total = 0.0;
        for (int index = 1; index < samples.size(); index++) {
            total += distance(samples.get(index - 1), samples.get(index));
        }
        return total;
    }

    private static List<LayoutPoint> freeze(final List<LayoutPoint> points, final String role) {
        Objects.requireNonNull(points, role);
        final List<LayoutPoint> copy = new ArrayList<LayoutPoint>(points.size());
        for (final LayoutPoint point : points) {
            copy.add(Objects.requireNonNull(point, role + " point"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static void requirePointCount(final List<LayoutPoint> points, final int expected,
            final String role) {
        if (points.size() != expected) {
            throw new IllegalArgumentException(role + " must contain " + expected + " points but had "
                + points.size());
        }
    }
}

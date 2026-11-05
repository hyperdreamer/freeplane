package org.freeplane.plugin.graph.canvas.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A measured title: numbers only, no icons, so it can be memoized away from the icon cache. */
public final class MeasuredTitle {
    public enum Availability {
        RENDERED, DEGRADED
    }

    private final Availability availability;
    private final List<MeasuredRun> runs;
    private final double width;
    private final double height;
    private final double ascent;
    private final double descent;
    private final boolean deferredForBudget;

    private MeasuredTitle(final Availability availability, final List<MeasuredRun> runs, final double width,
            final double height, final double ascent, final double descent, final boolean deferredForBudget) {
        if (runs == null) {
            throw new NullPointerException("runs");
        }
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("runs must not be empty");
        }
        final List<MeasuredRun> copy = new ArrayList<MeasuredRun>(runs);
        for (final MeasuredRun run : copy) {
            if (run == null) {
                throw new NullPointerException("run");
            }
        }
        requirePositive(height, "height");
        requireNonNegative(width, "width");
        requireNonNegative(ascent, "ascent");
        requireNonNegative(descent, "descent");
        this.availability = availability;
        this.runs = Collections.unmodifiableList(copy);
        this.width = width;
        this.height = height;
        this.ascent = ascent;
        this.descent = descent;
        this.deferredForBudget = deferredForBudget;
    }

    public static MeasuredTitle rendered(final List<MeasuredRun> runs, final double width, final double height,
            final double ascent, final double descent) {
        return new MeasuredTitle(Availability.RENDERED, runs, width, height, ascent, descent, false);
    }

    public static MeasuredTitle degraded(final List<MeasuredRun> runs, final double width, final double height,
            final double ascent, final double descent) {
        return new MeasuredTitle(Availability.DEGRADED, runs, width, height, ascent, descent, false);
    }

    public MeasuredTitle deferred() {
        return new MeasuredTitle(availability, runs, width, height, ascent, descent, true);
    }

    public Availability availability() {
        return availability;
    }

    public List<MeasuredRun> runs() {
        return runs;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double ascent() {
        return ascent;
    }

    public double descent() {
        return descent;
    }

    public boolean deferredForBudget() {
        return deferredForBudget;
    }

    private static void requirePositive(final double value, final String field) {
        if (!Double.isFinite(value) || !(value > 0.0)) {
            throw new IllegalArgumentException("measurement must be finite and positive: " + field);
        }
    }

    private static void requireNonNegative(final double value, final String field) {
        if (!Double.isFinite(value) || !(value >= 0.0)) {
            throw new IllegalArgumentException("measurement must be finite and non-negative: " + field);
        }
    }
}

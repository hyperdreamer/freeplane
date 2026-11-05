package org.freeplane.plugin.graph.canvas.title;

import java.util.Objects;

/** One measured run of a title, in screen pixels at the logical label font size. */
public final class MeasuredRun {
    public enum Kind {
        PLAIN, MATH
    }

    private final Kind kind;
    private final String source;
    private final String rawSource;
    private final double x;
    private final double width;
    private final double ascent;
    private final double descent;

    private MeasuredRun(final Kind kind, final String source, final String rawSource, final double x,
            final double width, final double ascent, final double descent) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (rawSource == null) {
            throw new NullPointerException("rawSource");
        }
        requireNonNegative(x, "x");
        requireNonNegative(width, "width");
        requireNonNegative(ascent, "ascent");
        requireNonNegative(descent, "descent");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.source = source;
        this.rawSource = rawSource;
        this.x = x;
        this.width = width;
        this.ascent = ascent;
        this.descent = descent;
    }

    public static MeasuredRun plain(final String text, final double x, final double width, final double ascent,
            final double descent) {
        return new MeasuredRun(Kind.PLAIN, text, text, x, width, ascent, descent);
    }

    public static MeasuredRun math(final String source, final String rawSource, final double x, final double width,
            final double ascent, final double descent) {
        return new MeasuredRun(Kind.MATH, source, rawSource, x, width, ascent, descent);
    }

    public Kind kind() {
        return kind;
    }

    public String source() {
        return source;
    }

    public String rawSource() {
        return rawSource;
    }

    public double x() {
        return x;
    }

    public double width() {
        return width;
    }

    public double ascent() {
        return ascent;
    }

    public double descent() {
        return descent;
    }

    public boolean isMath() {
        return kind == Kind.MATH;
    }

    private static void requireNonNegative(final double value, final String field) {
        if (!Double.isFinite(value) || !(value >= 0.0)) {
            throw new IllegalArgumentException("measured run must be finite and non-negative: " + field);
        }
    }
}

package org.freeplane.plugin.graph.canvas.title;

import java.util.Objects;

import org.scilab.forge.jlatexmath.TeXIcon;

public final class MathRunRow implements RunRow {
    private final String body;
    private final TeXIcon icon;
    private final double x;
    private final double width;
    private final double ascent;
    private final double descent;

    public MathRunRow(final String body, final TeXIcon icon, final double x, final double width,
            final double ascent, final double descent) {
        this.body = Objects.requireNonNull(body, "body");
        this.icon = Objects.requireNonNull(icon, "icon");
        requireFinite(x, "x");
        requireFinite(width, "width");
        requireFinite(ascent, "ascent");
        requireFinite(descent, "descent");
        this.x = x;
        this.width = width;
        this.ascent = ascent;
        this.descent = descent;
    }

    public String body() {
        return body;
    }

    public TeXIcon icon() {
        return icon;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double width() {
        return width;
    }

    public double ascent() {
        return ascent;
    }

    public double descent() {
        return descent;
    }

    private static void requireFinite(final double value, final String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("run geometry must be finite: " + field);
        }
    }
}

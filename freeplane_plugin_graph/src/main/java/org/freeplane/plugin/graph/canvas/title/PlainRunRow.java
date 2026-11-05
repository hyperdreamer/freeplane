package org.freeplane.plugin.graph.canvas.title;

import java.awt.Font;
import java.util.Objects;

public final class PlainRunRow implements RunRow {
    private final String paintText;
    private final Font worldFont;
    private final double x;
    private final double width;
    private final double ascent;
    private final double descent;

    public PlainRunRow(final String paintText, final Font worldFont, final double x, final double width,
            final double ascent, final double descent) {
        this.paintText = Objects.requireNonNull(paintText, "paintText");
        this.worldFont = Objects.requireNonNull(worldFont, "worldFont");
        requireFinite(x, "x");
        requireFinite(width, "width");
        requireFinite(ascent, "ascent");
        requireFinite(descent, "descent");
        this.x = x;
        this.width = width;
        this.ascent = ascent;
        this.descent = descent;
    }

    public String paintText() {
        return paintText;
    }

    public Font worldFont() {
        return worldFont;
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

package org.freeplane.plugin.graph.canvas.title;

import org.scilab.forge.jlatexmath.TeXIcon;

/**
 * Measured icon geometry. Carries the icon so a resolved row can paint it without consulting the cache; the icon is
 * null only in measurement-only test fixtures, which never paint.
 */
public final class IconBox {
    private final TeXIcon icon;
    private final double width;
    private final double height;
    private final double ascent;
    private final double descent;

    public IconBox(final TeXIcon icon, final double width, final double height, final double ascent,
            final double descent) {
        requireFinite(width, "width");
        requireFinite(height, "height");
        requireFinite(ascent, "ascent");
        requireFinite(descent, "descent");
        this.icon = icon;
        this.width = width;
        this.height = height;
        this.ascent = ascent;
        this.descent = descent;
    }

    public TeXIcon icon() {
        return icon;
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

    private static void requireFinite(final double value, final String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("icon metric must be finite: " + field);
        }
    }
}

package org.freeplane.plugin.graph.canvas.title;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.TitleText;

/** Degraded-text construction and the character truncation shared with plain titles. */
public final class TitleTruncation {
    private static final String ELLIPSIS = "\u2026";

    private TitleTruncation() {
    }

    /** The text a degraded title displays: plain runs verbatim, formulas as their raw source. */
    public static String degradedPlainText(final TitleText title) {
        Objects.requireNonNull(title, "title");
        final StringBuilder text = new StringBuilder();
        for (final TitleText.Run run : title.runs()) {
            text.append(run instanceof TitleText.Math ? ((TitleText.Math) run).rawSource()
                : ((TitleText.Plain) run).text());
        }
        return text.toString();
    }

    public static boolean fits(final String text, final Font font, final double limit) {
        return width(text, font) <= limit;
    }

    /** Mirrors {@code ScreenLabelPlacement.truncateTo}; equality is asserted from a canvas-package test. */
    public static String truncated(final String text, final Font font, final double limit) {
        if (width(text, font) <= limit) {
            return text;
        }
        for (int cut = text.length() - 1; cut > 2; cut--) {
            final String candidate = stripTrailing(text.substring(0, cut)) + ELLIPSIS;
            if (width(candidate, font) <= limit) {
                return candidate;
            }
        }
        return text.substring(0, Math.min(3, text.length())) + ELLIPSIS;
    }

    private static double width(final String text, final Font font) {
        final Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC);
        return bounds.getWidth();
    }

    private static String stripTrailing(final String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}

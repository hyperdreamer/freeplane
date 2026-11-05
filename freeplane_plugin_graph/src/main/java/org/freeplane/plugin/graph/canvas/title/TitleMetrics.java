package org.freeplane.plugin.graph.canvas.title;

import java.awt.Font;
import java.awt.font.FontRenderContext;

import org.scilab.forge.jlatexmath.TeXConstants;

/**
 * Shared numeric contract for title rendering. The render context is declared here, rather than reused from
 * {@code ScreenLabelPlacement}, because that constant is package-private in the sibling {@code canvas} package.
 * {@code ScreenLabelPlacementShould.titleMeasurerMatchesPlacementScreenBounds} pins the two contexts equal.
 */
public final class TitleMetrics {
    public static final FontRenderContext SCREEN_FRC = new FontRenderContext(null, true, true);

    public static final int STYLE = TeXConstants.STYLE_TEXT;

    public static final double MAX_HEIGHT_FACTOR = 2.0;

    private TitleMetrics() {
    }

    public static int px(final Font font) {
        return Math.max(1, Math.round(font.getSize2D()));
    }

    public static double lineHeight(final Font font) {
        return font.getLineMetrics("Hg", SCREEN_FRC).getHeight();
    }

    /** The paint font: the exact expression and operation order of {@code GraphPainter.java:281-282}. */
    public static Font worldFont(final Font logicalFont, final double zoom) {
        return logicalFont.deriveFont(Math.max(1.0f, logicalFont.getSize2D() / (float) zoom));
    }
}

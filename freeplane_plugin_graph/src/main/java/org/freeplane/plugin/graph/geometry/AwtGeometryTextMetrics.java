package org.freeplane.plugin.graph.geometry;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.BoundaryTier;

public final class AwtGeometryTextMetrics implements GeometryTextMetrics {
    private final Font font;
    private final FontRenderContext context;

    public AwtGeometryTextMetrics(final Font font, final FontRenderContext context) {
        this.font = Objects.requireNonNull(font, "font");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public Dimension2D measure(final String displayText, final BoundaryTier tier) {
        Objects.requireNonNull(displayText, "displayText");
        if (displayText.isEmpty()) {
            throw new IllegalArgumentException("displayText must not be empty");
        }
        Objects.requireNonNull(tier, "tier");
        if (tier == BoundaryTier.SUPPRESSED) {
            throw new IllegalArgumentException("Suppressed labels must not be measured");
        }
        final Font measuredFont = tier == BoundaryTier.EMPHATIC
            ? font.deriveFont(Font.BOLD, font.getSize2D() * 1.2f) : font;
        final Rectangle2D bounds = measuredFont.getStringBounds(displayText, context);
        final double width = bounds.getWidth();
        final double height = bounds.getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height) || !(width > 0.0) || !(height > 0.0)) {
            throw new IllegalArgumentException("Text metrics must be finite and positive");
        }
        return new ImmutableDimension(width, height);
    }

    private static final class ImmutableDimension extends Dimension2D {
        private final double width;
        private final double height;

        private ImmutableDimension(final double width, final double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public double getWidth() {
            return width;
        }

        @Override
        public double getHeight() {
            return height;
        }

        @Override
        public void setSize(final double width, final double height) {
            throw new UnsupportedOperationException("Text metrics are immutable");
        }
    }
}

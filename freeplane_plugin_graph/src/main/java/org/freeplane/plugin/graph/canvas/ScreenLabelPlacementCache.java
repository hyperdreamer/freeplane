package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class ScreenLabelPlacementCache {
    private final ScreenLabelPlacement placement = new ScreenLabelPlacement();
    private Key lastKey;
    private List<PlacedLabel> lastPlacements = Collections.emptyList();

    public List<PlacedLabel> place(final LabelPlacementRequest request, final LabelFonts fonts) {
        final Key key = Key.of(request, fonts);
        if (key.equals(lastKey)) {
            return lastPlacements;
        }
        final List<PlacedLabel> next = placement.place(request, lastPlacements, fonts);
        lastKey = key;
        lastPlacements = next;
        return next;
    }

    private static final class Key {
        private final long generation;
        private final LayoutPositions positions;
        private final double zoom;
        private final double centerX;
        private final double centerY;
        private final int width;
        private final int height;
        private final Set<ProjectedEndpointKey> forced;
        private final RenderingLevel level;
        private final Font full;
        private final Font dense;
        private final Font emphatic;

        private Key(final LabelPlacementRequest request, final LabelFonts fonts) {
            this.generation = request.projection().generation();
            this.positions = request.positions();
            this.zoom = request.zoom();
            this.centerX = request.centerX();
            this.centerY = request.centerY();
            this.width = (int) Math.round(request.placementArea().getWidth());
            this.height = (int) Math.round(request.placementArea().getHeight());
            this.forced = request.forced();
            this.level = request.renderingLevel();
            this.full = fonts.full();
            this.dense = fonts.dense();
            this.emphatic = fonts.emphatic();
        }

        static Key of(final LabelPlacementRequest request, final LabelFonts fonts) {
            return new Key(Objects.requireNonNull(request, "request"), Objects.requireNonNull(fonts, "fonts"));
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            final Key that = (Key) other;
            return generation == that.generation
                && positions == that.positions
                && Double.compare(zoom, that.zoom) == 0
                && Double.compare(centerX, that.centerX) == 0
                && Double.compare(centerY, that.centerY) == 0
                && width == that.width
                && height == that.height
                && forced.equals(that.forced)
                && level == that.level
                && full.equals(that.full)
                && dense.equals(that.dense)
                && emphatic.equals(that.emphatic);
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(generation).hashCode();
            result = 31 * result + System.identityHashCode(positions);
            result = 31 * result + Double.valueOf(zoom).hashCode();
            result = 31 * result + Double.valueOf(centerX).hashCode();
            result = 31 * result + Double.valueOf(centerY).hashCode();
            result = 31 * result + width;
            result = 31 * result + height;
            result = 31 * result + forced.hashCode();
            result = 31 * result + level.hashCode();
            result = 31 * result + full.hashCode();
            result = 31 * result + dense.hashCode();
            result = 31 * result + emphatic.hashCode();
            return result;
        }
    }
}

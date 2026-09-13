package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Insets;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.junit.Test;

public class MapSidebarLayoutShould {
    private static final Insets NO_INSETS = new Insets(0, 0, 0, 0);
    private static final Insets METAL_INSETS = new Insets(1, 1, 1, 1);

    @Test
    public void exposesTheApprovedWidthConstants() {
        assertThat(MapSidebarLayout.DEFAULT_WIDTH).isEqualTo(264);
        assertThat(MapSidebarLayout.DEFAULT_WIDTH).isEqualTo(DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH);
        assertThat(MapSidebarLayout.MIN_WIDTH).isEqualTo(180);
        assertThat(MapSidebarLayout.RAIL_WIDTH).isEqualTo(26);
    }

    @Test
    public void computesMaximumWidthAsHalfTheSplitWidthWithTheMinimumFloor() {
        assertThat(MapSidebarLayout.maximumWidth(360)).isEqualTo(180);
        assertThat(MapSidebarLayout.maximumWidth(400)).isEqualTo(200);
        assertThat(MapSidebarLayout.maximumWidth(800)).isEqualTo(400);
        assertThat(MapSidebarLayout.maximumWidth(1000)).isEqualTo(500);
        assertThat(MapSidebarLayout.maximumWidth(0)).isEqualTo(180);
    }

    @Test
    public void computesTheEffectiveMinimumFromTheLayoutGeometry() {
        assertThat(MapSidebarLayout.effectiveMinimum(1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(362, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(186, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(185, 6, NO_INSETS)).isEqualTo(179);
        assertThat(MapSidebarLayout.effectiveMinimum(150, 6, NO_INSETS)).isEqualTo(144);
        assertThat(MapSidebarLayout.effectiveMinimum(100, 6, NO_INSETS)).isEqualTo(94);
        assertThat(MapSidebarLayout.effectiveMinimum(6, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.effectiveMinimum(0, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.effectiveMinimum(150, 6, METAL_INSETS)).isEqualTo(142);
        assertThat(MapSidebarLayout.effectiveMinimum(186, 6, METAL_INSETS)).isEqualTo(178);
    }

    @Test
    public void clampsWidthsIntoTheEffectiveRange() {
        assertThat(MapSidebarLayout.clampWidth(100, 1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(180, 1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(500, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(501, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(1000, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(264, 150, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(0, 150, 6, NO_INSETS)).isEqualTo(144);
    }

    @Test
    public void keepsTheClampRangeNonEmptyAtEverySplitWidth() {
        for (int splitWidth = 0; splitWidth <= 1500; splitWidth++) {
            int lower = MapSidebarLayout.effectiveMinimum(splitWidth, 6, NO_INSETS);
            int upper = Math.max(lower, MapSidebarLayout.maximumWidth(splitWidth));
            assertThat(lower).isLessThanOrEqualTo(upper);
            assertThat(MapSidebarLayout.clampWidth(lower, splitWidth, 6, NO_INSETS)).isEqualTo(lower);
            assertThat(MapSidebarLayout.clampWidth(upper, splitWidth, 6, NO_INSETS)).isEqualTo(upper);
        }
    }

    @Test
    public void keepsCanvasMinimumWidthNonNegativeAndMonotonic() {
        int previous = -1;
        for (int splitWidth = 0; splitWidth <= 1500; splitWidth++) {
            int value = MapSidebarLayout.canvasMinimumWidth(splitWidth, 6, NO_INSETS);
            assertThat(value).isGreaterThanOrEqualTo(0);
            assertThat(value).isGreaterThanOrEqualTo(previous);
            previous = value;
        }
    }

    @Test
    public void computesTheCanvasMinimumFromTheLayoutGeometry() {
        assertThat(MapSidebarLayout.canvasMinimumWidth(1000, 6, NO_INSETS)).isEqualTo(494);
        assertThat(MapSidebarLayout.canvasMinimumWidth(362, 6, NO_INSETS)).isEqualTo(175);
        assertThat(MapSidebarLayout.canvasMinimumWidth(150, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.canvasMinimumWidth(100, 6, NO_INSETS)).isEqualTo(0);
    }
}

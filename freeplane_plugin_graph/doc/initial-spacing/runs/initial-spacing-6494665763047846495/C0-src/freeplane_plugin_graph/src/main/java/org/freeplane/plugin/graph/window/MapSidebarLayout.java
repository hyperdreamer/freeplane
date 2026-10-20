package org.freeplane.plugin.graph.window;

import java.awt.Insets;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;

final class MapSidebarLayout {
    static final int DEFAULT_WIDTH = DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH;
    static final int MIN_WIDTH = 180;
    static final int RAIL_WIDTH = 26;

    private MapSidebarLayout() {
    }

    static int maximumWidth(final int splitWidth) {
        return Math.max(MIN_WIDTH, splitWidth / 2);
    }

    static int effectiveMinimum(final int splitWidth, final int effectiveDividerWidth, final Insets insets) {
        return Math.max(0, Math.min(MIN_WIDTH,
            splitWidth - effectiveDividerWidth - insets.left - insets.right));
    }

    static int clampWidth(final int requestedWidth, final int splitWidth, final int effectiveDividerWidth,
            final Insets insets) {
        final int lower = effectiveMinimum(splitWidth, effectiveDividerWidth, insets);
        final int upper = Math.max(lower, maximumWidth(splitWidth));
        return Math.max(lower, Math.min(upper, requestedWidth));
    }

    static int canvasMinimumWidth(final int splitWidth, final int effectiveDividerWidth, final Insets insets) {
        return Math.max(0, splitWidth - effectiveDividerWidth - insets.right
            - Math.max(effectiveMinimum(splitWidth, effectiveDividerWidth, insets), maximumWidth(splitWidth)));
    }
}

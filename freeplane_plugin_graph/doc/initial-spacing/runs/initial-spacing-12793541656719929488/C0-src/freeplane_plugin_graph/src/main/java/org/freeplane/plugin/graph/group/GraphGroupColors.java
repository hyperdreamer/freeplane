package org.freeplane.plugin.graph.group;

import java.awt.Color;

import org.freeplane.core.resources.ResourceController;

public final class GraphGroupColors {
    public static final String COLOR_PROPERTY_KEY = "graph_group_color";
    public static final Color DEFAULT_COLOR = new Color(0xDF, 0x62, 0x5D);

    private GraphGroupColors() {
    }

    public static Color currentColor() {
        try {
            final Color color = ResourceController.getResourceController().getColorProperty(COLOR_PROPERTY_KEY);
            return color != null ? color : DEFAULT_COLOR;
        }
        catch (final RuntimeException exception) {
            return DEFAULT_COLOR;
        }
    }
}

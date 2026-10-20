package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

/**
 * Immutable, Swing-safe display data for one graph workspace.
 */
public final class GraphWorkspacePresentation {
    private static final Set<String> APPROVED_COLORS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "#4E79A7", "#F28E2B", "#59A14F", "#E15759",
            "#76B7B2", "#B07AA1", "#EDC948", "#9C755F")));

    private final DisplaySettings displaySettings;
    private final List<MapColor> mapColors;

    private GraphWorkspacePresentation(final DisplaySettings displaySettings,
            final List<MapColor> mapColors) {
        this.displaySettings = Objects.requireNonNull(displaySettings, "displaySettings");
        this.mapColors = copyMapColors(mapColors);
    }

    public static GraphWorkspacePresentation defaults() {
        return new GraphWorkspacePresentation(DisplaySettings.defaults(), Collections.<MapColor>emptyList());
    }

    public static GraphWorkspacePresentation of(final DisplaySettings displaySettings,
            final List<MapColor> mapColors) {
        return new GraphWorkspacePresentation(displaySettings, mapColors);
    }

    public DisplaySettings displaySettings() {
        return displaySettings;
    }

    public List<MapColor> mapColors() {
        return mapColors;
    }

    private static List<MapColor> copyMapColors(final List<MapColor> values) {
        Objects.requireNonNull(values, "mapColors");
        final List<MapColor> copy = new ArrayList<MapColor>(values.size());
        final Set<MapReferenceId> ids = new HashSet<MapReferenceId>();
        for (final MapColor value : values) {
            final MapColor entry = Objects.requireNonNull(value, "mapColors entry");
            if (!ids.add(entry.mapReferenceId())) {
                throw new IllegalArgumentException("Duplicate map color for " + entry.mapReferenceId().value());
            }
            copy.add(entry);
        }
        return Collections.unmodifiableList(copy);
    }

    private static String validateColor(final String value) {
        Objects.requireNonNull(value, "color");
        if (!APPROVED_COLORS.contains(value)) {
            throw new IllegalArgumentException("Map color must be one of the approved map colors");
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphWorkspacePresentation)) {
            return false;
        }
        final GraphWorkspacePresentation that = (GraphWorkspacePresentation) other;
        return displaySettings.equals(that.displaySettings) && mapColors.equals(that.mapColors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displaySettings, mapColors);
    }

    @Override
    public String toString() {
        return "GraphWorkspacePresentation{" + "displaySettings=" + displaySettings
            + ", mapColors=" + mapColors + '}';
    }

    public static final class MapColor {
        private final MapReferenceId mapReferenceId;
        private final String color;

        private MapColor(final MapReferenceId mapReferenceId, final String color) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.color = validateColor(color);
        }

        public static MapColor of(final MapReferenceId mapReferenceId, final String color) {
            return new MapColor(mapReferenceId, color);
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        public String color() {
            return color;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MapColor)) {
                return false;
            }
            final MapColor that = (MapColor) other;
            return mapReferenceId.equals(that.mapReferenceId) && color.equals(that.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mapReferenceId, color);
        }

        @Override
        public String toString() {
            return "MapColor{" + "mapReferenceId=" + mapReferenceId + ", color='" + color + "'}";
        }
    }
}

package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.styles.StyleNameMappingHelper;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class ListTool {
    private static final String ICONS_NOTE = "This list includes built-in and user-defined Freeplane icons only; "
        + "emoji icons are referenced by the emoji character itself and are not listed here.";

    private final IconDescriptionResolver iconDescriptionResolver;
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;

    public ListTool(IconDescriptionResolver iconDescriptionResolver, AvailableMaps availableMaps,
                    AvailableMaps.MapAccessListener mapAccessListener) {
        this.iconDescriptionResolver = Objects.requireNonNull(iconDescriptionResolver, "iconDescriptionResolver");
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
    }

    public ListResponse listAvailableIcons() {
        return new ListResponse("application", null, listAvailableIconValues(), ICONS_NOTE);
    }

    public ListResponse listMapStyles(String mapIdentifierValue) {
        if (mapIdentifierValue == null || mapIdentifierValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing mapIdentifier.");
        }
        UUID mapIdentifier;
        try {
            mapIdentifier = UUID.fromString(mapIdentifierValue);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifierValue);
        }
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        return new ListResponse("map", mapIdentifierValue, StyleNameMappingHelper.listAvailableStyleNames(mapModel),
            null);
    }

    public ToolCallSummary buildToolCallSummary(String toolName, ListResponse response) {
        int valueCount = response == null || response.getValues() == null ? 0 : response.getValues().size();
        String summaryText = toolName + ": values=" + valueCount;
        if (response != null && response.getMapIdentifier() != null) {
            summaryText = summaryText + ", mapIdentifier=" + response.getMapIdentifier();
        }
        return new ToolCallSummary(toolName, summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(String toolName, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        String summaryText = toolName + " error: " + safeMessage;
        return new ToolCallSummary(toolName, summaryText, true);
    }

    private List<String> listAvailableIconValues() {
        Collection<MindIcon> icons = IconStoreFactory.ICON_STORE.getMindIcons();
        Set<String> descriptions = new LinkedHashSet<>();
        for (NamedIcon icon : icons) {
            if (icon == null || isEmojiIcon(icon)) {
                continue;
            }
            String description = iconDescriptionResolver.resolveDescription(icon);
            if (description != null && !description.trim().isEmpty()) {
                descriptions.add(description);
            }
        }
        return new ArrayList<>(descriptions);
    }

    private boolean isEmojiIcon(NamedIcon icon) {
        String name = icon.getName();
        return name != null && name.startsWith("emoji-");
    }
}

package org.freeplane.plugin.ai.tools.tagcategories;

import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.icon.TagCategoryAccess;
import org.freeplane.features.icon.TagCategoryState;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class GetTagCategoriesTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final TagCategoryAccess tagCategoryAccess;

    public GetTagCategoriesTool(AvailableMaps availableMaps,
                                AvailableMaps.MapAccessListener mapAccessListener,
                                TagCategoryAccess tagCategoryAccess) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.tagCategoryAccess = Objects.requireNonNull(tagCategoryAccess, "tagCategoryAccess");
    }

    public TagCategoryStatePayload getTagCategories(GetTagCategoriesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        TagCategoryState categoryState = tagCategoryAccess.readCurrentCategoryState(mapModel);
        return TagCategoryStatePayload.fromState(mapIdentifierValue, categoryState);
    }

    public ToolCallSummary buildToolCallSummary(GetTagCategoriesRequest request, TagCategoryStatePayload response) {
        int categoryCount = response == null || response.getCategories() == null ? 0 : response.getCategories().size();
        String summaryText = "getTagCategories: categories=" + categoryCount;
        String revision = response == null ? "" : ToolCallSummaryFormatter.sanitizeValue(response.getRevision());
        if (!revision.isEmpty()) {
            summaryText = summaryText + ", revision=" + revision;
        }
        return new ToolCallSummary("getTagCategories", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(GetTagCategoriesRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("getTagCategories", "getTagCategories error: " + safeMessage, true);
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value.trim();
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier, error);
        }
    }
}

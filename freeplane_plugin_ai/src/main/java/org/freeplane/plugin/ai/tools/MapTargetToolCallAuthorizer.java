package org.freeplane.plugin.ai.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.plugin.ai.maps.AvailableMaps;

public class MapTargetToolCallAuthorizer {
    static final String DOCUMENTATION_MAP_EDITING_MESSAGE =
        "The internal API documentation map cannot be edited.";
    static final String DOCUMENTATION_MAP_SCRIPTING_MESSAGE =
        "The internal API documentation map cannot be used as a scripting or formula target.";

    private static final Set<String> EDITING_TOOL_NAMES = Collections.unmodifiableSet(
        new LinkedHashSet<String>(Arrays.asList(
            "createNodes",
            "edit",
            "deleteNodes",
            "moveNodes",
            "createSummary",
            "moveNodesIntoSummary",
            "editConnectors",
            "editTagCategories")));
    private static final Set<String> SCRIPTING_OR_FORMULA_TOOL_NAMES = Collections.unmodifiableSet(
        new LinkedHashSet<String>(Arrays.asList(
            "previewFormulaUpdates",
            "applyFormulaUpdates")));

    public void assertAuthorized(String toolName, String mapIdentifier) {
        String normalizedToolName = normalizeToolName(toolName);
        if (!AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString().equals(normalizeMapIdentifier(mapIdentifier))) {
            return;
        }
        if (EDITING_TOOL_NAMES.contains(normalizedToolName)) {
            throw new IllegalStateException(DOCUMENTATION_MAP_EDITING_MESSAGE);
        }
        if (SCRIPTING_OR_FORMULA_TOOL_NAMES.contains(normalizedToolName)) {
            throw new IllegalStateException(DOCUMENTATION_MAP_SCRIPTING_MESSAGE);
        }
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing tool name");
        }
        return toolName.trim();
    }

    private String normalizeMapIdentifier(String mapIdentifier) {
        if (mapIdentifier == null) {
            return null;
        }
        String trimmedIdentifier = mapIdentifier.trim();
        return trimmedIdentifier.isEmpty() ? null : trimmedIdentifier;
    }
}

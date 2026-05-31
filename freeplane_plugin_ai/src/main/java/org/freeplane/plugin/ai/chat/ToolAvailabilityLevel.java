package org.freeplane.plugin.ai.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public enum ToolAvailabilityLevel {
    DISABLED("disabled", Collections.<String>emptySet()),
    READING("reading", readingToolNames()),
    EDITING("editing", editingToolNames()),
    SCRIPT_EXECUTION("script_execution", editingToolNames());

    private final String preferenceValue;
    private final Set<String> allowedToolNames;

    ToolAvailabilityLevel(String preferenceValue, Set<String> allowedToolNames) {
        this.preferenceValue = preferenceValue;
        this.allowedToolNames = allowedToolNames;
    }

    public static ToolAvailabilityLevel fromPreferenceValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EDITING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ToolAvailabilityLevel availability : values()) {
            if (availability.preferenceValue.equals(normalized)
                || availability.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return availability;
            }
        }
        return EDITING;
    }

    public boolean includesTools() {
        return this != DISABLED;
    }

    public boolean allowsTool(String toolName) {
        return toolName != null && allowedToolNames.contains(toolName);
    }

    Set<String> allowedToolNames() {
        return allowedToolNames;
    }

    String getPreferenceValue() {
        return preferenceValue;
    }

    public boolean includesEditing() {
        return this == EDITING || this == SCRIPT_EXECUTION;
    }

    public boolean includesScriptExecution() {
        return this == SCRIPT_EXECUTION;
    }

    private static Set<String> readingToolNames() {
        return unmodifiableLinkedHashSet(
            "readNodesWithDescendants",
            "readNodesWithDescendantsAsPlainText",
            "getSelectedMapAndNodeIdentifiers",
            "searchNodes",
            "selectSingleNode",
            "getApiDocumentation");
    }

    private static Set<String> editingToolNames() {
        LinkedHashSet<String> toolNames = new LinkedHashSet<String>(readingToolNames());
        toolNames.addAll(Arrays.asList(
            "fetchNodesForEditing",
            "getTagCategories",
            "editTagCategories",
            "deleteNodes",
            "listAvailableIcons",
            "listMapStyles",
            "editConnectors",
            "edit",
            "createNodes",
            "moveNodes",
            "createSummary",
            "moveNodesIntoSummary"));
        return Collections.unmodifiableSet(toolNames);
    }

    private static Set<String> unmodifiableLinkedHashSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }
}

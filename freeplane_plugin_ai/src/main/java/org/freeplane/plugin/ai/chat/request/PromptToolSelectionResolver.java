package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevelSettings;

public class PromptToolSelectionResolver {
    private final ToolAvailabilityLevelSettings chatToolAvailabilitySettings;

    public PromptToolSelectionResolver(ToolAvailabilityLevelSettings chatToolAvailabilitySettings) {
        this.chatToolAvailabilitySettings = chatToolAvailabilitySettings;
    }

    public ToolAvailabilityLevel resolveEffectiveToolAvailability(String toolAvailabilitySelectionValue) {
        String normalizedSelectionValue = normalizeSelectionValue(toolAvailabilitySelectionValue);
        return normalizedSelectionValue == null
            ? chatToolAvailabilitySettings.getToolAvailability()
            : ToolAvailabilityLevel.fromPreferenceValue(normalizedSelectionValue);
    }

    public ToolAvailabilityLevel resolveShownChatOverride(String toolAvailabilitySelectionValue) {
        String normalizedSelectionValue = normalizeSelectionValue(toolAvailabilitySelectionValue);
        return normalizedSelectionValue == null
            ? null
            : ToolAvailabilityLevel.fromPreferenceValue(normalizedSelectionValue);
    }

    private String normalizeSelectionValue(String selectionValue) {
        if (selectionValue == null) {
            return null;
        }
        String normalized = selectionValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

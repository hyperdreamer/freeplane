package org.freeplane.plugin.ai.chat;

class PromptToolSelectionResolver {
    private final ToolAvailabilityLevelSettings chatToolAvailabilitySettings;

    PromptToolSelectionResolver(ToolAvailabilityLevelSettings chatToolAvailabilitySettings) {
        this.chatToolAvailabilitySettings = chatToolAvailabilitySettings;
    }

    ToolAvailabilityLevel resolveEffectiveToolAvailability(String toolAvailabilitySelectionValue) {
        String normalizedSelectionValue = normalizeSelectionValue(toolAvailabilitySelectionValue);
        return normalizedSelectionValue == null
            ? chatToolAvailabilitySettings.getToolAvailability()
            : ToolAvailabilityLevel.fromPreferenceValue(normalizedSelectionValue);
    }

    ToolAvailabilityLevel resolveShownChatOverride(String toolAvailabilitySelectionValue) {
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

package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.model.AIModelSelection;

public class AiRequestMappings {
    private AiRequestMappings() {
    }

    public static String toSelectedModelOverride(AiModelSelection selection) {
        if (selection == null || selection.isCurrent()) {
            return null;
        }
        return AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName());
    }

    public static ToolAvailabilityLevel toToolAvailabilityLevel(AiToolAvailability toolAvailability) {
        if (toolAvailability == null) {
            return null;
        }
        switch (toolAvailability) {
            case DISABLED:
                return ToolAvailabilityLevel.DISABLED;
            case READING:
                return ToolAvailabilityLevel.READING;
            case EDITING:
                return ToolAvailabilityLevel.EDITING;
            case SCRIPT_EXECUTION:
                return ToolAvailabilityLevel.SCRIPT_EXECUTION;
            case CURRENT:
            default:
                return null;
        }
    }
}

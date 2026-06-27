package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

public class AiRequestMappings {
    private AiRequestMappings() {
    }

    public static org.freeplane.plugin.ai.model.AIModelConfiguration toModelConfiguration(
        AiModelConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        return org.freeplane.plugin.ai.model.AIModelConfiguration.of(
            toInternalModelSelection(configuration.getModelSelection()),
            configuration.getThinkingEffort(),
            configuration.getTemperature());
    }

    public static String toSelectedModelOverride(AiModelConfiguration configuration) {
        org.freeplane.plugin.ai.model.AIModelConfiguration internalConfiguration =
            toModelConfiguration(configuration);
        if (internalConfiguration == null || internalConfiguration.getModelSelection() == null) {
            return null;
        }
        AIModelSelection selection = internalConfiguration.getModelSelection();
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

    private static AIModelSelection toInternalModelSelection(AiModelSelection selection) {
        if (selection == null || selection.isDefaultModel()) {
            return null;
        }
        return AIModelSelection.fromSelectionValue(
            AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName()));
    }

}

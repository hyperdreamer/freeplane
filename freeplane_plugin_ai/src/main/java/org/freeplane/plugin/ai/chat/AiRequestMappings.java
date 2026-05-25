package org.freeplane.plugin.ai.chat;

import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.model.AIModelSelection;

class AiRequestMappings {
    private AiRequestMappings() {
    }

    static String toSelectedModelOverride(AiModelSelection selection) {
        if (selection == null || selection.isCurrent()) {
            return null;
        }
        return AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName());
    }

    static ChatToolAvailability toChatToolAvailability(AiToolAvailability toolAvailability) {
        if (toolAvailability == null) {
            return null;
        }
        switch (toolAvailability) {
            case DISABLED:
                return ChatToolAvailability.DISABLED;
            case READING:
                return ChatToolAvailability.READING;
            case EDITING:
                return ChatToolAvailability.EDITING;
            case CURRENT:
            default:
                return null;
        }
    }
}

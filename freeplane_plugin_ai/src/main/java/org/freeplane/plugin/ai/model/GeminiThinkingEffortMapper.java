package org.freeplane.plugin.ai.model;

import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import org.freeplane.api.ai.AiThinkingEffort;

class GeminiThinkingEffortMapper {
    private GeminiThinkingEffortMapper() {
    }

    static GeminiThinkingConfig.GeminiThinkingLevel toThinkingLevel(AiThinkingEffort effort) {
        if (effort == null || effort == AiThinkingEffort.NONE) {
            return null;
        }
        switch (effort) {
            case MAX:
            case XHIGH:
            case HIGH:
                return GeminiThinkingConfig.GeminiThinkingLevel.HIGH;
            case MEDIUM:
                return GeminiThinkingConfig.GeminiThinkingLevel.MEDIUM;
            case LOW:
                return GeminiThinkingConfig.GeminiThinkingLevel.LOW;
            case MINIMAL:
                return GeminiThinkingConfig.GeminiThinkingLevel.MINIMAL;
            default:
                return null;
        }
    }
}

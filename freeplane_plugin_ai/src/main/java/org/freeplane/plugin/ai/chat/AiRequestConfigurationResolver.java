package org.freeplane.plugin.ai.chat;

import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;

class AiRequestConfigurationResolver {
    private final AIProviderConfiguration configuration;

    AiRequestConfigurationResolver(AIProviderConfiguration configuration) {
        this.configuration = configuration;
    }

    Issue resolve(String selectedModelOverride) {
        String effectiveSelectionValue = selectedModelOverride == null || selectedModelOverride.trim().isEmpty()
            ? configuration.getSelectedModelValue()
            : selectedModelOverride;
        AIModelSelection selection = AIModelSelection.fromSelectionValue(effectiveSelectionValue);
        if (selection == null) {
            return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing AI model selection.");
        }
        String providerName = selection.getProviderName();
        if (AIChatModelFactory.PROVIDER_NAME_OPENROUTER.equalsIgnoreCase(providerName)) {
            if (configuration.getOpenRouterKey() == null || configuration.getOpenRouterKey().isEmpty()) {
                return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing OpenRouter key setting.");
            }
        }
        else if (AIChatModelFactory.PROVIDER_NAME_GEMINI.equalsIgnoreCase(providerName)) {
            if (configuration.getGeminiKey() == null || configuration.getGeminiKey().isEmpty()) {
                return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing Gemini key setting.");
            }
        }
        else if (AIChatModelFactory.PROVIDER_NAME_OLLAMA.equalsIgnoreCase(providerName)) {
            if (!configuration.hasOllamaServiceAddress()) {
                return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing Ollama service address setting.");
            }
        }
        else {
            return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Unknown AI provider selection.");
        }
        return null;
    }

    static class Issue {
        private final AiRequestStatus status;
        private final String detail;

        Issue(AiRequestStatus status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        AiRequestStatus getStatus() {
            return status;
        }

        String getDetail() {
            return detail;
        }
    }
}

package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;

public class AiRequestConfigurationResolver {
    private final AIProviderConfiguration configuration;

    public AiRequestConfigurationResolver(AIProviderConfiguration configuration) {
        this.configuration = configuration;
    }

    public Issue resolve(String selectedModelOverride) {
        String effectiveSelectionValue = selectedModelOverride == null || selectedModelOverride.trim().isEmpty()
            ? configuration.getSelectedModelValue()
            : selectedModelOverride;
        AIModelSelection selection = AIModelSelection.fromSelectionValue(effectiveSelectionValue);
        if (selection == null) {
            return new Issue(
                AiRequestStatus.CONFIGURATION_ERROR,
                TextUtils.getText("ai_model_selection_missing"));
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

    public static class Issue {
        private final AiRequestStatus status;
        private final String detail;

        public Issue(AiRequestStatus status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public AiRequestStatus getStatus() {
            return status;
        }

        public String getDetail() {
            return detail;
        }
    }
}

package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.model.OpenAICompatibleProvider;
import org.freeplane.plugin.ai.model.OpenAICompatibleProviderConfiguration;

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
        OpenAICompatibleProvider openAICompatibleProvider =
            OpenAICompatibleProvider.fromProviderName(providerName);
        if (openAICompatibleProvider != null) {
            OpenAICompatibleProviderConfiguration providerConfiguration =
                configuration.getOpenAICompatibleConfiguration(providerName);
            if (providerConfiguration != null && providerConfiguration.isConfigured()) {
                return null;
            }
            String missingSetting = openAICompatibleProvider == OpenAICompatibleProvider.CUSTOM
                ? "service address"
                : "key";
            return new Issue(
                AiRequestStatus.CONFIGURATION_ERROR,
                "Missing " + openAICompatibleProvider.getDisplayName() + " " + missingSetting + " setting.");
        }
        if (AIChatModelFactory.PROVIDER_NAME_GEMINI.equalsIgnoreCase(providerName)) {
            return configuration.isGeminiConfigured()
                ? null
                : new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing Gemini key setting.");
        }
        if (AIChatModelFactory.PROVIDER_NAME_OLLAMA.equalsIgnoreCase(providerName)) {
            return configuration.isOllamaConfigured()
                ? null
                : new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Missing Ollama service address setting.");
        }
        return new Issue(AiRequestStatus.CONFIGURATION_ERROR, "Unknown AI provider selection.");
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

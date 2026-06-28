package org.freeplane.plugin.ai.model;

import java.util.Collections;
import java.util.Map;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.resources.ResourceController;

public class AIProviderConfiguration {
    private static final String AI_PROVIDER_NAME_PROPERTY = "ai_provider_name";
    private static final String AI_MODEL_NAME_PROPERTY = "ai_model_name";
    private static final String AI_SELECTED_MODEL_PROPERTY = "ai_selected_model";
    public static final String AI_THINKING_EFFORT_PROPERTY = "ai_thinking_effort";
    public static final String AI_TEMPERATURE_PROPERTY = "ai_temperature";
    private static final String AI_OPENROUTER_SERVICE_ADDRESS_PROPERTY = "ai_openrouter_service_address";
    private static final String AI_OPENROUTER_KEY_PROPERTY = "ai_openrouter_key";
    private static final String AI_OPENROUTER_MODEL_ALLOWLIST_PROPERTY = "ai_openrouter_model_allowlist";
    private static final String AI_GEMINI_SERVICE_ADDRESS_PROPERTY = "ai_gemini_service_address";
    private static final String AI_GEMINI_KEY_PROPERTY = "ai_gemini_key";
    private static final String AI_GEMINI_MODEL_LIST_PROPERTY = "ai_gemini_model_list";
    private static final String AI_OLLAMA_SERVICE_ADDRESS_PROPERTY = "ai_ollama_service_address";
    private static final String AI_OLLAMA_API_KEY_PROPERTY = "ai_ollama_api_key";
    private static final String AI_OLLAMA_MODEL_ALLOWLIST_PROPERTY = "ai_ollama_model_allowlist";

    private final ResourceController resourceController;

    public AIProviderConfiguration() {
        this(ResourceController.getResourceController());
    }

    AIProviderConfiguration(ResourceController resourceController) {
        this.resourceController = resourceController;
    }

    public String getSelectedModelValue() {
        AIModelSelection selection = getDefaultModelSelection();
        if (selection == null) {
            return null;
        }
        return AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName());
    }


    public AIModelConfiguration getDefaultModelConfiguration() {
        return AIModelConfiguration.of(
            getDefaultModelSelection(),
            parseThinkingEffort(resourceController.getProperty(AI_THINKING_EFFORT_PROPERTY)),
            AIModelTemperatureStorage.fromGlobalPreferenceValue(
                resourceController.getProperty(AI_TEMPERATURE_PROPERTY)));
    }

    public String getStoredSelectedModelValue() {
        return resourceController.getProperty(AI_SELECTED_MODEL_PROPERTY);
    }

    public void setSelectedModelValue(String selectionValue) {
        resourceController.setProperty(AI_SELECTED_MODEL_PROPERTY, selectionValue);
    }

    public void setThinkingEffortValue(AiThinkingEffort thinkingEffort) {
        AiThinkingEffort value = thinkingEffort == null ? AiThinkingEffort.MEDIUM : thinkingEffort;
        resourceController.setProperty(AI_THINKING_EFFORT_PROPERTY, value.name());
    }

    public void setTemperatureValue(AiTemperature temperature) {
        resourceController.setProperty(
            AI_TEMPERATURE_PROPERTY,
            AIModelTemperatureStorage.toPreferenceValue(temperature));
    }

    public String getOpenrouterServiceAddress() {
        return resourceController.getProperty(AI_OPENROUTER_SERVICE_ADDRESS_PROPERTY);
    }

    public String getOpenRouterKey() {
        return resourceController.getProperty(AI_OPENROUTER_KEY_PROPERTY);
    }

    public String getOpenrouterModelAllowlistValue() {
        return resourceController.getProperty(AI_OPENROUTER_MODEL_ALLOWLIST_PROPERTY);
    }

    public String getGeminiServiceAddress() {
        return resourceController.getProperty(AI_GEMINI_SERVICE_ADDRESS_PROPERTY);
    }

    public String getGeminiKey() {
        return resourceController.getProperty(AI_GEMINI_KEY_PROPERTY);
    }

    public String getGeminiModelListValue() {
        return resourceController.getProperty(AI_GEMINI_MODEL_LIST_PROPERTY);
    }

    public String getOllamaServiceAddress() {
        return resourceController.getProperty(AI_OLLAMA_SERVICE_ADDRESS_PROPERTY);
    }

    public String getOllamaApiKey() {
        return resourceController.getProperty(AI_OLLAMA_API_KEY_PROPERTY);
    }

    public boolean hasOllamaServiceAddress() {
        return hasNonBlankText(getOllamaServiceAddress());
    }

    public Map<String, String> getOllamaRequestHeaders() {
        String apiKey = trimToEmpty(getOllamaApiKey());
        if (apiKey.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.singletonMap("Authorization", "Bearer " + apiKey);
    }

    public String getOllamaModelAllowlistValue() {
        return resourceController.getProperty(AI_OLLAMA_MODEL_ALLOWLIST_PROPERTY);
    }

    private AIModelSelection getDefaultModelSelection() {
        AIModelSelection storedSelection = AIModelSelection.fromSelectionValue(getStoredSelectedModelValue());
        if (storedSelection != null) {
            return storedSelection;
        }
        String providerName = resourceController.getProperty(AI_PROVIDER_NAME_PROPERTY);
        String modelName = resourceController.getProperty(AI_MODEL_NAME_PROPERTY);
        if (providerName == null || providerName.isEmpty() || modelName == null || modelName.isEmpty()) {
            return null;
        }
        return AIModelSelection.fromSelectionValue(AIModelSelection.createSelectionValue(providerName, modelName));
    }

    private AiThinkingEffort parseThinkingEffort(String value) {
        AiThinkingEffort thinkingEffort = AiThinkingEffort.fromPreferenceValue(value);
        return thinkingEffort == null ? AiThinkingEffort.MEDIUM : thinkingEffort;
    }

    private boolean hasNonBlankText(String value) {
        return !trimToEmpty(value).isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

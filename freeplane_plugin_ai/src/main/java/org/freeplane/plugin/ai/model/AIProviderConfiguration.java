package org.freeplane.plugin.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private static final String AI_GEMINI_SERVICE_ADDRESS_PROPERTY = "ai_gemini_service_address";
    private static final String AI_GEMINI_KEY_PROPERTY = "ai_gemini_key";
    private static final String AI_GEMINI_MODELS_PROPERTY = "ai_gemini_models";
    private static final String AI_OLLAMA_SERVICE_ADDRESS_PROPERTY = "ai_ollama_service_address";
    private static final String AI_OLLAMA_API_KEY_PROPERTY = "ai_ollama_api_key";
    private static final String AI_OLLAMA_MODELS_PROPERTY = "ai_ollama_models";

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

    public List<OpenAICompatibleProviderConfiguration> getOpenAICompatibleConfigurations() {
        List<OpenAICompatibleProviderConfiguration> configurations = new ArrayList<>();
        for (OpenAICompatibleProvider provider : OpenAICompatibleProvider.values()) {
            configurations.add(createOpenAICompatibleConfiguration(provider));
        }
        return Collections.unmodifiableList(configurations);
    }

    public OpenAICompatibleProviderConfiguration getOpenAICompatibleConfiguration(String providerName) {
        OpenAICompatibleProvider provider = OpenAICompatibleProvider.fromProviderName(providerName);
        return provider == null ? null : createOpenAICompatibleConfiguration(provider);
    }

    public boolean isGeminiConfigured() {
        return hasNonBlankText(getGeminiKey());
    }

    public boolean isOllamaConfigured() {
        return hasNonBlankText(getOllamaServiceAddress());
    }

    public boolean hasConfiguredProvider() {
        for (OpenAICompatibleProviderConfiguration configuration : getOpenAICompatibleConfigurations()) {
            if (configuration.isConfigured()) {
                return true;
            }
        }
        return isGeminiConfigured() || isOllamaConfigured();
    }

    public String getGeminiServiceAddress() {
        return valueOrDefault(
            resourceController.getProperty(AI_GEMINI_SERVICE_ADDRESS_PROPERTY),
            AIChatModelFactory.DEFAULT_GEMINI_SERVICE_ADDRESS);
    }

    public String getGeminiKey() {
        return trimToEmpty(resourceController.getProperty(AI_GEMINI_KEY_PROPERTY));
    }

    public AIModelListConfiguration getGeminiModelListConfiguration() {
        return AIModelListConfiguration.parse(resourceController.getProperty(AI_GEMINI_MODELS_PROPERTY));
    }

    public String getOllamaServiceAddress() {
        return trimToEmpty(resourceController.getProperty(AI_OLLAMA_SERVICE_ADDRESS_PROPERTY));
    }

    public String getOllamaApiKey() {
        return trimToEmpty(resourceController.getProperty(AI_OLLAMA_API_KEY_PROPERTY));
    }

    public Map<String, String> getOllamaRequestHeaders() {
        String apiKey = getOllamaApiKey();
        if (apiKey.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.singletonMap("Authorization", "Bearer " + apiKey);
    }

    public AIModelListConfiguration getOllamaModelListConfiguration() {
        return AIModelListConfiguration.parse(resourceController.getProperty(AI_OLLAMA_MODELS_PROPERTY));
    }

    private OpenAICompatibleProviderConfiguration createOpenAICompatibleConfiguration(
        OpenAICompatibleProvider provider) {
        String propertyPrefix = "ai_" + provider.getProviderName();
        String serviceAddress = valueOrDefault(
            resourceController.getProperty(propertyPrefix + "_service_address"),
            defaultServiceAddress(provider));
        String modelsAddress = appendPath(serviceAddress, "models");
        if (provider == OpenAICompatibleProvider.CUSTOM) {
            String configuredModelsAddress = trimToEmpty(
                resourceController.getProperty("ai_custom_models_address"));
            if (!configuredModelsAddress.isEmpty()) {
                modelsAddress = configuredModelsAddress;
            }
        }
        return new OpenAICompatibleProviderConfiguration(
            provider,
            serviceAddress,
            modelsAddress,
            resourceController.getProperty(propertyPrefix + "_key"),
            AIModelListConfiguration.parse(resourceController.getProperty(propertyPrefix + "_models")));
    }

    private String defaultServiceAddress(OpenAICompatibleProvider provider) {
        switch (provider) {
            case OPENAI:
                return AIChatModelFactory.DEFAULT_OPENAI_SERVICE_ADDRESS;
            case OPENROUTER:
                return AIChatModelFactory.DEFAULT_OPENROUTER_SERVICE_ADDRESS;
            case REQUESTY:
                return AIChatModelFactory.DEFAULT_REQUESTY_SERVICE_ADDRESS;
            case CUSTOM:
            default:
                return "";
        }
    }

    private String appendPath(String serviceAddress, String path) {
        if (serviceAddress.isEmpty()) {
            return "";
        }
        return serviceAddress.endsWith("/") ? serviceAddress + path : serviceAddress + "/" + path;
    }

    private String valueOrDefault(String value, String defaultValue) {
        String normalizedValue = trimToEmpty(value);
        return normalizedValue.isEmpty() ? defaultValue : normalizedValue;
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

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

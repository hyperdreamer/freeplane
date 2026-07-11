package org.freeplane.plugin.ai.model;

import java.util.List;
import java.util.Map;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AIProviderConfigurationTest {
    @Test
    public void returnsIndependentConfiguredProviders() {
        ResourceController resources = mock(ResourceController.class);
        configureProvider(resources, "openai", "https://openai.example/v1", "openai-key", "openai-model");
        configureProvider(resources, "openrouter", "https://openrouter.example/v1", "router-key", "router/model");
        configureProvider(resources, "requesty", "https://requesty.example/v1", "requesty-key", "requesty/model");
        configureProvider(resources, "custom", "https://custom.example/v1", "custom-key", "custom-model");
        when(resources.getProperty("ai_custom_models_address"))
            .thenReturn("https://catalog.custom.example/models");
        AIProviderConfiguration configuration = configurationWith(resources);

        List<OpenAICompatibleProviderConfiguration> providers =
            configuration.getOpenAICompatibleConfigurations();

        assertThat(providers).extracting(provider -> provider.getProvider().getProviderName())
            .containsExactly("openai", "openrouter", "requesty", "custom");
        assertThat(providers).extracting(OpenAICompatibleProviderConfiguration::getApiKey)
            .containsExactly("openai-key", "router-key", "requesty-key", "custom-key");
        assertThat(providers).extracting(OpenAICompatibleProviderConfiguration::getModelsAddress)
            .containsExactly(
                "https://openai.example/v1/models",
                "https://openrouter.example/v1/models",
                "https://requesty.example/v1/models",
                "https://catalog.custom.example/models");
        assertThat(providers).extracting(provider ->
            provider.getModelListConfiguration().getLiteralModelNames().get(0))
            .containsExactly("openai-model", "router/model", "requesty/model", "custom-model");
        assertThat(providers).allMatch(OpenAICompatibleProviderConfiguration::isConfigured);
    }

    @Test
    public void providerActivationUsesItsOwnRequiredField() {
        ResourceController resources = mock(ResourceController.class);
        when(resources.getProperty("ai_openai_key")).thenReturn(" ");
        when(resources.getProperty("ai_openrouter_key")).thenReturn("router-key");
        when(resources.getProperty("ai_requesty_key")).thenReturn(null);
        when(resources.getProperty("ai_custom_service_address")).thenReturn("https://custom.example/v1");
        when(resources.getProperty("ai_custom_key")).thenReturn("");
        when(resources.getProperty("ai_gemini_key")).thenReturn("gemini-key");
        when(resources.getProperty("ai_ollama_service_address")).thenReturn("https://ollama.example");
        AIProviderConfiguration configuration = configurationWith(resources);

        List<OpenAICompatibleProviderConfiguration> providers =
            configuration.getOpenAICompatibleConfigurations();

        assertThat(providers.get(0).isConfigured()).isFalse();
        assertThat(providers.get(1).isConfigured()).isTrue();
        assertThat(providers.get(2).isConfigured()).isFalse();
        assertThat(providers.get(3).isConfigured()).isTrue();
        assertThat(configuration.isGeminiConfigured()).isTrue();
        assertThat(configuration.isOllamaConfigured()).isTrue();
        assertThat(configuration.hasConfiguredProvider()).isTrue();
    }

    @Test
    public void getOllamaRequestHeadersReturnsAuthorizationHeaderOnlyForConfiguredApiKey() {
        ResourceController resources = mock(ResourceController.class);
        when(resources.getProperty("ai_ollama_api_key")).thenReturn("  token-123  ", "  ");
        AIProviderConfiguration configuration = configurationWith(resources);

        Map<String, String> configuredHeaders = configuration.getOllamaRequestHeaders();
        Map<String, String> blankHeaders = configuration.getOllamaRequestHeaders();

        assertThat(configuredHeaders).containsEntry("Authorization", "Bearer token-123");
        assertThat(blankHeaders).isEmpty();
    }

    @Test
    public void getSelectedModelValueReturnsStoredSelectedModel() {
        ResourceController resources = mock(ResourceController.class);
        when(resources.getProperty("ai_selected_model")).thenReturn("gemini|gemini-2.5-flash");
        AIProviderConfiguration configuration = configurationWith(resources);

        assertThat(configuration.getSelectedModelValue()).isEqualTo("gemini|gemini-2.5-flash");
    }

    @Test
    public void getDefaultModelConfigurationParsesStoredModelThinkingAndTemperature() {
        ResourceController resources = mock(ResourceController.class);
        when(resources.getProperty("ai_selected_model")).thenReturn("openrouter|openai/gpt-5");
        when(resources.getProperty("ai_thinking_effort")).thenReturn(" XHIGH ");
        when(resources.getProperty("ai_temperature")).thenReturn(" 0.25 ");
        AIProviderConfiguration configuration = configurationWith(resources);

        AIModelConfiguration modelConfiguration = configuration.getDefaultModelConfiguration();

        assertThat(modelConfiguration.getModelSelection())
            .isEqualTo(AIModelSelection.fromSelectionValue("openrouter|openai/gpt-5"));
        assertThat(modelConfiguration.getThinkingEffort()).isEqualTo(AiThinkingEffort.XHIGH);
        assertThat(modelConfiguration.getTemperature()).isEqualTo(AiTemperature.of(0.25));
    }

    @Test
    public void writesThinkingEffortAndTemperaturePreferences() {
        ResourceController resources = mock(ResourceController.class);
        AIProviderConfiguration configuration = configurationWith(resources);

        configuration.setThinkingEffortValue(AiThinkingEffort.LOW);
        configuration.setThinkingEffortValue(null);
        configuration.setTemperatureValue(AiTemperature.of(0.2));
        configuration.setTemperatureValue(AiTemperature.modelDefault());

        verify(resources).setProperty("ai_thinking_effort", "LOW");
        verify(resources).setProperty("ai_thinking_effort", "MEDIUM");
        verify(resources).setProperty("ai_temperature", "0.2");
        verify(resources).setProperty("ai_temperature", "model_default");
    }

    private void configureProvider(ResourceController resources,
                                   String provider,
                                   String serviceAddress,
                                   String key,
                                   String models) {
        when(resources.getProperty("ai_" + provider + "_service_address")).thenReturn(serviceAddress);
        when(resources.getProperty("ai_" + provider + "_key")).thenReturn(key);
        when(resources.getProperty("ai_" + provider + "_models")).thenReturn(models);
    }

    private AIProviderConfiguration configurationWith(ResourceController resourceController) {
        return new AIProviderConfiguration(resourceController);
    }
}

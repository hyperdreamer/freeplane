package org.freeplane.plugin.ai.model;

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
    public void getOllamaRequestHeaders_returnsAuthorizationHeaderForConfiguredApiKey() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_ollama_api_key")).thenReturn("  token-123  ");
        AIProviderConfiguration uut = configurationWith(resourceController);

        Map<String, String> requestHeaders = uut.getOllamaRequestHeaders();

        assertThat(requestHeaders).containsEntry("Authorization", "Bearer token-123");
    }

    @Test
    public void getOllamaRequestHeaders_returnsEmptyMapForBlankApiKey() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_ollama_api_key")).thenReturn("  ");
        AIProviderConfiguration uut = configurationWith(resourceController);

        Map<String, String> requestHeaders = uut.getOllamaRequestHeaders();

        assertThat(requestHeaders).isEmpty();
    }

    @Test
    public void hasOllamaServiceAddress_returnsTrueOnlyForNonBlankValue() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_ollama_service_address"))
            .thenReturn("  ", "https://example.ollama.test");
        AIProviderConfiguration uut = configurationWith(resourceController);

        boolean hasAddressBeforeUpdate = uut.hasOllamaServiceAddress();
        boolean hasAddressAfterUpdate = uut.hasOllamaServiceAddress();

        assertThat(hasAddressBeforeUpdate).isFalse();
        assertThat(hasAddressAfterUpdate).isTrue();
    }

    @Test
    public void getSelectedModelValue_returnsStoredSelectedModel() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_selected_model")).thenReturn("gemini|gemini-2.5-flash");
        AIProviderConfiguration uut = configurationWith(resourceController);

        assertThat(uut.getSelectedModelValue()).isEqualTo("gemini|gemini-2.5-flash");
    }

    @Test
    public void getDefaultModelConfiguration_parsesStoredModelThinkingAndTemperature() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_selected_model")).thenReturn("openrouter|openai/gpt-5");
        when(resourceController.getProperty("ai_thinking_effort")).thenReturn(" XHIGH ");
        when(resourceController.getProperty("ai_temperature")).thenReturn(" 0.25 ");
        AIProviderConfiguration uut = configurationWith(resourceController);

        AIModelConfiguration modelConfiguration = uut.getDefaultModelConfiguration();

        assertThat(modelConfiguration.getModelSelection())
            .isEqualTo(AIModelSelection.fromSelectionValue("openrouter|openai/gpt-5"));
        assertThat(modelConfiguration.getThinkingEffort()).isEqualTo(AiThinkingEffort.XHIGH);
        assertThat(modelConfiguration.getTemperature()).isEqualTo(AiTemperature.of(0.25));
    }

    @Test
    public void setThinkingEffortValue_writesThinkingEffortPreference() {
        ResourceController resourceController = mock(ResourceController.class);
        AIProviderConfiguration uut = configurationWith(resourceController);

        uut.setThinkingEffortValue(AiThinkingEffort.LOW);
        uut.setThinkingEffortValue(null);

        verify(resourceController).setProperty("ai_thinking_effort", "LOW");
        verify(resourceController).setProperty("ai_thinking_effort", "MEDIUM");
    }

    @Test
    public void setTemperatureValue_writesTemperaturePreference() {
        ResourceController resourceController = mock(ResourceController.class);
        AIProviderConfiguration uut = configurationWith(resourceController);

        uut.setTemperatureValue(AiTemperature.of(0.2));
        uut.setTemperatureValue(AiTemperature.modelDefault());

        verify(resourceController).setProperty("ai_temperature", "0.2");
        verify(resourceController).setProperty("ai_temperature", "model_default");
    }

    @Test
    public void getDefaultModelConfiguration_usesMediumThinkingForBlankInvalidAndLegacyInheritValues() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_selected_model")).thenReturn("openrouter|openai/gpt-5");
        when(resourceController.getProperty("ai_thinking_effort")).thenReturn("inherit", "not-a-value", "  ");
        when(resourceController.getProperty("ai_temperature")).thenReturn("not-a-number");
        AIProviderConfiguration uut = configurationWith(resourceController);

        assertThat(uut.getDefaultModelConfiguration().getThinkingEffort()).isEqualTo(AiThinkingEffort.MEDIUM);
        assertThat(uut.getDefaultModelConfiguration().getThinkingEffort()).isEqualTo(AiThinkingEffort.MEDIUM);
        AIModelConfiguration modelConfiguration = uut.getDefaultModelConfiguration();
        assertThat(modelConfiguration.getThinkingEffort()).isEqualTo(AiThinkingEffort.MEDIUM);
        assertThat(modelConfiguration.getTemperature()).isEqualTo(AiTemperature.modelDefault());
    }

    private AIProviderConfiguration configurationWith(ResourceController resourceController) {
        return new AIProviderConfiguration(resourceController);
    }
}

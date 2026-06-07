package org.freeplane.plugin.ai.model;

import java.util.Map;
import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    public void getSelectedModelValue_prefersExplicitOverride() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_selected_model")).thenReturn("gemini|gemini-2.5-flash");
        AIProviderConfiguration uut = new AIProviderConfiguration(resourceController, "openrouter|openai/gpt-4.1-mini");

        assertThat(uut.getSelectedModelValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
    }

    private AIProviderConfiguration configurationWith(ResourceController resourceController) {
        return new AIProviderConfiguration(resourceController);
    }
}

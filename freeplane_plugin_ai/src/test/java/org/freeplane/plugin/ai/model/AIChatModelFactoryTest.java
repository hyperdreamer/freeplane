package org.freeplane.plugin.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AIChatModelFactoryTest {

    @Test
    public void createChatLanguageModel_setsMaxRetriesForOpenRouter() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getSelectedModelValue())
            .thenReturn(AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OPENROUTER, "openai/gpt-5"));
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterServiceAddress()).thenReturn("https://openrouter.ai/api/v1");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maxRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
    }

    @Test
    public void createChatLanguageModel_setsMaxRetriesForGemini() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getSelectedModelValue())
            .thenReturn(AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_GEMINI, "gemini-2.0-flash"));
        when(configuration.getGeminiKey()).thenReturn("test-key");
        when(configuration.getGeminiServiceAddress()).thenReturn("https://generativelanguage.googleapis.com/v1beta");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maximumRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
    }

    @Test
    public void createChatLanguageModel_setsMaxRetriesForOllama() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getSelectedModelValue())
            .thenReturn(AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2"));
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maxRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
    }

    @Test
    public void createChatLanguageModel_setsAuthorizationHeaderForOllamaWhenConfigured() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getSelectedModelValue())
            .thenReturn(AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2"));
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.singletonMap("Authorization", "Bearer token-123"));

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(ollamaRequestHeaders(chatModel))
            .isEqualTo(Collections.singletonMap("Authorization", "Bearer token-123"));
    }

    @Test
    public void createChatLanguageModel_omitsAuthorizationHeaderForOllamaWhenNotConfigured() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getSelectedModelValue())
            .thenReturn(AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2"));
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(ollamaRequestHeaders(chatModel)).isEqualTo(Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> ollamaRequestHeaders(ChatModel chatModel) throws Exception {
        Object ollamaClient = fieldValue(chatModel, "client");
        Supplier<Map<String, String>> customHeadersSupplier =
            (Supplier<Map<String, String>>) fieldValue(ollamaClient, "customHeadersSupplier");
        return customHeadersSupplier.get();
    }

    private Object fieldValue(Object target, String fieldName) throws Exception {
        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    return field.get(target);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new NoSuchFieldException(fieldName);
    }
}

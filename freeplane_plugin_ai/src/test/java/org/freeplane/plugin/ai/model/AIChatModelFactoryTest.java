package org.freeplane.plugin.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.freeplane.api.ai.AiThinkingEffort;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AIChatModelFactoryTest {

    @Test
    public void createChatLanguageModel_setsMaxRetriesForOpenRouter() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_OPENROUTER, "openai/gpt-5");
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterServiceAddress()).thenReturn("https://openrouter.ai/api/v1");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maxRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
        assertThat(openAiRequestParameters(chatModel).parallelToolCalls()).isFalse();
    }

    @Test
    public void createChatLanguageModel_appliesOpenRouterTemperatureAndThinkingEffort() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-5"),
            AiThinkingEffort.XHIGH,
            Double.valueOf(0.2));
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterServiceAddress()).thenReturn("https://openrouter.ai/api/v1");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);
        OpenAiChatRequestParameters parameters = openAiRequestParameters(chatModel);

        assertThat(parameters.temperature()).isEqualTo(0.2);
        assertThat(parameters.reasoningEffort()).isEqualTo("xhigh");
        assertThat(parameters.parallelToolCalls()).isFalse();
    }

    @Test
    public void createChatLanguageModel_resolvesRequestConfigurationOverDefaultsByField() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration defaultConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-5"),
            AiThinkingEffort.HIGH,
            Double.valueOf(0.9));
        AIModelConfiguration requestConfiguration = AIModelConfiguration.of(
            null,
            AiThinkingEffort.NONE,
            null);
        when(configuration.getDefaultModelConfiguration()).thenReturn(defaultConfiguration);
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterServiceAddress()).thenReturn("https://openrouter.ai/api/v1");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration, requestConfiguration);
        OpenAiChatRequestParameters parameters = openAiRequestParameters(chatModel);

        assertThat(parameters.temperature()).isEqualTo(0.9);
        assertThat(parameters.reasoningEffort()).isEqualTo("none");
    }

    @Test
    public void createChatLanguageModel_setsMaxRetriesForGemini() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_GEMINI, "gemini-2.0-flash");
        when(configuration.getGeminiKey()).thenReturn("test-key");
        when(configuration.getGeminiServiceAddress()).thenReturn("https://generativelanguage.googleapis.com/v1beta");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maximumRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
    }

    @Test
    public void createChatLanguageModel_appliesGeminiTemperatureAndMappedThinkingLevel() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("gemini|gemini-2.5-flash"),
            AiThinkingEffort.MAX,
            Double.valueOf(0.3));
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
        when(configuration.getGeminiKey()).thenReturn("test-key");
        when(configuration.getGeminiServiceAddress()).thenReturn("https://generativelanguage.googleapis.com/v1beta");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(defaultRequestParameters(chatModel).temperature()).isEqualTo(0.3);
        GeminiThinkingConfig thinkingConfig = (GeminiThinkingConfig) fieldValue(chatModel, "thinkingConfig");
        assertThat(thinkingConfig.thinkingLevel()).isEqualTo("high");
        assertThat(thinkingConfig.includeThoughts()).isTrue();
        assertThat(fieldValue(chatModel, "returnThinking")).isEqualTo(Boolean.TRUE);
        assertThat(fieldValue(chatModel, "sendThinking")).isEqualTo(Boolean.TRUE);
    }

    @Test
    public void createChatLanguageModel_omitsGeminiThinkingForExplicitNone() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("gemini|gemini-3-pro-preview"),
            AiThinkingEffort.NONE,
            null);
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
        when(configuration.getGeminiKey()).thenReturn("test-key");
        when(configuration.getGeminiServiceAddress()).thenReturn("https://generativelanguage.googleapis.com/v1beta");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "thinkingConfig")).isNull();
    }

    @Test
    public void createChatLanguageModel_keepsGemini3AutomaticThinkingWhenThinkingIsInherited() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_GEMINI, "gemini-3-pro-preview");
        when(configuration.getGeminiKey()).thenReturn("test-key");
        when(configuration.getGeminiServiceAddress()).thenReturn("https://generativelanguage.googleapis.com/v1beta");

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        GeminiThinkingConfig thinkingConfig = (GeminiThinkingConfig) fieldValue(chatModel, "thinkingConfig");
        assertThat(thinkingConfig).isNotNull();
        assertThat(thinkingConfig.includeThoughts()).isTrue();
    }

    @Test
    public void createChatLanguageModel_setsMaxRetriesForOllama() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2");
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(fieldValue(chatModel, "maxRetries")).isEqualTo(AIChatModelFactory.CHAT_MODEL_MAX_RETRIES);
        OllamaChatRequestParameters parameters =
            (OllamaChatRequestParameters) fieldValue(chatModel, "defaultRequestParameters");
        assertThat(parameters.think()).isNull();
    }

    @Test
    public void createChatLanguageModel_appliesOllamaTemperatureAndThinkingEnabledForExplicitThinking() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("ollama|llama3.2"),
            AiThinkingEffort.HIGH,
            Double.valueOf(0.4));
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);
        OllamaChatRequestParameters parameters =
            (OllamaChatRequestParameters) fieldValue(chatModel, "defaultRequestParameters");

        assertThat(parameters.temperature()).isEqualTo(0.4);
        assertThat(parameters.think()).isEqualTo(Boolean.TRUE);
    }

    @Test
    public void createChatLanguageModel_disablesOllamaThinkingForExplicitNone() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("ollama|llama3.2"),
            AiThinkingEffort.NONE,
            null);
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);
        OllamaChatRequestParameters parameters =
            (OllamaChatRequestParameters) fieldValue(chatModel, "defaultRequestParameters");

        assertThat(parameters.think()).isEqualTo(Boolean.FALSE);
    }

    @Test
    public void createChatLanguageModel_setsAuthorizationHeaderForOllamaWhenConfigured() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2");
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.singletonMap("Authorization", "Bearer token-123"));

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(ollamaRequestHeaders(chatModel))
            .isEqualTo(Collections.singletonMap("Authorization", "Bearer token-123"));
    }

    @Test
    public void createChatLanguageModel_omitsAuthorizationHeaderForOllamaWhenNotConfigured() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        whenDefaultModelConfiguration(configuration, AIChatModelFactory.PROVIDER_NAME_OLLAMA, "llama3.2");
        when(configuration.getOllamaServiceAddress()).thenReturn("https://example.ollama.test");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());

        ChatModel chatModel = AIChatModelFactory.createChatLanguageModel(configuration);

        assertThat(ollamaRequestHeaders(chatModel)).isEqualTo(Collections.emptyMap());
    }

    private void whenDefaultModelConfiguration(AIProviderConfiguration configuration,
                                               String providerName,
                                               String modelName) {
        AIModelConfiguration modelConfiguration = AIModelConfiguration.fromSelectionValue(
            AIModelSelection.createSelectionValue(providerName, modelName));
        when(configuration.getDefaultModelConfiguration()).thenReturn(modelConfiguration);
    }

    private OpenAiChatRequestParameters openAiRequestParameters(ChatModel chatModel) throws Exception {
        return (OpenAiChatRequestParameters) fieldValue(chatModel, "defaultRequestParameters");
    }

    private ChatRequestParameters defaultRequestParameters(ChatModel chatModel) throws Exception {
        return (ChatRequestParameters) fieldValue(chatModel, "defaultRequestParameters");
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

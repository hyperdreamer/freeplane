package org.freeplane.plugin.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;

public class AIChatModelFactory {

    public static final String PROVIDER_NAME_OPENROUTER = "openrouter";
    public static final String PROVIDER_NAME_GEMINI = "gemini";
    public static final String PROVIDER_NAME_OLLAMA = "ollama";
    public static final String DEFAULT_OPENROUTER_SERVICE_ADDRESS = "https://openrouter.ai/api/v1";
    static final int CHAT_MODEL_MAX_RETRIES = 2;

    private AIChatModelFactory() {
    }

    public static ChatModel createChatLanguageModel(AIProviderConfiguration configuration) {
        return createChatLanguageModel(configuration, null);
    }

    public static ChatModel createChatLanguageModel(AIProviderConfiguration configuration,
                                                    AIModelConfiguration requestConfiguration) {
        AIModelConfiguration modelConfiguration = effectiveModelConfiguration(configuration, requestConfiguration);
        AIModelSelection selection = modelConfiguration == null ? null : modelConfiguration.getModelSelection();
        if (selection == null) {
            throw new IllegalArgumentException("Missing model selection");
        }
        String providerName = selection.getProviderName();
        String modelName = selection.getModelName();
        if (PROVIDER_NAME_OPENROUTER.equalsIgnoreCase(providerName)) {
            OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(getOpenrouterServiceAddress(configuration))
                .apiKey(configuration.getOpenRouterKey())
                .modelName(modelName)
                .maxRetries(CHAT_MODEL_MAX_RETRIES)
                .parallelToolCalls(false);
            applyTemperature(builder::temperature, modelConfiguration.getTemperature());
            if (modelConfiguration.getThinkingEffort() != null) {
                builder.reasoningEffort(modelConfiguration.getThinkingEffort().toOpenAiValue());
            }
            return builder.build();
        }
        if (PROVIDER_NAME_GEMINI.equalsIgnoreCase(providerName)) {
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                .apiKey(configuration.getGeminiKey())
                .modelName(modelName)
                .maxRetries(CHAT_MODEL_MAX_RETRIES);
            String serviceAddress = configuration.getGeminiServiceAddress();
            if (serviceAddress != null && !serviceAddress.isEmpty()) {
                builder.baseUrl(serviceAddress);
            }
            applyTemperature(builder::temperature, modelConfiguration.getTemperature());
            applyGeminiThinking(builder, modelName, modelConfiguration.getThinkingEffort());
            return builder.build();
        }
        if (PROVIDER_NAME_OLLAMA.equalsIgnoreCase(providerName)) {
            OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(getOllamaServiceAddress(configuration))
                .modelName(modelName)
                .maxRetries(CHAT_MODEL_MAX_RETRIES);
            applyTemperature(builder::temperature, modelConfiguration.getTemperature());
            applyOllamaThinking(builder, modelConfiguration.getThinkingEffort());
            Map<String, String> requestHeaders = configuration.getOllamaRequestHeaders();
            if (!requestHeaders.isEmpty()) {
                builder.customHeaders(requestHeaders);
            }
            return builder.build();
        }
        throw new IllegalArgumentException("Unknown provider name: " + providerName);
    }

    private static AIModelConfiguration effectiveModelConfiguration(AIProviderConfiguration configuration,
                                                                    AIModelConfiguration requestConfiguration) {
        AIModelConfiguration defaultConfiguration = configuration.getDefaultModelConfiguration();
        return requestConfiguration == null
            ? defaultConfiguration
            : requestConfiguration.withFallback(defaultConfiguration);
    }

    private static void applyTemperature(java.util.function.Consumer<Double> temperatureConsumer,
                                         AiTemperature temperature) {
        if (temperature != null && temperature.isNumeric()) {
            temperatureConsumer.accept(temperature.getValue());
        }
    }

    private static void applyOllamaThinking(OllamaChatModel.OllamaChatModelBuilder builder,
                                            AiThinkingEffort thinkingEffort) {
        if (thinkingEffort != null) {
            builder.think(thinkingEffort != AiThinkingEffort.NONE);
        }
    }

    private static void applyGeminiThinking(GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder,
                                            String modelName,
                                            AiThinkingEffort thinkingEffort) {
        if (thinkingEffort == AiThinkingEffort.NONE) {
            return;
        }
        if (thinkingEffort != null) {
            GeminiThinkingConfig.GeminiThinkingLevel thinkingLevel =
                GeminiThinkingEffortMapper.toThinkingLevel(thinkingEffort);
            if (thinkingLevel != null) {
                GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                    .includeThoughts(true)
                    .thinkingLevel(thinkingLevel)
                    .build();
                builder.thinkingConfig(thinkingConfig)
                    .returnThinking(true)
                    .sendThinking(true);
            }
            return;
        }
        if (modelName != null && modelName.startsWith("gemini-3-")) {
            GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(true)
                .build();
            builder.thinkingConfig(thinkingConfig)
                .returnThinking(true)
                .sendThinking(true);
        }
    }

    private static String getOpenrouterServiceAddress(AIProviderConfiguration configuration) {
        String serviceAddress = configuration.getOpenrouterServiceAddress();
        if (serviceAddress == null || serviceAddress.isEmpty()) {
            return DEFAULT_OPENROUTER_SERVICE_ADDRESS;
        }
        return serviceAddress;
    }

    private static String getOllamaServiceAddress(AIProviderConfiguration configuration) {
        return configuration.getOllamaServiceAddress();
    }
}

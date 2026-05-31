package org.freeplane.plugin.ai.chat;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.freeplane.plugin.ai.code.AiCodeToolSet;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;

public class AIChatServiceFactory {

    private AIChatServiceFactory() {
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, null, null, null, null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, null, null, null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer, null, null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, null);
    }

    public static AIChatService createService(AIToolSet toolSet,
                                              Collection<?> toolObjects,
                                              ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                              String selectedModelOverride) {
        Objects.requireNonNull(toolSet, "toolSet");
        Collection<?> effectiveToolObjects = toolObjects == null
            ? Collections.<Object>singletonList(toolSet)
            : toolObjects;
        AIProviderConfiguration configuration = new AIProviderConfiguration(selectedModelOverride);
        ChatModel chatLanguageModel = AIChatModelFactory.createChatLanguageModel(configuration);
        AiCodeToolSet aiCodeToolSet = findAiCodeToolSet(effectiveToolObjects);
        if (toolAvailabilitySupplier == null) {
            return new AIChatService(chatLanguageModel, toolSet, effectiveToolObjects, aiCodeToolSet, chatMemory,
                chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer);
        }
        return new AIChatService(chatLanguageModel, toolSet, effectiveToolObjects, aiCodeToolSet, chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                              String selectedModelOverride) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, selectedModelOverride);
    }

    private static AiCodeToolSet findAiCodeToolSet(Collection<?> toolObjects) {
        if (toolObjects == null) {
            return null;
        }
        for (Object toolObject : toolObjects) {
            if (toolObject instanceof AiCodeToolSet) {
                return (AiCodeToolSet) toolObject;
            }
        }
        return null;
    }
}

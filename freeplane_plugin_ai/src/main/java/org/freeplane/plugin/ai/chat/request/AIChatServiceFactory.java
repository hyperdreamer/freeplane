package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingSettings;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

public class AIChatServiceFactory {

    private AIChatServiceFactory() {
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, null, null, null, (AIModelConfiguration) null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, null, null,
            (AIModelConfiguration) null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer, null,
            (AIModelConfiguration) null);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, (AIModelConfiguration) null);
    }

    public static AIChatService createService(AIToolSet toolSet,
                                              Collection<?> toolObjects,
                                              ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                              AIModelConfiguration modelConfigurationOverride) {
        return createService(toolSet, toolObjects, chatMemory, chatTokenUsageTracker, toolCallSummaryHandler,
            cancellationSupplier, tokenUsageConsumer, toolAvailabilitySupplier, modelConfigurationOverride,
            null, false, false);
    }

    public static AIChatService createService(AIToolSet toolSet,
                                              Collection<?> toolObjects,
                                              ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                              AIModelConfiguration modelConfigurationOverride,
                                              String systemMessage,
                                              boolean isSystemMessageExact,
                                              boolean hiddenRequest) {
        Objects.requireNonNull(toolSet, "toolSet");
        Collection<?> effectiveToolObjects = toolObjects == null
            ? Collections.<Object>singletonList(toolSet)
            : toolObjects;
        AIProviderConfiguration configuration = new AIProviderConfiguration();
        ChatModel chatLanguageModel = AIChatModelFactory.createChatLanguageModel(
            configuration,
            modelConfigurationOverride);
        AiCodeToolSet aiCodeToolSet = findAiCodeToolSet(effectiveToolObjects);
        if (toolAvailabilitySupplier == null) {
            return new AIChatService(chatLanguageModel, toolSet, effectiveToolObjects, aiCodeToolSet, chatMemory,
                chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
                new Supplier<ToolAvailabilityLevel>() {
                    @Override
                    public ToolAvailabilityLevel get() {
                        try {
                            return new ToolAvailabilityLevelSettings().getToolAvailability();
                        } catch (Exception ignored) {
                            return ToolAvailabilityLevel.EDITING;
                        }
                    }
                },
                () -> Boolean.valueOf(new FormulaEditingSettings().isEnabled()),
                null,
                systemMessage,
                isSystemMessageExact,
                hiddenRequest);
        }
        return new AIChatService(chatLanguageModel, toolSet, effectiveToolObjects, aiCodeToolSet, chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, () -> Boolean.valueOf(new FormulaEditingSettings().isEnabled()), null,
            systemMessage,
            isSystemMessageExact,
            hiddenRequest);
    }

    public static AIChatService createService(AIToolSet toolSet, ChatMemory chatMemory,
                                              ChatTokenUsageTracker chatTokenUsageTracker,
                                              ToolCallSummaryHandler toolCallSummaryHandler,
                                              Supplier<Boolean> cancellationSupplier,
                                              Consumer<TokenUsage> tokenUsageConsumer,
                                              Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                              AIModelConfiguration modelConfigurationOverride) {
        return createService(toolSet, Collections.<Object>singletonList(toolSet), chatMemory,
            chatTokenUsageTracker, toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
            toolAvailabilitySupplier, modelConfigurationOverride);
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

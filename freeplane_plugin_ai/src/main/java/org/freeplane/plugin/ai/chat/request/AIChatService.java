package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingAccess;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingSettings;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorFactory;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorRegistry;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public class AIChatService {
    private static final int MAXIMUM_SUMMARY_TEXT_LENGTH = 160;

    private AIAssistant assistant;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private final ChatModel chatLanguageModel;
    private final AIToolSet toolSet;
    private final AiCodeToolSet aiCodeToolSet;
    private final ChatMemory chatMemory;
    private final ChatTokenUsageTracker chatTokenUsageTracker;
    private final Supplier<Boolean> cancellationSupplier;
    private final Consumer<TokenUsage> tokenUsageConsumer;
    private final ToolExecutorRegistry toolExecutorRegistry;
    private final Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier;
    private final Supplier<Boolean> formulaEditingEnabledSupplier;
    private final Function<ToolAvailabilityLevel, AIAssistant> assistantFactory;
    private final String baseSystemMessage;
    private final boolean isSystemMessageExact;
    private final boolean hiddenRequest;
    private final SystemInstructionComposer systemInstructionComposer;
    private ToolAvailabilityLevel lastToolAvailability;

    public AIChatService(ChatModel chatLanguageModel, AIToolSet toolSet, ChatMemory chatMemory,
                         ChatTokenUsageTracker chatTokenUsageTracker, ToolCallSummaryHandler toolCallSummaryHandler,
                         Supplier<Boolean> cancellationSupplier, Consumer<TokenUsage> tokenUsageConsumer) {
        this(chatLanguageModel, toolSet, Collections.<Object>singletonList(toolSet), null, chatMemory,
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
            new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return Boolean.valueOf(new FormulaEditingSettings().isEnabled());
                }
            },
            null);
    }

    AIChatService(ChatModel chatLanguageModel,
                  AIToolSet toolSet,
                  Collection<?> toolObjects,
                  AiCodeToolSet aiCodeToolSet,
                  ChatMemory chatMemory,
                  ChatTokenUsageTracker chatTokenUsageTracker,
                  ToolCallSummaryHandler toolCallSummaryHandler,
                  Supplier<Boolean> cancellationSupplier,
                  Consumer<TokenUsage> tokenUsageConsumer) {
        this(chatLanguageModel, toolSet, toolObjects, aiCodeToolSet, chatMemory, chatTokenUsageTracker,
            toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer,
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
            new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return Boolean.valueOf(new FormulaEditingSettings().isEnabled());
                }
            },
            null);
    }

    AIChatService(ChatModel chatLanguageModel,
                  AIToolSet toolSet,
                  Collection<?> toolObjects,
                  AiCodeToolSet aiCodeToolSet,
                  ChatMemory chatMemory,
                  ChatTokenUsageTracker chatTokenUsageTracker,
                  ToolCallSummaryHandler toolCallSummaryHandler,
                  Supplier<Boolean> cancellationSupplier,
                  Consumer<TokenUsage> tokenUsageConsumer,
                  Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                  Supplier<Boolean> formulaEditingEnabledSupplier,
                  Function<ToolAvailabilityLevel, AIAssistant> assistantFactory) {
        this(chatLanguageModel, toolSet, toolObjects, aiCodeToolSet, chatMemory, chatTokenUsageTracker,
            toolCallSummaryHandler, cancellationSupplier, tokenUsageConsumer, toolAvailabilitySupplier,
            formulaEditingEnabledSupplier, assistantFactory, null, false, false);
    }

    AIChatService(ChatModel chatLanguageModel,
                  AIToolSet toolSet,
                  Collection<?> toolObjects,
                  AiCodeToolSet aiCodeToolSet,
                  ChatMemory chatMemory,
                  ChatTokenUsageTracker chatTokenUsageTracker,
                  ToolCallSummaryHandler toolCallSummaryHandler,
                  Supplier<Boolean> cancellationSupplier,
                  Consumer<TokenUsage> tokenUsageConsumer,
                  Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                  Supplier<Boolean> formulaEditingEnabledSupplier,
                  Function<ToolAvailabilityLevel, AIAssistant> assistantFactory,
                  String systemMessage,
                  boolean isSystemMessageExact,
                  boolean hiddenRequest) {
        Objects.requireNonNull(chatTokenUsageTracker, "chatTokenUsageTracker");
        this.chatLanguageModel = chatLanguageModel;
        this.toolSet = toolSet;
        this.aiCodeToolSet = aiCodeToolSet;
        this.chatMemory = chatMemory;
        this.chatTokenUsageTracker = chatTokenUsageTracker;
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolArgumentsErrorHandler = buildToolArgumentsErrorHandler();
        this.cancellationSupplier = cancellationSupplier;
        this.tokenUsageConsumer = tokenUsageConsumer;
        ToolExecutorFactory toolExecutorFactory = new ToolExecutorFactory(true, true, cancellationSupplier);
        this.toolExecutorRegistry = toolExecutorFactory.createRegistry(toolObjects);
        this.toolAvailabilitySupplier = Objects.requireNonNull(toolAvailabilitySupplier, "toolAvailabilitySupplier");
        this.formulaEditingEnabledSupplier = Objects.requireNonNull(
            formulaEditingEnabledSupplier,
            "formulaEditingEnabledSupplier");
        this.assistantFactory = assistantFactory != null
            ? assistantFactory
            : new Function<ToolAvailabilityLevel, AIAssistant>() {
                @Override
                public AIAssistant apply(ToolAvailabilityLevel toolAvailability) {
                    return buildAssistant(toolAvailability);
                }
            };
        this.baseSystemMessage = systemMessage == null ? null : systemMessage.trim();
        this.isSystemMessageExact = isSystemMessageExact && this.baseSystemMessage != null;
        this.hiddenRequest = hiddenRequest;
        this.systemInstructionComposer = new SystemInstructionComposer();
        this.lastToolAvailability = currentToolAvailability();
        this.assistant = this.assistantFactory.apply(lastToolAvailability);
    }

    public String chat(String message) {
        ToolAvailabilityLevel toolAvailability = currentToolAvailability();
        if (toolAvailability != lastToolAvailability) {
            assistant = assistantFactory.apply(toolAvailability);
            lastToolAvailability = toolAvailability;
        }
        return assistant.chat(message);
    }

    private AIAssistant buildAssistant(ToolAvailabilityLevel toolAvailability) {
        AiServices<AIAssistant> builder = AiServices.builder(AIAssistant.class)
            .toolArgumentsErrorHandler(toolArgumentsErrorHandler)
            .chatModel(chatLanguageModel)
            .systemMessageProvider(systemMessageProvider(toolAvailability))
            .registerListener(new AiServiceListener<AiServiceErrorEvent>() {

                @Override
                public Class<AiServiceErrorEvent> getEventClass() {
                    return AiServiceErrorEvent.class;
                }

                @Override
                public void onEvent(AiServiceErrorEvent event) {
                    event.error().printStackTrace();
                }

            })
            .registerListener(new AiServiceListener<AiServiceResponseReceivedEvent>() {

                @Override
                public Class<AiServiceResponseReceivedEvent> getEventClass() {
                    return AiServiceResponseReceivedEvent.class;
                }

                @Override
                public void onEvent(AiServiceResponseReceivedEvent event) {
                    if (tokenUsageConsumer != null) {
                        tokenUsageConsumer.accept(event.response().tokenUsage());
                    }
                }

            })
            .registerListener(new AiServiceListener<ToolExecutedEvent>() {

                @Override
                public Class<ToolExecutedEvent> getEventClass() {
                    return ToolExecutedEvent.class;
                }

                @Override
                public void onEvent(ToolExecutedEvent event) {
                    chatTokenUsageTracker.logToolExecuted(event);
                }
            });
        Collection<String> allowedToolNames = allowedToolNames(toolAvailability);
        if (!allowedToolNames.isEmpty()) {
            builder.tools(toolExecutorRegistry.filtered(allowedToolNames)
                .getExecutorsBySpecification());
        }
        if (chatMemory != null) {
            builder.chatMemory(chatMemory);
        }
        return builder.build();
    }

    private ToolAvailabilityLevel currentToolAvailability() {
        ToolAvailabilityLevel toolAvailability = toolAvailabilitySupplier.get();
        return toolAvailability == null
            ? ToolAvailabilityLevel.EDITING
            : toolAvailability;
    }

    Function<Object, String> systemMessageProvider(ToolAvailabilityLevel toolAvailability) {
        final ToolAvailabilityLevel normalizedAvailability = toolAvailability == null
            ? ToolAvailabilityLevel.EDITING
            : toolAvailability;
        return new Function<Object, String>() {
            @Override
            public String apply(Object input) {
                String baseMessage = baseSystemMessage == null && !isSystemMessageExact
                    ? MessageBuilder.configuredSystemMessage()
                    : baseSystemMessage;
                String codeHostGuidance = aiCodeToolSet == null ? null : aiCodeToolSet.systemMessageForChat(input);
                return systemInstructionComposer.compose(new SystemInstructionContext(
                    baseMessage,
                    isSystemMessageExact,
                    normalizedAvailability,
                    hiddenRequest ? RequestVisibility.HIDDEN : RequestVisibility.VISIBLE,
                    hasProfileInstruction(),
                    codeHostGuidance));
            }
        };
    }

    Collection<String> allowedToolNames(ToolAvailabilityLevel toolAvailability) {
        ToolAvailabilityLevel normalizedAvailability = toolAvailability == null
            ? ToolAvailabilityLevel.EDITING
            : toolAvailability;
        Set<String> toolNames = new LinkedHashSet<String>(normalizedAvailability.allowedToolNames());
        boolean formulaEditingAllowed = FormulaEditingAccess.isFormulaEditingAllowed(
            normalizedAvailability,
            formulaEditingEnabledSupplier.get().booleanValue());
        if (formulaEditingAllowed) {
            toolNames.addAll(FormulaEditingAccess.FORMULA_TOOL_NAMES);
        } else {
            toolNames.removeAll(FormulaEditingAccess.FORMULA_TOOL_NAMES);
        }
        if (aiCodeToolSet != null) {
            toolNames.addAll(aiCodeToolSet.authorizedToolNames());
        }
        return toolNames;
    }

    private boolean hasProfileInstruction() {
        return chatMemory instanceof AssistantProfileChatMemory
            && ((AssistantProfileChatMemory) chatMemory).hasProfileInstruction();
    }

    public interface AIAssistant {
        String chat(String message);
    }

    private ToolArgumentsErrorHandler buildToolArgumentsErrorHandler() {
        return (error, context) -> {
            String errorMessage = isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage();
            String toolName = context == null ? null : context.toolExecutionRequest().name();
            String arguments = context == null ? null : context.toolExecutionRequest().arguments();
            if (isNullOrBlank(toolName)) {
                toolName = "unknown tool";
            }
            publishToolArgumentsErrorSummary(toolName, arguments, errorMessage);
            return ToolErrorHandlerResult.text("Tool arguments error for " + toolName + ": " + errorMessage);
        };
    }

    private void publishToolArgumentsErrorSummary(String toolName, String arguments, String errorMessage) {
        if (toolCallSummaryHandler == null) {
            return;
        }
        LogUtils.info(buildToolArgumentsErrorLog(toolName, arguments, errorMessage));
        String summaryText = "tool arguments error: tool=" + sanitizeSummaryValue(toolName);
        String safeArguments = sanitizeSummaryValue(arguments);
        if (!safeArguments.isEmpty()) {
            summaryText = summaryText + ", arguments=" + safeArguments;
        }
        String safeErrorMessage = sanitizeSummaryValue(errorMessage);
        if (!safeErrorMessage.isEmpty()) {
            summaryText = summaryText + ", error=" + safeErrorMessage;
        }
        ToolCallSummary summary = new ToolCallSummary("toolArgumentsError", summaryText, true, ToolCaller.CHAT);
        toolCallSummaryHandler.handleToolCallSummary(summary);
    }

    private String buildToolArgumentsErrorLog(String toolName, String arguments, String errorMessage) {
        String safeToolName = toolName == null ? "unknown tool" : toolName;
        String safeArguments = arguments == null ? "" : arguments;
        String safeError = errorMessage == null ? "" : errorMessage;
        return "Tool arguments error: tool=" + safeToolName + ", arguments=" + safeArguments + ", error=" + safeError;
    }

    private String sanitizeSummaryValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ").trim();
        if (normalized.length() <= MAXIMUM_SUMMARY_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAXIMUM_SUMMARY_TEXT_LENGTH - 3) + "...";
    }
}

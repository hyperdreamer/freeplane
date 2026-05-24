package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class ChatPromptRunnerTest {

    @Test
    public void runPrompt_omitsSelectionContextForExplicitDisabledToolSelection() {
        ChatToolAvailabilitySettings settings = mock(ChatToolAvailabilitySettings.class);
        when(settings.getToolAvailability()).thenReturn(ChatToolAvailability.EDITING);
        PromptToolSelectionResolver promptToolSelectionResolver = new PromptToolSelectionResolver(settings);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AiPromptRequestComposer aiPromptRequestComposer =
            new AiPromptRequestComposer(availableMaps, mock(TextController.class));
        ChatMemory promptChatMemory = mock(ChatMemory.class);
        AIChatService promptService = mock(AIChatService.class);
        AtomicReference<String> seenPreparedMessage = new AtomicReference<String>();
        AtomicReference<ChatToolAvailability> seenShownChatOverride = new AtomicReference<ChatToolAvailability>();
        AtomicReference<ChatToolAvailability> seenServiceToolAvailability = new AtomicReference<ChatToolAvailability>();
        ChatPromptRunner uut = newPromptRunner(
            availableMaps,
            aiPromptRequestComposer,
            promptToolSelectionResolver,
            promptChatMemory,
            seenPreparedMessage,
            seenShownChatOverride);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.availableMaps(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.build()).thenReturn(mock(AIToolSet.class));
                 })) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_session_prefix")).thenReturn("Prompt: ");
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ChatToolAvailability>>any(),
                nullable(String.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ChatToolAvailability> toolAvailabilitySupplier = invocation.getArgument(6);
                    seenServiceToolAvailability.set(toolAvailabilitySupplier.get());
                    return promptService;
                });

            uut.runPrompt(new AiPrompt("Rewrite", "Rewrite the selected nodes.", true, "", "disabled"), null);
        }

        assertThat(seenPreparedMessage.get()).isEqualTo("Rewrite the selected nodes.");
        assertThat(seenShownChatOverride.get()).isEqualTo(ChatToolAvailability.DISABLED);
        assertThat(seenServiceToolAvailability.get()).isEqualTo(ChatToolAvailability.DISABLED);
    }

    @Test
    public void runPrompt_omitsSelectionContextForCurrentToolsWhenGlobalSettingIsDisabled() {
        ChatToolAvailabilitySettings settings = mock(ChatToolAvailabilitySettings.class);
        when(settings.getToolAvailability()).thenReturn(ChatToolAvailability.DISABLED);
        PromptToolSelectionResolver promptToolSelectionResolver = new PromptToolSelectionResolver(settings);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AiPromptRequestComposer aiPromptRequestComposer =
            new AiPromptRequestComposer(availableMaps, mock(TextController.class));
        ChatMemory promptChatMemory = mock(ChatMemory.class);
        AIChatService promptService = mock(AIChatService.class);
        AtomicReference<String> seenPreparedMessage = new AtomicReference<String>();
        AtomicReference<ChatToolAvailability> seenShownChatOverride = new AtomicReference<ChatToolAvailability>();
        AtomicReference<ChatToolAvailability> seenServiceToolAvailability = new AtomicReference<ChatToolAvailability>();
        ChatPromptRunner uut = newPromptRunner(
            availableMaps,
            aiPromptRequestComposer,
            promptToolSelectionResolver,
            promptChatMemory,
            seenPreparedMessage,
            seenShownChatOverride);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.availableMaps(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.build()).thenReturn(mock(AIToolSet.class));
                 })) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_session_prefix")).thenReturn("Prompt: ");
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ChatToolAvailability>>any(),
                nullable(String.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ChatToolAvailability> toolAvailabilitySupplier = invocation.getArgument(6);
                    seenServiceToolAvailability.set(toolAvailabilitySupplier.get());
                    return promptService;
                });

            uut.runPrompt(new AiPrompt("Rewrite", "Rewrite the selected nodes.", true), null);
        }

        assertThat(seenPreparedMessage.get()).isEqualTo("Rewrite the selected nodes.");
        assertThat(seenShownChatOverride.get()).isNull();
        assertThat(seenServiceToolAvailability.get()).isEqualTo(ChatToolAvailability.DISABLED);
    }

    private ChatPromptRunner newPromptRunner(AvailableMaps availableMaps,
                                             AiPromptRequestComposer aiPromptRequestComposer,
                                             PromptToolSelectionResolver promptToolSelectionResolver,
                                             ChatMemory promptChatMemory,
                                             AtomicReference<String> seenPreparedMessage,
                                             AtomicReference<ChatToolAvailability> seenShownChatOverride) {
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatRequestFlow chatRequestFlow = mock(ChatRequestFlow.class);
        ChatTokenUsageTracker chatTokenUsageTracker = new ChatTokenUsageTracker(totals -> {
        });
        return new ChatPromptRunner(
            null,
            null,
            null,
            () -> {
            },
            availableMaps,
            aiPromptRequestComposer,
            promptToolSelectionResolver,
            liveChatController,
            chatRequestFlow,
            chatTokenUsageTracker,
            () -> promptChatMemory,
            selectionValue -> null,
            (message, error) -> {
            },
            (memory, service, preparedMessage, promptDisplayName, selectedModelOverride, toolAvailabilityOverride) -> {
                seenPreparedMessage.set(preparedMessage);
                seenShownChatOverride.set(toolAvailabilityOverride);
            });
    }
}

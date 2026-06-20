package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.ui.AiPromptProgressDialog;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ChatPromptRunnerTest {

    @Test
    public void startShownPrompt_omitsSelectionContextForDisabledTools() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AiPromptRequestComposer aiPromptRequestComposer =
            new AiPromptRequestComposer(availableMaps, mock(TextController.class));
        ChatMemory promptChatMemory = mock(ChatMemory.class);
        AIChatService promptService = mock(AIChatService.class);
        AtomicReference<String> seenPreparedMessage = new AtomicReference<String>();
        AtomicReference<ToolAvailabilityLevel> seenServiceToolAvailability = new AtomicReference<ToolAvailabilityLevel>();
        ChatPromptRunner uut = newShownPromptRunner(
            availableMaps,
            aiPromptRequestComposer,
            promptChatMemory,
            seenPreparedMessage);

        try (MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = promptServiceBuilderConstruction()) {
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                nullable(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.nullable(Consumer.class),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(String.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier = invocation.getArgument(7);
                    seenServiceToolAvailability.set(toolAvailabilitySupplier.get());
                    return promptService;
                });

            boolean started = uut.startShownPrompt(
                "Rewrite the selected nodes.",
                null,
                ToolAvailabilityLevel.DISABLED,
                null,
                null);

            assertThat(started).isTrue();
        }

        assertThat(seenPreparedMessage.get()).isEqualTo("Rewrite the selected nodes.");
        assertThat(seenServiceToolAvailability.get()).isEqualTo(ToolAvailabilityLevel.DISABLED);
    }

    @Test
    public void startShownPrompt_includesSelectionContextWhenToolsEnabled() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AiPromptRequestComposer aiPromptRequestComposer =
            new AiPromptRequestComposer(availableMaps, mock(TextController.class));
        ChatMemory promptChatMemory = mock(ChatMemory.class);
        AIChatService promptService = mock(AIChatService.class);
        AtomicReference<String> seenPreparedMessage = new AtomicReference<String>();
        ChatPromptRunner uut = newShownPromptRunner(
            availableMaps,
            aiPromptRequestComposer,
            promptChatMemory,
            seenPreparedMessage);

        try (MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = promptServiceBuilderConstruction()) {
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                nullable(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.nullable(Consumer.class),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(String.class)))
                .thenReturn(promptService);

            boolean started = uut.startShownPrompt(
                "Rewrite the selected nodes.",
                null,
                ToolAvailabilityLevel.EDITING,
                null,
                null);

            assertThat(started).isTrue();
        }

        assertThat(seenPreparedMessage.get()).startsWith("Selected map and node identifiers:\n");
        assertThat(seenPreparedMessage.get()).endsWith("Rewrite the selected nodes.");
    }

    @Test
    public void submitHiddenRequest_doesNotOpenProgressDialogForHiddenMode() throws Exception {
        RecordingHiddenPromptRequestRunnerFactory hiddenRunnerFactory =
            new RecordingHiddenPromptRequestRunnerFactory();
        AiPromptProgressDialogFactory dialogFactory = mock(AiPromptProgressDialogFactory.class);
        ChatPromptRunner uut = newHiddenPromptRunner(hiddenRunnerFactory, dialogFactory);
        java.awt.Component owner = new javax.swing.JPanel();

        try (MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = promptServiceBuilderConstruction()) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                nullable(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.nullable(Consumer.class),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(String.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(mock(AIChatService.class));

            boolean started = uut.submitHiddenRequest(
                "Rewrite",
                "Rewrite the selected nodes.",
                null,
                ToolAvailabilityLevel.DISABLED,
                null,
                owner,
                false,
                mock(HiddenAiRequestObserverBridge.class));

            assertThat(started).isTrue();
            flushEdt();
        }

        verifyNoInteractions(dialogFactory);
    }

    @Test
    public void submitHiddenRequest_opensAndClosesProgressDialogOnCancellation() throws Exception {
        RecordingHiddenPromptRequestRunnerFactory hiddenRunnerFactory =
            new RecordingHiddenPromptRequestRunnerFactory();
        AiPromptProgressDialogFactory dialogFactory = mock(AiPromptProgressDialogFactory.class);
        AiPromptProgressDialog dialog = mock(AiPromptProgressDialog.class);
        java.awt.Component owner = new javax.swing.JPanel();
        when(dialogFactory.create(
            org.mockito.ArgumentMatchers.same(owner),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any())).thenReturn(dialog);
        ChatPromptRunner uut = newHiddenPromptRunner(hiddenRunnerFactory, dialogFactory);
        HiddenAiRequestObserverBridge observer = mock(HiddenAiRequestObserverBridge.class);

        try (MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = promptServiceBuilderConstruction()) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                nullable(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.nullable(Consumer.class),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(String.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(mock(AIChatService.class));

            boolean started = uut.submitHiddenRequest(
                "Rewrite",
                "Rewrite the selected nodes.",
                null,
                ToolAvailabilityLevel.DISABLED,
                null,
                owner,
                true,
                observer);

            assertThat(started).isTrue();
            flushEdt();
            hiddenRunnerFactory.lastRunner.cancelActiveRequest();
            flushEdt();
        }

        verify(dialog).showPrompt("Rewrite");
        verify(dialog).closeDialog();
        verify(observer).onCancelled();
    }

    private ChatPromptRunner newShownPromptRunner(AvailableMaps availableMaps,
                                                  AiPromptRequestComposer aiPromptRequestComposer,
                                                  ChatMemory promptChatMemory,
                                                  AtomicReference<String> seenPreparedMessage) {
        ChatRequestFlow shownRequestFlow = mock(ChatRequestFlow.class);
        ChatTokenUsageTracker shownRequestTokenUsageTracker = new ChatTokenUsageTracker(totals -> {
        });
        return new ChatPromptRunner(
            null,
            null,
            null,
            availableMaps,
            aiPromptRequestComposer,
            (sessionId, service, preparedMessage, requestFlow, requestTokenUsageTracker,
             requestCallbacks, requestedProfileMessage) -> seenPreparedMessage.set(preparedMessage),
            null,
            () -> ToolAvailabilityLevel.EDITING,
            () -> null,
            new HiddenPromptRequestRunnerFactory(),
            new AiPromptProgressDialogFactory(),
            promptChatMemory,
            (mapIdentifier, mapModel) -> {
            },
            shownRequestFlow,
            shownRequestTokenUsageTracker,
            LiveChatSessionId.create());
    }

    private ChatPromptRunner newHiddenPromptRunner(HiddenPromptRequestRunnerFactory hiddenRunnerFactory,
                                                   AiPromptProgressDialogFactory dialogFactory) {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        return new ChatPromptRunner(
            null,
            null,
            null,
            availableMaps,
            new AiPromptRequestComposer(availableMaps, mock(TextController.class)),
            (sessionId, service, preparedMessage, requestFlow, requestTokenUsageTracker,
             requestCallbacks, requestedProfileMessage) -> {
            },
            null,
            () -> ToolAvailabilityLevel.EDITING,
            null,
            hiddenRunnerFactory,
            dialogFactory,
            null,
            null,
            null,
            null,
            null);
    }

    private MockedConstruction<AIToolSetBuilder> promptServiceBuilderConstruction() {
        return mockConstruction(AIToolSetBuilder.class, (mock, context) -> {
            AIToolSet toolSet = mock(AIToolSet.class);
            when(mock.toolCallSummaryHandler(nullable(ToolCallSummaryHandler.class))).thenReturn(mock);
            when(mock.availableMaps(any())).thenReturn(mock);
            when(mock.mapAccessListener(nullable(AvailableMaps.MapAccessListener.class))).thenReturn(mock);
            when(mock.codeHostService(nullable(AiCodeHostService.class))).thenReturn(mock);
            when(mock.aiCodeOperationAuthorizer(nullable(AiCodeOperationAuthorizer.class))).thenReturn(mock);
            when(mock.build()).thenReturn(toolSet);
            when(mock.buildToolObjects()).thenReturn(Collections.<Object>singletonList(toolSet));
        });
    }

    private void flushEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static class RecordingHiddenPromptRequestRunnerFactory extends HiddenPromptRequestRunnerFactory {
        private TestHiddenPromptRequestRunner lastRunner;

        @Override
        public HiddenPromptRequestRunner create(HiddenPromptRequestRunner.Callbacks callbacks) {
            lastRunner = new TestHiddenPromptRequestRunner(callbacks);
            return lastRunner;
        }
    }

    private static class TestHiddenPromptRequestRunner extends HiddenPromptRequestRunner {
        private final Callbacks callbacks;
        private boolean requestActive;
        private boolean cancelled;
        private String promptName;

        private TestHiddenPromptRequestRunner(Callbacks callbacks) {
            super(null);
            this.callbacks = callbacks;
        }

        @Override
        public boolean isRequestActive() {
            return requestActive;
        }

        @Override
        public Supplier<Boolean> cancellationSupplier() {
            return () -> cancelled;
        }

        @Override
        public void cancelActiveRequest() {
            if (!requestActive) {
                return;
            }
            cancelled = true;
            requestActive = false;
            callbacks.onRequestCancelled(promptName);
            callbacks.onRequestFinished(promptName);
        }

        @Override
        public void submit(String promptName, AIChatService chatService, String userMessage) {
            this.promptName = promptName;
            requestActive = true;
            callbacks.onRequestStarted(promptName);
        }
    }
}

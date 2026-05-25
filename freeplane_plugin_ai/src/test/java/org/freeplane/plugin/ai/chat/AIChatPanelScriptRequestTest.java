package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptStore;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class AIChatPanelScriptRequestTest {

    @Test
    public void shownRequestCallbackRunsAfterAssistantMessageAppend() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatPromptRunner.VisiblePromptRequestCallbacks requestCallbacks =
            mock(ChatPromptRunner.VisiblePromptRequestCallbacks.class);
        CapturingChatRequestFlowFactory chatRequestFlowFactory = new CapturingChatRequestFlowFactory();
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);

        createVisibleRequestFlow(
            harness.panel,
            harness.sessionId,
            new ChatTokenUsageTracker(totals -> {
            }),
            requestCallbacks);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            chatRequestFlowFactory.callbacks.onAssistantResponse("Answer");
        }

        InOrder inOrder = inOrder(harness.chatOutputView, requestCallbacks);
        inOrder.verify(harness.chatOutputView).appendAssistantMessage("Answer");
        inOrder.verify(requestCallbacks).onResponseAppended("Answer");
    }

    @Test
    public void addToChatWithExistingVisibleChatReusesSessionAndAppliesExplicitOverridesBeforeSending() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSelectedModel = new AtomicReference<String>();
        AtomicReference<ChatToolAvailability> seenToolAvailability = new AtomicReference<ChatToolAvailability>();
        LiveChatSessionId originalSessionId = harness.sessionId;
        String explicitSelection = "openrouter|openai/gpt-4.1-mini";
        AiRequest request = new AiRequest(
            "Prompt",
            AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini"),
            AiToolAvailability.DISABLED,
            AiRequestMode.ADD_TO_CHAT,
            Duration.ofSeconds(10));
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
        });

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.availableMaps(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.build()).thenReturn(mock(AIToolSet.class));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
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
                    seenToolAvailability.set(toolAvailabilitySupplier == null ? null : toolAvailabilitySupplier.get());
                    seenSelectedModel.set(invocation.getArgument(7));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(request, handle);

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(originalSessionId);
        assertThat(harness.liveChatController.currentSessionSelectedModelOverride()).isEqualTo(explicitSelection);
        assertThat(harness.liveChatController.currentSessionToolAvailabilityOverride())
            .isEqualTo(ChatToolAvailability.DISABLED);
        assertThat(seenSelectedModel.get()).isEqualTo(explicitSelection);
        assertThat(seenToolAvailability.get()).isEqualTo(ChatToolAvailability.DISABLED);
        verify(harness.chatOutputView).appendUserMessage("Prompt");
    }

    @Test
    public void addToChatWithNoVisibleChatCreatesNewSession() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        LiveChatSessionId originalSessionId = harness.sessionId;
        AiRequest request = new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.ADD_TO_CHAT,
            Duration.ofSeconds(10));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.availableMaps(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.build()).thenReturn(mock(AIToolSet.class));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(new javax.swing.JPanel());
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ChatToolAvailability>>any(),
                nullable(String.class)))
                .thenReturn(mock(AIChatService.class));

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isNotEqualTo(originalSessionId);
    }

    @Test
    public void addToChatWithCurrentSelectionsResolvesTargetSessionValuesAtDispatchStart() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.setCurrentSessionSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
        harness.liveChatController.setCurrentSessionToolAvailabilityOverride(ChatToolAvailability.READING);
        when(harness.chatToolAvailabilitySettings.getToolAvailability()).thenReturn(ChatToolAvailability.EDITING);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSelectedModel = new AtomicReference<String>();
        AtomicReference<ChatToolAvailability> seenToolAvailability = new AtomicReference<ChatToolAvailability>();
        AiRequest request = new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.ADD_TO_CHAT,
            Duration.ofSeconds(10));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.availableMaps(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.build()).thenReturn(mock(AIToolSet.class));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
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
                    seenToolAvailability.set(toolAvailabilitySupplier == null ? null : toolAvailabilitySupplier.get());
                    seenSelectedModel.set(invocation.getArgument(7));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(seenSelectedModel.get()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(seenToolAvailability.get()).isEqualTo(ChatToolAvailability.READING);
    }

    @Test
    public void addToChatConfigurationFailureCompletesHandleBeforeRequestStart() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        AiRequestConfigurationResolver.Issue issue = new AiRequestConfigurationResolver.Issue(
            AiRequestStatus.CONFIGURATION_ERROR,
            "Missing AI model selection.");
        when(harness.aiRequestConfigurationResolver.resolve(any())).thenReturn(issue);
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
            seenStatus.set(result.getStatus());
            callbackLatch.countDown();
        });

        ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
            new AiRequest(
                "Prompt",
                AiModelSelection.current(),
                AiToolAvailability.CURRENT,
                AiRequestMode.ADD_TO_CHAT,
                Duration.ofSeconds(10)),
            handle);

        assertThat(started).isNull();
        assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CONFIGURATION_ERROR);
        assertThat(handle.isDone()).isTrue();
    }

    @Test
    public void runPromptShownStartsAfterHiddenPromptLaunch() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        ChatPromptRunnerFactory chatPromptRunnerFactory = mock(ChatPromptRunnerFactory.class);
        ChatPromptRunner hiddenRunner = mock(ChatPromptRunner.class);
        ChatPromptRunner shownRunner = mock(ChatPromptRunner.class);
        when(chatPromptRunnerFactory.createHidden()).thenReturn(hiddenRunner);
        when(chatPromptRunnerFactory.createShown(any(), any(), any(), any(), any())).thenReturn(shownRunner);
        when(hiddenRunner.submitHiddenRequest(
            "Hidden prompt",
            "Hidden body",
            null,
            ChatToolAvailability.EDITING,
            null,
            null,
            true,
            null)).thenReturn(true);
        when(shownRunner.startShownPrompt("Prompt body", null, ChatToolAvailability.EDITING, null, null))
            .thenReturn(true);
        setField(harness.panel, "chatPromptRunnerFactory", chatPromptRunnerFactory);

        try (MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            textUtils.when(() -> TextUtils.getText("ai_prompt_session_prefix")).thenReturn("Prompt: ");
            textUtils.when(() -> TextUtils.getText("ai_prompt_untitled")).thenReturn("Untitled");

            harness.panel.runPrompt(new AiPrompt("Hidden prompt", "Hidden body", false));
            harness.panel.runPrompt(new AiPrompt("Shown prompt", "Prompt body", true));
        }

        verify(hiddenRunner).submitHiddenRequest(
            "Hidden prompt",
            "Hidden body",
            null,
            ChatToolAvailability.EDITING,
            null,
            null,
            true,
            null);
        verify(chatPromptRunnerFactory).createShown(any(), any(), eq(requestFlow), any(), any());
        verify(shownRunner).startShownPrompt("Prompt body", null, ChatToolAvailability.EDITING, null, null);
    }

    @Test
    public void runPromptHiddenStartsWhileVisibleRequestIsTracked() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        HashMap<LiveChatSessionId, ChatRequestFlow> activeVisibleRequestFlows =
            new HashMap<LiveChatSessionId, ChatRequestFlow>();
        activeVisibleRequestFlows.put(harness.sessionId, mock(ChatRequestFlow.class));
        setField(harness.panel, "activeVisibleRequestFlows", activeVisibleRequestFlows);
        ChatPromptRunnerFactory chatPromptRunnerFactory = mock(ChatPromptRunnerFactory.class);
        ChatPromptRunner chatPromptRunner = mock(ChatPromptRunner.class);
        when(chatPromptRunnerFactory.createHidden()).thenReturn(chatPromptRunner);
        when(chatPromptRunner.submitHiddenRequest(
            "Hidden prompt",
            "Prompt body",
            null,
            ChatToolAvailability.EDITING,
            null,
            null,
            true,
            null)).thenReturn(true);
        setField(harness.panel, "chatPromptRunnerFactory", chatPromptRunnerFactory);

        harness.panel.runPrompt(new AiPrompt("Hidden prompt", "Prompt body", false));

        verify(chatPromptRunnerFactory).createHidden();
        verify(chatPromptRunner).submitHiddenRequest(
            "Hidden prompt",
            "Prompt body",
            null,
            ChatToolAvailability.EDITING,
            null,
            null,
            true,
            null);
    }

    @Test
    public void runPromptShownCreatesFreshRequestScopedRuntimeObjectsAcrossLaunches() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatRequestFlow firstFlow = mock(ChatRequestFlow.class);
        ChatRequestFlow secondFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(firstFlow, secondFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        ChatPromptRunnerFactory chatPromptRunnerFactory = mock(ChatPromptRunnerFactory.class);
        ChatPromptRunner firstRunner = mock(ChatPromptRunner.class);
        ChatPromptRunner secondRunner = mock(ChatPromptRunner.class);
        when(chatPromptRunnerFactory.createShown(any(), any(), eq(firstFlow), any(), any())).thenReturn(firstRunner);
        when(chatPromptRunnerFactory.createShown(any(), any(), eq(secondFlow), any(), any())).thenReturn(secondRunner);
        when(firstRunner.startShownPrompt("Prompt one", null, ChatToolAvailability.EDITING, null, null))
            .thenReturn(true);
        when(secondRunner.startShownPrompt("Prompt two", null, ChatToolAvailability.EDITING, null, null))
            .thenReturn(true);
        setField(harness.panel, "chatPromptRunnerFactory", chatPromptRunnerFactory);

        try (MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            textUtils.when(() -> TextUtils.getText("ai_prompt_session_prefix")).thenReturn("Prompt: ");
            textUtils.when(() -> TextUtils.getText("ai_prompt_untitled")).thenReturn("Untitled");

            harness.panel.runPrompt(new AiPrompt("Prompt one", "Prompt one", true));
            harness.panel.runPrompt(new AiPrompt("Prompt two", "Prompt two", true));
        }

        InOrder inOrder = inOrder(chatRequestFlowFactory, chatPromptRunnerFactory, firstRunner, secondRunner);
        inOrder.verify(chatRequestFlowFactory).create(any(), any());
        inOrder.verify(chatPromptRunnerFactory).createShown(any(), any(), eq(firstFlow), any(), any());
        inOrder.verify(firstRunner).startShownPrompt("Prompt one", null, ChatToolAvailability.EDITING, null, null);
        inOrder.verify(chatRequestFlowFactory).create(any(), any());
        inOrder.verify(chatPromptRunnerFactory).createShown(any(), any(), eq(secondFlow), any(), any());
        inOrder.verify(secondRunner).startShownPrompt("Prompt two", null, ChatToolAvailability.EDITING, null, null);
    }

    private ChatRequestFlow createVisibleRequestFlow(AIChatPanel panel,
                                                     LiveChatSessionId sessionId,
                                                     ChatTokenUsageTracker requestTracker,
                                                     ChatPromptRunner.VisiblePromptRequestCallbacks requestCallbacks)
        throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod(
            "createVisibleRequestFlow",
            LiveChatSessionId.class,
            ChatTokenUsageTracker.class,
            ChatPromptRunner.VisiblePromptRequestCallbacks.class);
        method.setAccessible(true);
        return (ChatRequestFlow) method.invoke(panel, sessionId, requestTracker, requestCallbacks);
    }

    private PanelHarness newPanelHarness(boolean panelSelected) throws Exception {
        AIChatPanel panel = mock(AIChatPanel.class, withSettings().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TextController textController = mock(TextController.class);
        ChatTranscriptStore transcriptStore = mock(ChatTranscriptStore.class);
        ChatMemorySettings chatMemorySettings = mock(ChatMemorySettings.class);
        when(chatMemorySettings.getMaximumTokenCount()).thenReturn(500);
        LiveChatController liveChatController = new LiveChatController(
            panel,
            availableMaps,
            textController,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            (chatMemory, fromTranscriptRestore) -> {
            },
            () -> null,
            transcriptStore,
            chatMemorySettings);
        liveChatController.initialize(AssistantProfileChatMemory.withMaxTokens(500));
        LiveChatSessionId sessionId = liveChatController.currentSessionId();

        ChatOutputView chatOutputView = mock(ChatOutputView.class);
        ChatInputControls chatInputControls = mock(ChatInputControls.class);
        ChatDisplaySettings chatDisplaySettings = mock(ChatDisplaySettings.class);
        when(chatDisplaySettings.isToolCallHistoryVisible()).thenReturn(true);
        ChatToolAvailabilitySettings chatToolAvailabilitySettings = mock(ChatToolAvailabilitySettings.class);
        when(chatToolAvailabilitySettings.getToolAvailability()).thenReturn(ChatToolAvailability.EDITING);
        ChatRequestFlowFactory chatRequestFlowFactory = new ChatRequestFlowFactory();
        AiRequestConfigurationResolver aiRequestConfigurationResolver = mock(AiRequestConfigurationResolver.class);
        when(aiRequestConfigurationResolver.resolve(any())).thenReturn(null);
        org.freeplane.plugin.ai.model.AIProviderConfiguration configuration =
            mock(org.freeplane.plugin.ai.model.AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("key");
        when(configuration.hasOllamaServiceAddress()).thenReturn(false);
        ChatTokenUsageTracker chatTokenUsageTracker = new ChatTokenUsageTracker(totals -> {
        });

        setField(panel, "liveChatController", liveChatController);
        setField(panel, "chatOutputView", chatOutputView);
        setField(panel, "chatInputControls", chatInputControls);
        setField(panel, "chatDisplaySettings", chatDisplaySettings);
        setField(panel, "chatToolAvailabilitySettings", chatToolAvailabilitySettings);
        setField(panel, "promptToolSelectionResolver", new PromptToolSelectionResolver(chatToolAvailabilitySettings));
        setField(panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        setField(panel, "visibleAiRequestCallbacksFactory", new VisibleAiRequestCallbacksFactory());
        setField(panel, "chatToolAvailabilityMenu", mock(ChatToolAvailabilityMenu.class));
        setField(panel, "modelSelectionController", mock(ChatModelSelector.class));
        setField(panel, "aiRequestConfigurationResolver", aiRequestConfigurationResolver);
        setField(panel, "configuration", configuration);
        setField(panel, "aiPromptRequestComposer", new AiPromptRequestComposer(availableMaps, textController));
        setField(panel, "aiSelectionOverrideResolver", mock(AiSelectionOverrideResolver.class));
        setField(panel, "availableMaps", availableMaps);
        setField(panel, "assistantProfileSelectionSync", mock(AssistantProfileSelectionSync.class));
        setField(panel, "inputArea", new JTextArea());
        setField(panel, "undoButton", mock(JButton.class));
        setField(panel, "redoButton", mock(JButton.class));
        setField(panel, "chatTokenUsageTracker", chatTokenUsageTracker);
        setField(panel, "chatMemory", AssistantProfileChatMemory.withMaxTokens(500));
        setField(panel, "activeVisibleRequestFlows", new HashMap<LiveChatSessionId, ChatRequestFlow>());
        setField(panel, "activeVisibleRequestTrackers", new HashMap<LiveChatSessionId, ChatTokenUsageTracker>());
        setField(panel, "currentSessionUsesAssistantProfile", false);

        return new PanelHarness(
            panel,
            liveChatController,
            sessionId,
            chatOutputView,
            chatToolAvailabilitySettings,
            aiRequestConfigurationResolver);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AIChatPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturingChatRequestFlowFactory extends ChatRequestFlowFactory {
        private ChatRequestFlow.RequestCallbacks callbacks;

        @Override
        ChatRequestFlow create(ChatRequestFlow.RequestCallbacks callbacks,
                               ChatTokenUsageTracker tokenUsageTracker) {
            this.callbacks = callbacks;
            return mock(ChatRequestFlow.class);
        }
    }

    private static class PanelHarness {
        private final AIChatPanel panel;
        private final LiveChatController liveChatController;
        private final LiveChatSessionId sessionId;
        private final ChatOutputView chatOutputView;
        private final ChatToolAvailabilitySettings chatToolAvailabilitySettings;
        private final AiRequestConfigurationResolver aiRequestConfigurationResolver;

        private PanelHarness(AIChatPanel panel,
                             LiveChatController liveChatController,
                             LiveChatSessionId sessionId,
                             ChatOutputView chatOutputView,
                             ChatToolAvailabilitySettings chatToolAvailabilitySettings,
                             AiRequestConfigurationResolver aiRequestConfigurationResolver) {
            this.panel = panel;
            this.liveChatController = liveChatController;
            this.sessionId = sessionId;
            this.chatOutputView = chatOutputView;
            this.chatToolAvailabilitySettings = chatToolAvailabilitySettings;
            this.aiRequestConfigurationResolver = aiRequestConfigurationResolver;
        }
    }
}

package org.freeplane.plugin.ai.chat.ui;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.text.html.HTMLEditorKit;
import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptStore;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatMemorySettings;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.profile.AssistantProfileSelectionSync;
import org.freeplane.plugin.ai.chat.request.AIChatService;
import org.freeplane.plugin.ai.chat.request.AIChatServiceFactory;
import org.freeplane.plugin.ai.chat.request.AiRequestConfigurationResolver;
import org.freeplane.plugin.ai.chat.request.AiRequestHandleImpl;
import org.freeplane.plugin.ai.chat.request.AiSelectionOverrideResolver;
import org.freeplane.plugin.ai.chat.request.ChatPromptRunner;
import org.freeplane.plugin.ai.chat.request.ChatPromptRunnerFactory;
import org.freeplane.plugin.ai.chat.request.ChatRequestFlow;
import org.freeplane.plugin.ai.chat.request.ChatRequestFlowFactory;
import org.freeplane.plugin.ai.chat.request.PromptToolSelectionResolver;
import org.freeplane.plugin.ai.chat.request.ResolvedAiRequest;
import org.freeplane.plugin.ai.chat.request.SystemInstructionComposer;
import org.freeplane.plugin.ai.chat.request.VisibleAiRequestCallbacksFactory;
import org.freeplane.plugin.ai.chat.session.LiveChatController;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;
import org.freeplane.plugin.ai.code.RoutingAiCodeHostService;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptActionRegistry;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class AIChatPanelScriptRequestTest {

    @Test
    public void showAndFocusInputSelectsTabAndRequestsInputFocus() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        FocusAwareTextArea inputArea = new FocusAwareTextArea();
        setField(harness.panel, "inputArea", inputArea);
        JTabbedPane tabs = mock(JTabbedPane.class);

        try (MockedStatic<UITools> uiTools = mockStatic(UITools.class)) {
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);

            harness.panel.showAndFocusInput();
            flushEdt();
        }

        verify(tabs).setSelectedComponent(harness.panel);
        assertThat(inputArea.focusRequested).isTrue();
    }

    @Test
    public void sendMessageStopsBeforeRequestConstructionForUnavailableModel() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatModelSelector modelSelector = (ChatModelSelector) getField(
            harness.panel, "modelSelectionController");
        when(modelSelector.hasAvailableSelectedModel()).thenReturn(false);
        JTextArea inputArea = (JTextArea) getField(harness.panel, "inputArea");
        inputArea.setText("message");
        Method sendMessage = AIChatPanel.class.getDeclaredMethod("sendMessage");
        sendMessage.setAccessible(true);

        sendMessage.invoke(harness.panel);

        verify(modelSelector).hasAvailableSelectedModel();
        assertThat(inputArea.getText()).isEqualTo("message");
    }

    @Test
    public void mcpToolSummaryUsesCurrentVisibleRequestFlowWhenPresent() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        HashMap<LiveChatSessionId, ChatRequestFlow> activeVisibleRequestFlows = new HashMap<LiveChatSessionId, ChatRequestFlow>();
        activeVisibleRequestFlows.put(harness.sessionId, requestFlow);
        setField(harness.panel, "activeVisibleRequestFlows", activeVisibleRequestFlows);
        ToolCallSummary summary = new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP);

        harness.panel.toolCallSummaryHandler().handleToolCallSummary(summary);
        flushEdt();

        verify(requestFlow).onToolCallSummary(summary);
        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(harness.sessionId);
    }

    @Test
    public void mcpToolSummaryUsesCurrentSessionWhenCurrentSessionExists() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        LiveChatSessionId originalSessionId = harness.sessionId;
        ToolCallSummary summary = new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP);

        harness.panel.toolCallSummaryHandler().handleToolCallSummary(summary);
        flushEdt();

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(originalSessionId);
        assertThat(sessionToolSummaryTexts(harness.liveChatController, originalSessionId)).contains("mcp summary");
    }

    @Test
    public void mcpToolSummaryStartsNewChatWhenCurrentSessionDoesNotExist() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        LiveChatSessionId newSessionId = LiveChatSessionId.create();
        AssistantProfileChatMemory newSessionMemory = AssistantProfileChatMemory.withMaxTokens(500);
        LiveChatController liveChatController = mock(LiveChatController.class);
        when(liveChatController.currentSessionId()).thenReturn(null);
        when(liveChatController.startNewChat()).thenReturn(newSessionId);
        when(liveChatController.chatMemory(newSessionId)).thenReturn(newSessionMemory);
        when(liveChatController.isCurrentSession(newSessionId)).thenReturn(true);
        org.mockito.Mockito.doNothing().when(harness.panel).showAndFocusInput();
        setField(harness.panel, "liveChatController", liveChatController);

        harness.panel.toolCallSummaryHandler().handleToolCallSummary(
            new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP));
        flushEdt();

        verify(liveChatController).startNewChat();
        assertThat(toolSummaryTexts(newSessionMemory)).contains("mcp summary");
    }

    @Test
    public void appendToolSummaryToCurrentSessionAppendsMcpSummaryIncrementally() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        setField(harness.panel, "chatMemory", harness.liveChatController.chatMemory(harness.sessionId));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getOptionalText("ai_chat_token_counter.input")).thenReturn("input");
            textUtils.when(() -> TextUtils.getOptionalText("ai_chat_token_counter.output")).thenReturn("output");

            appendToolSummaryToSession(
                harness.panel,
                harness.sessionId,
                new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP));
        }

        assertThat(sessionToolSummaryTexts(harness.liveChatController, harness.sessionId)).contains("mcp summary");
        verify(harness.chatOutputView).appendHistoryEntry(any(ChatMemoryRenderEntry.class));
        verify(harness.chatOutputView, org.mockito.Mockito.never()).rebuildHistory(any());
    }

    @Test
    public void chatOwnedToolSummaryAppendsIncrementallyWhenSharedRebuildCounterIsUnchanged() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        setField(harness.panel, "chatMemory", harness.liveChatController.chatMemory(harness.sessionId));
        ChatTokenUsageTracker requestTracker = new ChatTokenUsageTracker(totals -> {
        });
        ChatRequestFlow requestFlow = createVisibleRequestFlow(harness.panel, harness.sessionId, requestTracker, null);

        requestFlow.updateChatMemory(harness.liveChatController.chatMemory(harness.sessionId));
        requestFlow.beginRequest("Prompt");
        requestFlow.onToolCallSummary(new ToolCallSummary("searchNodes", "chat summary", false, ToolCaller.CHAT));
        flushEdt();

        assertThat(sessionToolSummaryTexts(harness.liveChatController, harness.sessionId)).contains("chat summary");
        verify(harness.chatOutputView).appendHistoryEntry(any(ChatMemoryRenderEntry.class));
        verify(harness.chatOutputView, org.mockito.Mockito.never()).rebuildHistory(any());
    }

    @Test
    public void chatOwnedIncrementalSummaryUpdateMatchesLaterFullRebuildOfSameSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        AssistantProfileChatMemory memory = (AssistantProfileChatMemory) harness.liveChatController.chatMemory(harness.sessionId);
        memory.add(UserMessage.from("u1"));
        memory.add(AiMessage.from("a1"));
        setField(harness.panel, "chatMemory", memory);

        JEditorPane messagePane = new JEditorPane();
        messagePane.setContentType("text/html");
        HTMLEditorKit editorKit = (HTMLEditorKit) messagePane.getEditorKit();
        ChatMessageHistory messageHistory = new ChatMessageHistory(messagePane, editorKit);
        ChatOutputView realChatOutputView = new ChatOutputView(messageHistory, harness.liveChatController, new JLabel());
        setField(harness.panel, "chatOutputView", realChatOutputView);

        rebuildHistoryFromMemory(harness.panel);

        ChatTokenUsageTracker requestTracker = new ChatTokenUsageTracker(totals -> {
        });
        ChatRequestFlow requestFlow = createVisibleRequestFlow(harness.panel, harness.sessionId, requestTracker, null);
        requestFlow.updateChatMemory(memory);
        requestFlow.beginRequest("Prompt");
        requestFlow.onToolCallSummary(new ToolCallSummary("searchNodes", "chat summary", false, ToolCaller.CHAT));
        flushEdt();

        String incrementalHtml = messagePane.getText();

        rebuildHistoryFromMemory(harness.panel);
        String rebuiltHtml = messagePane.getText();

        assertThat(incrementalHtml).contains("u1");
        assertThat(incrementalHtml).contains("a1");
        assertThat(incrementalHtml).contains("chat summary");
        assertThat(incrementalHtml).isEqualTo(rebuiltHtml);
    }

    @Test
    public void chatOwnedToolSummaryRebuildsVisibleHistoryWhenSharedRebuildCounterChanges() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        setField(harness.panel, "chatMemory", harness.liveChatController.chatMemory(harness.sessionId));
        ChatTokenUsageTracker requestTracker = new ChatTokenUsageTracker(totals -> {
        });
        ChatRequestFlow requestFlow = createVisibleRequestFlow(harness.panel, harness.sessionId, requestTracker, null);

        requestFlow.updateChatMemory(harness.liveChatController.chatMemory(harness.sessionId));
        requestFlow.beginRequest("Prompt");
        rebuildHistoryFromMemory(harness.panel);
        org.mockito.Mockito.clearInvocations(harness.chatOutputView);

        requestFlow.onToolCallSummary(new ToolCallSummary("searchNodes", "chat summary", false, ToolCaller.CHAT));
        flushEdt();

        assertThat(sessionToolSummaryTexts(harness.liveChatController, harness.sessionId)).contains("chat summary");
        verify(harness.chatOutputView).rebuildHistory(any());
        verify(harness.chatOutputView, org.mockito.Mockito.never()).appendHistoryEntry(any(ChatMemoryRenderEntry.class));
    }

    @Test
    public void mcpToolSummaryDoesNotOpenChatWhenToolCallHistoryIsHidden() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        when(harness.chatDisplaySettings.isToolCallHistoryVisible()).thenReturn(false);
        LiveChatSessionId originalSessionId = harness.sessionId;

        harness.panel.toolCallSummaryHandler().handleToolCallSummary(
            new ToolCallSummary("searchNodes", "hidden summary", false, ToolCaller.MCP));
        flushEdt();

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(originalSessionId);
    }

    @Test
    public void refreshChatMemoryMaximumTokenCountRebuildsVisibleHistoryFromCurrentMemory() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        String firstQuestion = repeatedWords("first", 24);
        String firstAnswer = repeatedWords("answer1", 24);
        String secondQuestion = repeatedWords("second", 24);
        String secondAnswer = repeatedWords("answer2", 24);
        String thirdQuestion = repeatedWords("third", 24);
        String thirdAnswer = repeatedWords("answer3", 24);
        int allTurnTokens = estimateTokens(
            UserMessage.from(firstQuestion),
            AiMessage.from(firstAnswer),
            UserMessage.from(secondQuestion),
            AiMessage.from(secondAnswer),
            UserMessage.from(thirdQuestion),
            AiMessage.from(thirdAnswer));
        int visibleAfterReductionTokens = estimateTokens(
            UserMessage.from(secondQuestion),
            AiMessage.from(secondAnswer),
            UserMessage.from(thirdQuestion),
            AiMessage.from(thirdAnswer));
        AtomicInteger maxTokens = new AtomicInteger(allTurnTokens);
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.builder()
            .dynamicMaxTokens(ignored -> maxTokens.get())
            .tokenEstimatorModelNameProvider(() -> "gpt-4o-mini")
            .build();
        memory.add(UserMessage.from(firstQuestion));
        memory.add(AiMessage.from(firstAnswer));
        memory.add(UserMessage.from(secondQuestion));
        memory.add(AiMessage.from(secondAnswer));
        memory.add(UserMessage.from(thirdQuestion));
        memory.add(AiMessage.from(thirdAnswer));
        setField(harness.panel, "chatMemory", memory);

        maxTokens.set(visibleAfterReductionTokens);
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getOptionalText("ai_chat_token_counter.input")).thenReturn("input");
            textUtils.when(() -> TextUtils.getOptionalText("ai_chat_token_counter.output")).thenReturn("output");

            refreshChatMemoryMaximumTokenCount(harness.panel);
        }

        assertThat(memory.messages())
            .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
            .contains(secondQuestion, thirdQuestion)
            .doesNotContain(firstQuestion);
        verify(harness.chatOutputView).rebuildHistory(any());
    }

    @Test
    public void switchToSessionActivatesChosenSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        LiveChatSessionId originalSessionId = harness.sessionId;
        LiveChatSessionId anotherSessionId = harness.liveChatController.startNewChat();

        harness.panel.switchToSession(originalSessionId);

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(originalSessionId);
        assertThat(harness.liveChatController.currentSessionId()).isNotEqualTo(anotherSessionId);
    }

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
    public void emptyChatWithPreviewEnabledSuppressesInitialHistorySystemMessage() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        setField(harness.panel, "chatMemory", harness.liveChatController.chatMemory(harness.sessionId));
        setField(harness.panel, "showInstructionMessages", true);
        setField(harness.panel, "showNextRequestInstructionPreview", true);
        setField(harness.panel, "currentSessionUsesAssistantProfile", true);

        rebuildHistoryFromMemory(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<ChatMemoryRenderEntry>> entriesCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(harness.chatOutputView).rebuildHistory(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).isEmpty();
    }

    @Test
    public void emptyChatWithFullInstructionsAndPreviewDisabledSuppressesInitialHistorySystemMessage() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        setField(harness.panel, "chatMemory", harness.liveChatController.chatMemory(harness.sessionId));
        setField(harness.panel, "showInstructionMessages", true);
        setField(harness.panel, "showNextRequestInstructionPreview", false);

        rebuildHistoryFromMemory(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<ChatMemoryRenderEntry>> entriesCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(harness.chatOutputView).rebuildHistory(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).isEmpty();
    }

    @Test
    public void historyWithOnlyInitialSystemAndMcpToolSummariesSuppressesSystemMessage() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        AssistantProfileChatMemory memory = (AssistantProfileChatMemory) harness.liveChatController.chatMemory(harness.sessionId);
        memory.addToolCallSummary("mcp summary", ToolCaller.MCP);
        setField(harness.panel, "chatMemory", memory);
        setField(harness.panel, "showInstructionMessages", true);

        rebuildHistoryFromMemory(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<ChatMemoryRenderEntry>> entriesCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(harness.chatOutputView).rebuildHistory(entriesCaptor.capture());
        java.util.List<ChatMemoryRenderEntry> entries = entriesCaptor.getValue();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).isToolSummary()).isTrue();
        assertThat(entries.get(0).toolCaller()).isEqualTo(ToolCaller.MCP);
        assertThat(entries.get(0).toolSummaryText()).isEqualTo("mcp summary");
    }

    @Test
    public void instructionPreviewStartsWithSystemTextComposedWithPendingProfile() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        NextRequestInstructionPreviewView previewView = mock(NextRequestInstructionPreviewView.class);
        AssistantProfileSelectionSync selectionSync = mock(AssistantProfileSelectionSync.class);
        when(selectionSync.pendingProfileMessageIfDifferent()).thenReturn(
            new AssistantProfileSwitchMessage("profile", "Reviewer", "profile instructions"));
        setField(harness.panel, "nextRequestInstructionPreviewView", previewView);
        setField(harness.panel, "assistantProfileSelectionSync", selectionSync);
        setField(harness.panel, "showNextRequestInstructionPreview", true);
        setField(harness.panel, "currentSessionUsesAssistantProfile", true);

        invokeRefreshInstructionPreview(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<PreviewInstructionBlock>> blocksCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(previewView).showPreview(blocksCaptor.capture());
        java.util.List<PreviewInstructionBlock> blocks = blocksCaptor.getValue();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).getKind()).isEqualTo(PreviewInstructionKind.SYSTEM);
        assertThat(blocks.get(0).getText()).contains("Profile changes are communicated");
        assertThat(blocks.get(1).getKind()).isEqualTo(PreviewInstructionKind.PROFILE);
        assertThat(blocks.get(1).getText()).isEqualTo("profile instructions");
    }

    @Test
    public void instructionPreviewUsesExactSystemMessageWithoutDynamicGuidance() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        NextRequestInstructionPreviewView previewView = mock(NextRequestInstructionPreviewView.class);
        harness.liveChatController.chatMemory(harness.sessionId).add(
            new GeneralSystemMessage(" exact system ", " exact system ", true));
        setField(harness.panel, "nextRequestInstructionPreviewView", previewView);
        setField(harness.panel, "showNextRequestInstructionPreview", true);

        invokeRefreshInstructionPreview(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<PreviewInstructionBlock>> blocksCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(previewView).showPreview(blocksCaptor.capture());
        java.util.List<PreviewInstructionBlock> blocks = blocksCaptor.getValue();
        assertThat(blocks.get(0).getKind()).isEqualTo(PreviewInstructionKind.SYSTEM);
        assertThat(blocks.get(0).getText()).isEqualTo("exact system");
        assertThat(blocks.get(0).getText()).doesNotContain("Respond in Markdown.");
    }

    @Test
    public void instructionPreviewRepeatsLatestCommittedProfileWhenThereIsNoPendingProfileChange() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        NextRequestInstructionPreviewView previewView = mock(NextRequestInstructionPreviewView.class);
        AssistantProfileSelectionSync selectionSync = mock(AssistantProfileSelectionSync.class);
        harness.liveChatController.chatMemory(harness.sessionId).add(
            new AssistantProfileSwitchMessage("profile", "Reviewer", "profile instructions"));
        setField(harness.panel, "nextRequestInstructionPreviewView", previewView);
        setField(harness.panel, "assistantProfileSelectionSync", selectionSync);
        setField(harness.panel, "showNextRequestInstructionPreview", true);
        setField(harness.panel, "currentSessionUsesAssistantProfile", true);

        invokeRefreshInstructionPreview(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<PreviewInstructionBlock>> blocksCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(previewView).showPreview(blocksCaptor.capture());
        java.util.List<PreviewInstructionBlock> blocks = blocksCaptor.getValue();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).getKind()).isEqualTo(PreviewInstructionKind.SYSTEM);
        assertThat(blocks.get(0).getText()).contains("Profile changes are communicated");
        assertThat(blocks.get(1).getKind()).isEqualTo(PreviewInstructionKind.PROFILE);
        assertThat(blocks.get(1).getLabel()).isEqualTo("Profile message: Reviewer");
        assertThat(blocks.get(1).getText()).isEqualTo("profile instructions");
    }

    @Test
    public void instructionPreviewIncludesPromptReferencePromptTextOnly() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        NextRequestInstructionPreviewView previewView = mock(NextRequestInstructionPreviewView.class);
        AiPromptActionRegistry promptActionRegistry = mock(AiPromptActionRegistry.class);
        when(promptActionRegistry.prompts()).thenReturn(java.util.Arrays.asList(
            new AiPrompt("Summarize branch", "Prompt instructions", false)));
        JTextArea inputArea = new JTextArea("/Summarize branch user suffix");
        setField(harness.panel, "nextRequestInstructionPreviewView", previewView);
        setField(harness.panel, "promptActionRegistry", promptActionRegistry);
        setField(harness.panel, "inputArea", inputArea);
        setField(harness.panel, "showNextRequestInstructionPreview", true);

        invokeRefreshInstructionPreview(harness.panel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<PreviewInstructionBlock>> blocksCaptor =
            ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(previewView).showPreview(blocksCaptor.capture());
        java.util.List<PreviewInstructionBlock> blocks = blocksCaptor.getValue();
        PreviewInstructionBlock promptBlock = blocks.get(blocks.size() - 1);
        assertThat(promptBlock.getKind()).isEqualTo(PreviewInstructionKind.PROMPT);
        assertThat(promptBlock.getLabel()).isEqualTo("Prompt: Summarize branch");
        assertThat(promptBlock.getText()).isEqualTo("Prompt instructions");
        assertThat(promptBlock.getText()).doesNotContain("user suffix");
    }

    @Test
    public void instructionPreviewRefreshesWhenSessionToolAvailabilityChanges() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        NextRequestInstructionPreviewView previewView = mock(NextRequestInstructionPreviewView.class);
        setField(harness.panel, "nextRequestInstructionPreviewView", previewView);
        setField(harness.panel, "showNextRequestInstructionPreview", true);

        harness.panel.setSessionToolAvailabilityOverride(harness.sessionId, ToolAvailabilityLevel.READING);

        verify(previewView).showPreview(any());
    }

    @Test
    public void addToChatWithExistingVisibleChatReusesSessionAndAppliesExplicitOverridesAndSessionThinkingBeforeSending() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSelectedModel = new AtomicReference<String>();
        AtomicReference<AiThinkingEffort> seenThinkingEffort = new AtomicReference<AiThinkingEffort>();
        AtomicReference<ToolAvailabilityLevel> seenToolAvailability = new AtomicReference<ToolAvailabilityLevel>();
        harness.liveChatController.setCurrentSessionThinkingEffortOverride(AiThinkingEffort.LOW);
        LiveChatSessionId originalSessionId = harness.sessionId;
        String explicitSelection = "openrouter|openai/gpt-4.1-mini";
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini")).build(),
            AiToolAvailability.DISABLED,
            null,
            null,
            false,
            null,
            null);
        AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
        });

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier = invocation.getArgument(7);
                    seenToolAvailability.set(toolAvailabilitySupplier == null ? null : toolAvailabilitySupplier.get());
                    AIModelConfiguration modelConfiguration = invocation.getArgument(8);
                    seenSelectedModel.set(selectionValue(modelConfiguration));
                    seenThinkingEffort.set(modelConfiguration == null ? null : modelConfiguration.getThinkingEffort());
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(request, handle);

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(originalSessionId);
        assertThat(harness.liveChatController.currentSessionSelectedModelOverride()).isEqualTo(explicitSelection);
        assertThat(harness.liveChatController.currentSessionToolAvailabilityOverride())
            .isEqualTo(ToolAvailabilityLevel.DISABLED);
        assertThat(seenSelectedModel.get()).isEqualTo(explicitSelection);
        assertThat(seenThinkingEffort.get()).isEqualTo(AiThinkingEffort.LOW);
        assertThat(seenToolAvailability.get()).isEqualTo(ToolAvailabilityLevel.DISABLED);
        verify(harness.chatOutputView).appendUserMessage("Prompt");
    }

    @Test
    public void addToChatWithMatchingRequestSystemMessageReusesSelectedSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.chatMemory(harness.sessionId).add(new GeneralSystemMessage("selected system", "selected system", false));
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSystemMessage = new AtomicReference<String>();
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            " selected system ",
            false,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> {
                    seenSystemMessage.set(invocation.getArgument(9));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(harness.sessionId);
        assertThat(seenSystemMessage.get()).isEqualTo("selected system");
    }

    @Test
    public void addToChatWithMatchingExactRequestSystemMessageReusesSelectedSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.chatMemory(harness.sessionId).add(
            new GeneralSystemMessage("selected system", "selected system", true));
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSystemMessage = new AtomicReference<String>();
        AtomicReference<Boolean> seenIsSystemMessageExact = new AtomicReference<Boolean>();
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            " selected system ",
            true,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                eq(true),
                eq(false)))
                .thenAnswer(invocation -> {
                    seenSystemMessage.set(invocation.getArgument(9));
                    seenIsSystemMessageExact.set(invocation.getArgument(10));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isEqualTo(harness.sessionId);
        assertThat(seenSystemMessage.get()).isEqualTo("selected system");
        assertThat(seenIsSystemMessageExact.get()).isTrue();
    }

    @Test
    public void addToChatWithSameTextButDifferentExactnessStartsNewSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.chatMemory(harness.sessionId).add(new GeneralSystemMessage("same system", "same system", false));
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            "same system",
            true,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                eq(true),
                eq(false)))
                .thenReturn(mock(AIChatService.class));

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isNotEqualTo(harness.sessionId);
        ChatMemory newMemory = harness.liveChatController.chatMemory(harness.liveChatController.currentSessionId());
        assertThat(((AssistantProfileChatMemory) newMemory).isSystemMessageExact()).isTrue();
    }

    @Test
    public void addToChatWithDifferentRequestSystemMessageStartsNewSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.chatMemory(harness.sessionId).add(new GeneralSystemMessage("old system", "old system", false));
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSystemMessage = new AtomicReference<String>();
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            "new system",
            false,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> {
                    seenSystemMessage.set(invocation.getArgument(9));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isNotEqualTo(harness.sessionId);
        assertThat(seenSystemMessage.get()).isEqualTo("new system");
    }

    @Test
    public void addToChatWithNoVisibleChatCreatesNewSession() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        LiveChatSessionId originalSessionId = harness.sessionId;
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            null,
            false,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(new javax.swing.JPanel());
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(mock(AIChatService.class));

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(harness.liveChatController.currentSessionId()).isNotEqualTo(originalSessionId);
        assertThat(currentSessionNameEdited(harness.liveChatController)).isFalse();
    }

    @Test
    public void addToChatNewSessionUsesResolvedPromptDisplayNameWhenProvided() throws Exception {
        PanelHarness harness = newPanelHarness(false);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            "Rewrite",
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            null,
            false,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(new javax.swing.JPanel());
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(mock(AIChatService.class));

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(currentSessionDisplayName(harness.liveChatController)).isEqualTo("Rewrite");
        assertThat(currentSessionNameEdited(harness.liveChatController)).isTrue();
    }

    @Test
    public void addToChatWithCurrentSelectionsResolvesTargetSessionValuesAtDispatchStart() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        harness.liveChatController.setCurrentSessionSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
        harness.liveChatController.setCurrentSessionToolAvailabilityOverride(ToolAvailabilityLevel.READING);
        when(harness.chatToolAvailabilitySettings.getToolAvailability()).thenReturn(ToolAvailabilityLevel.EDITING);
        ChatRequestFlow requestFlow = mock(ChatRequestFlow.class);
        ChatRequestFlowFactory chatRequestFlowFactory = mock(ChatRequestFlowFactory.class);
        when(chatRequestFlowFactory.create(any(), any())).thenReturn(requestFlow);
        setField(harness.panel, "chatRequestFlowFactory", chatRequestFlowFactory);
        AtomicReference<String> seenSelectedModel = new AtomicReference<String>();
        AtomicReference<ToolAvailabilityLevel> seenToolAvailability = new AtomicReference<ToolAvailabilityLevel>();
        ResolvedAiRequest request = new ResolvedAiRequest(
            "Prompt",
            null,
            Duration.ofSeconds(10),
            AiRequestMode.ADD_TO_CHAT,
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
            AiToolAvailability.CURRENT,
            null,
            null,
            false,
            null,
            null);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class);
             MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class);
             MockedStatic<UITools> uiTools = mockStatic(UITools.class);
             MockedStatic<AIChatServiceFactory> chatServiceFactory = mockStatic(AIChatServiceFactory.class);
             MockedConstruction<AIToolSetBuilder> toolSetBuilders = mockConstruction(AIToolSetBuilder.class,
                 (mock, context) -> {
                     AIToolSet toolSet = mock(AIToolSet.class);
                     when(mock.toolCallSummaryHandler(any())).thenReturn(mock);
                     when(mock.mapAccessListener(any())).thenReturn(mock);
                     when(mock.codeHostService(org.mockito.ArgumentMatchers.nullable(org.freeplane.features.ai.code.AiCodeHostService.class))).thenReturn(mock);
                     when(mock.aiCodeOperationAuthorizer(org.mockito.ArgumentMatchers.nullable(org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer.class))).thenReturn(mock);
                     when(mock.build()).thenReturn(toolSet);
                     when(mock.buildToolObjects()).thenReturn(java.util.Collections.<Object>singletonList(toolSet));
                 })) {
            ResourceController resourceController = mock(ResourceController.class);
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            JTabbedPane tabs = mock(JTabbedPane.class);
            when(tabs.getSelectedComponent()).thenReturn(harness.panel);
            uiTools.when(UITools::getFreeplaneTabbedPanel).thenReturn(tabs);
            chatServiceFactory.when(() -> AIChatServiceFactory.createService(
                any(AIToolSet.class),
                org.mockito.ArgumentMatchers.<java.util.Collection<?>>any(),
                any(ChatMemory.class),
                any(ChatTokenUsageTracker.class),
                any(ToolCallSummaryHandler.class),
                org.mockito.ArgumentMatchers.<Supplier<Boolean>>any(),
                org.mockito.ArgumentMatchers.<Consumer<TokenUsage>>any(),
                org.mockito.ArgumentMatchers.<Supplier<ToolAvailabilityLevel>>any(),
                nullable(AIModelConfiguration.class),
                nullable(String.class),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier = invocation.getArgument(7);
                    seenToolAvailability.set(toolAvailabilitySupplier == null ? null : toolAvailabilitySupplier.get());
                    seenSelectedModel.set(selectionValue(invocation.getArgument(8)));
                    return mock(AIChatService.class);
                });

            ChatRequestFlow started = harness.panel.startAddToChatAiRequestAtDispatch(
                request,
                new AiRequestHandleImpl(Runnable::run, result -> {
                }));

            assertThat(started).isSameAs(requestFlow);
        }

        assertThat(seenSelectedModel.get()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(seenToolAvailability.get()).isEqualTo(ToolAvailabilityLevel.READING);
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
            new ResolvedAiRequest(
                "Prompt",
                null,
                Duration.ofSeconds(10),
                AiRequestMode.ADD_TO_CHAT,
                AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build(),
                AiToolAvailability.CURRENT,
                null,
                null,
                false,
                null,
                null),
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
        when(chatPromptRunnerFactory.createHidden(any())).thenReturn(hiddenRunner);
        when(chatPromptRunnerFactory.createShown(any(), any(), any(), any(), any())).thenReturn(shownRunner);
        when(hiddenRunner.submitHiddenRequest(
            "Hidden prompt",
            "Hidden body",
            null,
            ToolAvailabilityLevel.EDITING,
            null,
            null,
            true,
            null,
            null,
            false,
            true,
            null)).thenReturn(true);
        when(shownRunner.startShownPrompt("Prompt body", null, ToolAvailabilityLevel.EDITING, null, null))
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
            ToolAvailabilityLevel.EDITING,
            null,
            null,
            true,
            null,
            null,
            false,
            true,
            null);
        verify(chatPromptRunnerFactory).createShown(any(), any(), eq(requestFlow), any(), any());
        verify(shownRunner).startShownPrompt("Prompt body", null, ToolAvailabilityLevel.EDITING, null, null);
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
        when(chatPromptRunnerFactory.createHidden(any())).thenReturn(chatPromptRunner);
        when(chatPromptRunner.submitHiddenRequest(
            "Hidden prompt",
            "Prompt body",
            null,
            ToolAvailabilityLevel.EDITING,
            null,
            null,
            true,
            null,
            null,
            false,
            true,
            null)).thenReturn(true);
        setField(harness.panel, "chatPromptRunnerFactory", chatPromptRunnerFactory);

        harness.panel.runPrompt(new AiPrompt("Hidden prompt", "Prompt body", false));

        verify(chatPromptRunnerFactory).createHidden(any());
        verify(chatPromptRunner).submitHiddenRequest(
            "Hidden prompt",
            "Prompt body",
            null,
            ToolAvailabilityLevel.EDITING,
            null,
            null,
            true,
            null,
            null,
            false,
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
        when(firstRunner.startShownPrompt("Prompt one", null, ToolAvailabilityLevel.EDITING, null, null))
            .thenReturn(true);
        when(secondRunner.startShownPrompt("Prompt two", null, ToolAvailabilityLevel.EDITING, null, null))
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
        inOrder.verify(firstRunner).startShownPrompt("Prompt one", null, ToolAvailabilityLevel.EDITING, null, null);
        inOrder.verify(chatRequestFlowFactory).create(any(), any());
        inOrder.verify(chatPromptRunnerFactory).createShown(any(), any(), eq(secondFlow), any(), any());
        inOrder.verify(secondRunner).startShownPrompt("Prompt two", null, ToolAvailabilityLevel.EDITING, null, null);
    }

    @Test
    public void canReopenAiOwnedCodeIsFalseWhenCurrentAiHostStateIsNoCode() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        AiCodeHostService codeHostService = mock(AiCodeHostService.class);
        when(codeHostService.readCode(any(ReadCodeRequest.class))).thenReturn(new ReadCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.NO_CODE,
            null,
            null,
            null,
            null,
            null,
            null,
            null));

        harness.panel.setCodeHostService(codeHostService);

        assertThat(harness.panel.canReopenAiOwnedCode()).isFalse();
    }

    @Test
    public void canReopenAiOwnedCodeIsTrueWhenCurrentAiHostStateExists() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        AiCodeHostService codeHostService = mock(AiCodeHostService.class);
        when(codeHostService.readCode(any(ReadCodeRequest.class))).thenReturn(new ReadCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.EDITED,
            null,
            new CodeStateToken("code", "fingerprint"),
            new CodeStateContent("println 1", null),
            null,
            null,
            null,
            null));

        harness.panel.setCodeHostService(codeHostService);

        assertThat(harness.panel.canReopenAiOwnedCode()).isTrue();
    }

    @Test
    public void reopenAiOwnedCodeDelegatesToRoutingHost() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        ReopenableAiHost aiHost = new ReopenableAiHost();
        RoutingAiCodeHostService routingHost = new RoutingAiCodeHostService(mock(AiCodeHostService.class), () -> aiHost);

        harness.panel.setCodeHostService(routingHost);

        assertThat(harness.panel.reopenAiOwnedCode()).isTrue();
        assertThat(aiHost.shownCode).isEqualTo("current");
    }

    @Test
    public void handleCodeRunFinishedSubmitsAutomaticCodeStatusTextToPendingSession() throws Exception {
        PanelHarness harness = newPanelHarness(true);
        doReturn(true).when(harness.panel).submitMessageToSession(eq(harness.sessionId), any());
        rememberPendingAiOwnedUserRunFollowup(
            harness.panel,
            new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.WAITING_FOR_USER_RUN,
                org.freeplane.features.ai.code.ScriptRunInitiator.AI,
                new CodeStateToken("code", "args"),
                null,
                null,
                null,
                null),
            harness.sessionId);

        harness.panel.handleCodeRunFinished(new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.RUN_SUCCEEDED,
            org.freeplane.features.ai.code.ScriptRunInitiator.USER,
            new CodeStateToken("code", "args"),
            null,
            null,
            null,
            null));

        verify(harness.panel).submitMessageToSession(eq(harness.sessionId), any());
        assertThat(getField(harness.panel, "pendingAiOwnedUserRunFollowupSessionId")).isNull();
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

    private void rebuildHistoryFromMemory(AIChatPanel panel) throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod("rebuildHistoryFromMemory");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private void refreshChatMemoryMaximumTokenCount(AIChatPanel panel) throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod("refreshChatMemoryMaximumTokenCount");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private void invokeRefreshInstructionPreview(AIChatPanel panel) throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod("refreshInstructionPreview");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private void appendToolSummaryToSession(AIChatPanel panel,
                                            LiveChatSessionId sessionId,
                                            ToolCallSummary summary) throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod(
            "appendToolSummaryToSession",
            LiveChatSessionId.class,
            ToolCallSummary.class);
        method.setAccessible(true);
        method.invoke(panel, sessionId, summary);
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
        ToolAvailabilityLevelSettings chatToolAvailabilitySettings = mock(ToolAvailabilityLevelSettings.class);
        when(chatToolAvailabilitySettings.getToolAvailability()).thenReturn(ToolAvailabilityLevel.EDITING);
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
        setField(panel, "chatToolAvailabilityMenu", mock(ToolAvailabilityLevelMenu.class));
        setField(panel, "modelSelectionController", mock(ChatModelSelector.class));
        setField(panel, "thinkingEffortSelector", mock(ChatThinkingEffortSelector.class));
        setField(panel, "aiRequestConfigurationResolver", aiRequestConfigurationResolver);
        setField(panel, "configuration", configuration);
        setField(panel, "aiPromptRequestComposer", new AiPromptRequestComposer(availableMaps, textController));
        setField(panel, "promptReferenceResolver", new PromptReferenceResolver());
        setField(panel, "aiSelectionOverrideResolver", mock(AiSelectionOverrideResolver.class));
        setField(panel, "availableMaps", availableMaps);
        setField(panel, "assistantProfileSelectionSync", mock(AssistantProfileSelectionSync.class));
        setField(panel, "systemInstructionComposer", new SystemInstructionComposer());
        setField(panel, "nextRequestInstructionPreviewView", mock(NextRequestInstructionPreviewView.class));
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
            chatDisplaySettings,
            chatToolAvailabilitySettings,
            aiRequestConfigurationResolver);
    }

    private void flushEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
        });
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AIChatPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = AIChatPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void rememberPendingAiOwnedUserRunFollowup(AIChatPanel panel,
                                                       RunCodeResponse response,
                                                       LiveChatSessionId sessionId) throws Exception {
        Method method = AIChatPanel.class.getDeclaredMethod(
            "rememberAiOwnedUserRunFollowup",
            RunCodeResponse.class,
            LiveChatSessionId.class);
        method.setAccessible(true);
        method.invoke(panel, response, sessionId);
    }

    private Object currentSession(LiveChatController liveChatController) throws Exception {
        Object sessionManager = sessionManager(liveChatController);
        java.lang.reflect.Method getCurrentSession = sessionManager.getClass().getDeclaredMethod("getCurrentSession");
        getCurrentSession.setAccessible(true);
        return getCurrentSession.invoke(sessionManager);
    }

    private Object sessionManager(LiveChatController liveChatController) throws Exception {
        Field sessionManagerField = LiveChatController.class.getDeclaredField("liveChatSessionManager");
        sessionManagerField.setAccessible(true);
        return sessionManagerField.get(liveChatController);
    }

    private String currentSessionDisplayName(LiveChatController liveChatController) throws Exception {
        Object session = currentSession(liveChatController);
        java.lang.reflect.Method getDisplayName = session.getClass().getDeclaredMethod("getDisplayName");
        getDisplayName.setAccessible(true);
        return (String) getDisplayName.invoke(session);
    }

    private java.util.List<String> sessionToolSummaryTexts(LiveChatController liveChatController,
                                                           LiveChatSessionId sessionId) throws Exception {
        return toolSummaryTexts(liveChatController.chatMemory(sessionId));
    }

    private java.util.List<String> toolSummaryTexts(ChatMemory chatMemory) throws Exception {
        if (!(chatMemory instanceof AssistantProfileChatMemory)) {
            return java.util.Collections.emptyList();
        }
        Field conversationMessagesField = AssistantProfileChatMemory.class.getDeclaredField("conversationMessages");
        conversationMessagesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<dev.langchain4j.data.message.ChatMessage> conversationMessages =
            (java.util.List<dev.langchain4j.data.message.ChatMessage>) conversationMessagesField.get(chatMemory);
        java.util.List<String> summaries = new java.util.ArrayList<String>();
        for (dev.langchain4j.data.message.ChatMessage message : conversationMessages) {
            if (message == null || !message.getClass().getSimpleName().equals("ToolCallSummaryMessage")) {
                continue;
            }
            summaries.add(((dev.langchain4j.data.message.SystemMessage) message).text());
        }
        return summaries;
    }

    private int estimateTokens(ChatMessage... messages) {
        OpenAiTokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimator.estimateTokenCountInMessage(message);
        }
        return total;
    }

    private String repeatedWords(String word, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(word);
        }
        return builder.toString();
    }

    private boolean currentSessionNameEdited(LiveChatController liveChatController) throws Exception {
        Object session = currentSession(liveChatController);
        java.lang.reflect.Method isNameEdited = session.getClass().getDeclaredMethod("isNameEdited");
        isNameEdited.setAccessible(true);
        return ((Boolean) isNameEdited.invoke(session)).booleanValue();
    }

    private static class FocusAwareTextArea extends JTextArea {
        private boolean focusRequested;

        @Override
        public boolean requestFocusInWindow() {
            focusRequested = true;
            return true;
        }
    }

    private static class CapturingChatRequestFlowFactory extends ChatRequestFlowFactory {
        private ChatRequestFlow.RequestCallbacks callbacks;

        @Override
        public ChatRequestFlow create(ChatRequestFlow.RequestCallbacks callbacks,
                               ChatTokenUsageTracker tokenUsageTracker) {
            this.callbacks = callbacks;
            return mock(ChatRequestFlow.class);
        }
    }

    public static class ReopenableAiHost implements AiCodeHostService {
        private String shownCode;

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            return new ReadCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.EDITED,
                null,
                new CodeStateToken("code", "fingerprint"),
                new CodeStateContent("println 1", null),
                null,
                null,
                null,
                null);
        }

        @Override
        public WriteCodeResponse writeCode(WriteCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunCodeResponse runCode(RunCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRunListener(AiCodeRunListener listener) {
        }

        @Override
        public void removeRunListener(AiCodeRunListener listener) {
        }

        public void showCurrentCode() {
            shownCode = "current";
        }
    }

    private static String selectionValue(AIModelConfiguration modelConfiguration) {
        if (modelConfiguration == null || modelConfiguration.getModelSelection() == null) {
            return null;
        }
        org.freeplane.plugin.ai.model.AIModelSelection selection = modelConfiguration.getModelSelection();
        return org.freeplane.plugin.ai.model.AIModelSelection.createSelectionValue(
            selection.getProviderName(),
            selection.getModelName());
    }

    private static class PanelHarness {
        private final AIChatPanel panel;
        private final LiveChatController liveChatController;
        private final LiveChatSessionId sessionId;
        private final ChatOutputView chatOutputView;
        private final ChatDisplaySettings chatDisplaySettings;
        private final ToolAvailabilityLevelSettings chatToolAvailabilitySettings;
        private final AiRequestConfigurationResolver aiRequestConfigurationResolver;

        private PanelHarness(AIChatPanel panel,
                             LiveChatController liveChatController,
                             LiveChatSessionId sessionId,
                             ChatOutputView chatOutputView,
                             ChatDisplaySettings chatDisplaySettings,
                             ToolAvailabilityLevelSettings chatToolAvailabilitySettings,
                             AiRequestConfigurationResolver aiRequestConfigurationResolver) {
            this.panel = panel;
            this.liveChatController = liveChatController;
            this.sessionId = sessionId;
            this.chatOutputView = chatOutputView;
            this.chatDisplaySettings = chatDisplaySettings;
            this.chatToolAvailabilitySettings = chatToolAvailabilitySettings;
            this.aiRequestConfigurationResolver = aiRequestConfigurationResolver;
        }
    }
}

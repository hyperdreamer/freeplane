package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatRequestFlowTest {

    @Test
    public void failureRestoresChatSnapshotWithoutAppLevelRetry() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("question"))
            .thenThrow(new RuntimeException("HTTP 503 Service Unavailable"));

        uut.beginRequest("question");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();
        assertThat(callbacks.assistantResponseCount).isZero();
        assertThat(callbacks.assistantErrorCount).isEqualTo(1);
        assertThat(callbacks.restoreCount).isEqualTo(1);
        assertThat(callbacks.failureRecoveryCount).isEqualTo(1);
        assertThat(callbacks.lastFailureMessage).isEqualTo("HTTP 503 Service Unavailable");
        verify(chatService, times(1)).chat("question");
    }

    @Test
    public void cancelRestoresChatSnapshotWithoutFailureMessage() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("question")).thenAnswer(invocation -> {
            Thread.sleep(200);
            return "late";
        });

        uut.beginRequest("question");
        uut.submitRequest(chatService);
        uut.cancelActiveRequest();

        assertThat(callbacks.awaitFinished()).isTrue();
        assertThat(callbacks.assistantResponseCount).isZero();
        assertThat(callbacks.assistantErrorCount).isZero();
        assertThat(callbacks.restoreCount).isEqualTo(1);
        assertThat(callbacks.failureRecoveryCount).isZero();
        assertThat(callbacks.cancelledCount).isEqualTo(1);
    }

    @Test
    public void nonRetryableContextTooLargeFailureDoesNotRetry() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("question"))
            .thenThrow(new RuntimeException("context too large"));

        uut.beginRequest("question");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();
        assertThat(callbacks.assistantResponseCount).isZero();
        assertThat(callbacks.assistantErrorCount).isEqualTo(1);
        assertThat(callbacks.restoreCount).isEqualTo(1);
        assertThat(callbacks.failureRecoveryCount).isEqualTo(1);
        assertThat(callbacks.lastFailureMessage).isEqualTo("context too large");
        verify(chatService, times(1)).chat("question");
    }

    @Test
    public void nonRetryableModelNotFoundFailureDoesNotRetry() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("question"))
            .thenThrow(new RuntimeException("model not found"));

        uut.beginRequest("question");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();
        assertThat(callbacks.assistantResponseCount).isZero();
        assertThat(callbacks.assistantErrorCount).isEqualTo(1);
        assertThat(callbacks.restoreCount).isEqualTo(1);
        assertThat(callbacks.failureRecoveryCount).isEqualTo(1);
        assertThat(callbacks.lastFailureMessage).isEqualTo("model not found");
        verify(chatService, times(1)).chat("question");
    }

    @Test
    public void automaticCodeStatusRequestRebuildsVisibleHistoryAfterAssistantResponse() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("Automatic app-authored code-status message:\ncodeState=RUN_FAILED"))
            .thenReturn("analysis");

        uut.beginRequest("Automatic app-authored code-status message:\ncodeState=RUN_FAILED");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();
        assertThat(callbacks.assistantResponseCount).isEqualTo(1);
        assertThat(callbacks.rebuildHistoryCount).isEqualTo(1);
    }

    @Test
    public void onProviderUsageRecordsUsageAndRefreshesCounters() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatTokenUsageTracker tokenUsageTracker = spy(new ChatTokenUsageTracker(totals -> {}));
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, tokenUsageTracker);
        TokenUsage usage = mock(TokenUsage.class);
        when(usage.inputTokenCount()).thenReturn(120);
        when(usage.outputTokenCount()).thenReturn(80);
        uut.onProviderUsage(usage);

        verify(tokenUsageTracker, times(1)).recordProviderUsage(usage);
        assertThat(callbacks.postResponseEvictionCount).isZero();
        assertThat(callbacks.refreshTokenCountersCount).isZero();
    }

    @Test
    public void restoreChatSnapshot_truncatesAssistantProfileMemoryThroughInternalAdapter() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        memory.add(UserMessage.from("hello"));
        memory.add(AiMessage.from("world"));
        uut.updateChatMemory(memory);

        uut.beginRequest("question");
        memory.add(UserMessage.from("extra"));
        uut.restoreChatSnapshot();

        assertThat(memory.conversationMessageCount()).isEqualTo(2);
        assertThat(callbacks.restoreCount).isEqualTo(1);
        assertThat(callbacks.rebuildHistoryCount).isEqualTo(1);
    }

    @Test
    public void requestCompletionTriggersPostResponseEvictionWhenWindowAdvances() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatTokenUsageTracker tokenUsageTracker = spy(new ChatTokenUsageTracker(totals -> {}));
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, tokenUsageTracker);
        AssistantProfileChatMemory memory = mock(AssistantProfileChatMemory.class);
        AIChatService chatService = mock(AIChatService.class);
        when(chatService.chat("question")).thenReturn("ok");
        when(memory.onResponseTokenUsage((TokenUsage) null)).thenReturn(true);

        uut.updateChatMemory(memory);
        uut.beginRequest("question");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();

        verify(memory, times(1)).onResponseTokenUsage((TokenUsage) null);
        assertThat(callbacks.postResponseEvictionCount).isEqualTo(1);
        assertThat(callbacks.refreshTokenCountersCount).isZero();
    }

    @Test
    public void requestCompletionUsesLatestProviderUsageForCompaction() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatTokenUsageTracker tokenUsageTracker = spy(new ChatTokenUsageTracker(totals -> {}));
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, tokenUsageTracker);
        AssistantProfileChatMemory memory = mock(AssistantProfileChatMemory.class);
        AIChatService chatService = mock(AIChatService.class);
        TokenUsage usage = mock(TokenUsage.class);
        when(chatService.chat("question")).thenReturn("ok");
        when(memory.onResponseTokenUsage(usage)).thenReturn(false);

        uut.updateChatMemory(memory);
        uut.onProviderUsage(usage);
        uut.beginRequest("question");
        uut.submitRequest(chatService);

        assertThat(callbacks.awaitFinished()).isTrue();

        verify(memory, times(1)).onResponseTokenUsage(usage);
    }

    @Test
    public void onToolCallSummaryStoresSummaryAndAppendsMcpRenderEntry() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.updateChatMemory(memory);
        uut.onToolCallSummary(new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP));

        assertThat(callbacks.toolSummaryAppendCount).isEqualTo(1);
        assertThat(callbacks.rebuildHistoryCount).isZero();
        assertThat(callbacks.lastSummaryEntry).isNotNull();
        assertThat(callbacks.lastSummaryEntry.isToolSummary()).isTrue();
        assertThat(callbacks.lastSummaryEntry.toolSummaryText()).isEqualTo("mcp summary");
        assertThat(callbacks.lastSummaryEntry.toolCaller()).isEqualTo(ToolCaller.MCP);
        assertThat(memory.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("mcp summary");
    }

    @Test
    public void chatOwnedToolSummaryAppendsIncrementallyWhenVisibleHistoryRebuildCounterIsUnchanged() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.updateChatMemory(memory);
        uut.beginRequest("question");
        uut.onToolCallSummary(new ToolCallSummary("searchNodes", "chat summary", false, ToolCaller.CHAT));

        assertThat(callbacks.toolSummaryAppendCount).isEqualTo(1);
        assertThat(callbacks.rebuildHistoryCount).isZero();
        assertThat(memory.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("chat summary");
    }

    @Test
    public void chatOwnedToolSummaryRequestsVisibleHistoryRebuildWhenCounterChangesAfterRequestStart() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.updateChatMemory(memory);
        uut.beginRequest("question");
        callbacks.visibleHistoryRebuildCounter = 1L;
        uut.onToolCallSummary(new ToolCallSummary("searchNodes", "chat summary", false, ToolCaller.CHAT));

        assertThat(callbacks.toolSummaryAppendCount).isZero();
        assertThat(callbacks.rebuildHistoryCount).isEqualTo(1);
        assertThat(memory.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("chat summary");
    }

    @Test
    public void mcpToolSummaryStillAppendsIncrementallyWhenCounterChangesAfterRequestStart() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.updateChatMemory(memory);
        uut.beginRequest("question");
        callbacks.visibleHistoryRebuildCounter = 1L;
        uut.onToolCallSummary(new ToolCallSummary("searchNodes", "mcp summary", false, ToolCaller.MCP));

        assertThat(callbacks.toolSummaryAppendCount).isEqualTo(1);
        assertThat(callbacks.rebuildHistoryCount).isZero();
        assertThat(memory.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .extracting(ChatMemoryRenderEntry::toolSummaryText)
            .contains("mcp summary");
    }

    @Test
    public void onToolCallSummaryDoesNothingWhenToolHistoryHidden() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        callbacks.toolCallHistoryVisible = false;
        ChatRequestFlow uut = new ChatRequestFlow(callbacks, new ChatTokenUsageTracker(totals -> {}));
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.updateChatMemory(memory);
        uut.onToolCallSummary(new ToolCallSummary("searchNodes", "hidden summary", false, ToolCaller.MCP));

        assertThat(callbacks.toolSummaryAppendCount).isZero();
        assertThat(memory.activeConversationRenderEntries())
            .filteredOn(ChatMemoryRenderEntry::isToolSummary)
            .isEmpty();
    }

    private static class RecordingCallbacks implements ChatRequestFlow.RequestCallbacks {

        private final CountDownLatch finishedLatch = new CountDownLatch(1);
        private int assistantResponseCount;
        private int assistantErrorCount;
        private int failureRecoveryCount;
        private int cancelledCount;
        private String lastFailureUserMessage;
        private String lastFailureMessage;
        private int synchronizeTranscriptCount;
        private int rebuildHistoryCount;
        private int restoreCount;
        private int postResponseEvictionCount;
        private int refreshTokenCountersCount;
        private boolean toolCallHistoryVisible = true;
        private long visibleHistoryRebuildCounter;
        private int toolSummaryAppendCount;
        private ChatMemoryRenderEntry lastSummaryEntry;

        @Override
        public void onRequestStarted() {
        }

        @Override
        public void onRequestFinished() {
            finishedLatch.countDown();
        }

        @Override
        public void onUserTextRestored(String userText) {
            restoreCount++;
        }

        @Override
        public void onRequestFailed(String userText, String errorMessage) {
            failureRecoveryCount++;
            lastFailureUserMessage = userText;
            lastFailureMessage = errorMessage;
        }

        @Override
        public void onRequestCancelled() {
            cancelledCount++;
        }

        @Override
        public void onAssistantResponse(String text) {
            assistantResponseCount++;
        }

        @Override
        public void onAssistantError(String text) {
            assistantErrorCount++;
        }

        @Override
        public void synchronizeTranscriptWithMemory() {
            synchronizeTranscriptCount++;
        }

        @Override
        public void rebuildVisibleHistoryFromMemory() {
            rebuildHistoryCount++;
        }

        @Override
        public void onPostResponseEviction() {
            postResponseEvictionCount++;
        }

        @Override
        public void refreshTokenCounters() {
            refreshTokenCountersCount++;
        }

        @Override
        public boolean isToolCallHistoryVisible() {
            return toolCallHistoryVisible;
        }

        @Override
        public long currentVisibleHistoryRebuildCounter() {
            return visibleHistoryRebuildCounter;
        }

        @Override
        public void onToolSummaryAppended(ChatMemoryRenderEntry entry) {
            toolSummaryAppendCount++;
            lastSummaryEntry = entry;
        }

        boolean awaitFinished() throws InterruptedException {
            return finishedLatch.await(3, TimeUnit.SECONDS);
        }
    }
}

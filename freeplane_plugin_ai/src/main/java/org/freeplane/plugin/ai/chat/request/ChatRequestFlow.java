package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.model.output.TokenUsage;
import java.util.function.Supplier;
import javax.swing.SwingWorker;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.memory.SingleTurnChatMemory;
import org.freeplane.plugin.ai.chat.memory.SingleTurnChatMemoryFactory;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;

public class ChatRequestFlow {

    public interface RequestCallbacks {
        void onRequestStarted();
        void onRequestFinished();
        void onUserTextRestored(String userText);
        void onRequestFailed(String userText, String errorMessage);
        void onRequestCancelled();
        void onAssistantResponse(String text);
        void onAssistantError(String text);
        void synchronizeTranscriptWithMemory();
        void rebuildHistoryFromTranscript();
        void onPostResponseEviction();
        void refreshTokenCounters();
        boolean isToolCallHistoryVisible();
        void onToolSummaryAppended(ChatMemoryRenderEntry entry);
    }

    private final RequestCallbacks callbacks;
    private final ChatRequestCancellation requestCancellation;
    private final ChatTokenUsageTracker tokenUsageTracker;
    private AssistantProfileChatMemory assistantProfileChatMemory;
    private SingleTurnChatMemory singleTurnChatMemory = SingleTurnChatMemoryFactory.forMemory(null);
    private SwingWorker<String, Void> activeWorker;
    private boolean requestInProgress;
    private int activeRequestId;
    private int snapshotChatSize;
    private String snapshotUserText;
    private TokenUsage responseUsage;
    private String requestFailureMessage;

    public ChatRequestFlow(RequestCallbacks callbacks, ChatTokenUsageTracker tokenUsageTracker) {
        this.callbacks = callbacks;
        this.tokenUsageTracker = tokenUsageTracker;
        this.requestCancellation = new ChatRequestCancellation();
    }

    public boolean isRequestActive() {
        return requestInProgress;
    }

    public Supplier<Boolean> cancellationSupplier() {
        return requestCancellation::isCancelled;
    }

    void refreshTokenCounters() {
        callbacks.refreshTokenCounters();
    }

    public void updateChatMemory(dev.langchain4j.memory.ChatMemory chatMemory) {
        assistantProfileChatMemory = chatMemory instanceof AssistantProfileChatMemory
            ? (AssistantProfileChatMemory) chatMemory
            : null;
        singleTurnChatMemory = SingleTurnChatMemoryFactory.forMemory(chatMemory);
    }

    public void onToolCallSummary(ToolCallSummary summary) {
        if (summary == null || !callbacks.isToolCallHistoryVisible()) {
            return;
        }
        if (assistantProfileChatMemory != null) {
            assistantProfileChatMemory.addToolCallSummary(summary.getSummaryText(), summary.getToolCaller());
        }
        callbacks.onToolSummaryAppended(
            ChatMemoryRenderEntry.forToolSummary(summary.getSummaryText(), summary.getToolCaller()));
    }

    public void onProviderUsage(TokenUsage usage) {
        if (usage != null && tokenUsageTracker != null) {
            tokenUsageTracker.recordProviderUsage(usage);
        }
        responseUsage = usage;
    }

    public void beginRequest(String userMessage) {
        requestCancellation.reset();
        activeRequestId++;
        snapshotUserText = userMessage;
        snapshotChatSize = singleTurnChatMemory.snapshotSize();
        requestFailureMessage = null;
        requestInProgress = true;
        callbacks.onRequestStarted();
    }

    public void submitRequest(AIChatService chatService) {
        executeRequestWorker(chatService, snapshotUserText, activeRequestId);
    }

    public void captureChatSnapshot() {
        snapshotChatSize = singleTurnChatMemory.snapshotSize();
    }

    public void cancelActiveRequest() {
        if (!isRequestActive()) {
            return;
        }
        requestCancellation.cancel();
        activeRequestId++;
        if (activeWorker != null) {
            activeWorker.cancel(true);
        }
        requestInProgress = false;
        restoreChatSnapshot();
    }

    void restoreChatSnapshot() {
        singleTurnChatMemory.truncateTo(snapshotChatSize);
        callbacks.synchronizeTranscriptWithMemory();
        callbacks.rebuildHistoryFromTranscript();
        activeWorker = null;
        requestInProgress = false;
        callbacks.onUserTextRestored(snapshotUserText);
        if (requestFailureMessage != null) {
            callbacks.onRequestFailed(snapshotUserText, requestFailureMessage);
        }
        callbacks.onRequestFinished();
        if (requestFailureMessage == null && requestCancellation.isCancelled()) {
            callbacks.onRequestCancelled();
        }
        callbacks.refreshTokenCounters();
        clearRequestState();
    }

    void resetRequestState() {
        clearRequestState();
    }

    private void executeRequestWorker(AIChatService chatService, String userMessage, int requestId) {
        activeWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return chatService.chat(userMessage);
            }

            @Override
            protected void done() {
                if (requestId != activeRequestId || requestCancellation.isCancelled()) {
                    return;
                }
                try {
                    callbacks.onAssistantResponse(get());
                    finishRequest();
                } catch (Exception error) {
                    requestFailureMessage = normalizeErrorMessage(error);
                    callbacks.onAssistantError(requestFailureMessage);
                    restoreChatSnapshot();
                }
            }
        };
        activeWorker.execute();
    }

    private String normalizeErrorMessage(Exception error) {
        Throwable rootCause = rootCause(error);
        String message = rootCause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return rootCause.getClass().getSimpleName();
        }
        return message;
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void finishRequest() {
        applyPostResponseCompaction();
        activeWorker = null;
        requestInProgress = false;
        callbacks.onRequestFinished();
        clearRequestState();
    }

    private void clearRequestState() {
        snapshotChatSize = 0;
        snapshotUserText = null;
        responseUsage = null;
        requestFailureMessage = null;
    }

    private void applyPostResponseCompaction() {
        boolean evicted = assistantProfileChatMemory != null
            && assistantProfileChatMemory.onResponseTokenUsage(responseUsage);
        if (evicted) {
            callbacks.onPostResponseEviction();
        }
    }
}

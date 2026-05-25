package org.freeplane.plugin.ai.chat;

class ChatRequestFlowFactory {
    ChatRequestFlow create(ChatRequestFlow.RequestCallbacks callbacks,
                           ChatTokenUsageTracker tokenUsageTracker) {
        return new ChatRequestFlow(callbacks, tokenUsageTracker);
    }
}

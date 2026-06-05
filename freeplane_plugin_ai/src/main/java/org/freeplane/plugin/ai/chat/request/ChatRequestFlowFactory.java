package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;

public class ChatRequestFlowFactory {
    public ChatRequestFlow create(ChatRequestFlow.RequestCallbacks callbacks,
                           ChatTokenUsageTracker tokenUsageTracker) {
        return new ChatRequestFlow(callbacks, tokenUsageTracker);
    }
}

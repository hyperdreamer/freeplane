package org.freeplane.plugin.ai.chat;

import org.freeplane.api.ai.AiRequest;

class AddToChatDispatchJobFactory {
    private final AIChatPanel aiChatPanel;

    AddToChatDispatchJobFactory(AIChatPanel aiChatPanel) {
        this.aiChatPanel = aiChatPanel;
    }

    AddToChatDispatchJob create(AiRequest request,
                                AiRequestHandleImpl handle,
                                AiRequestTimeoutController timeoutController) {
        return new AddToChatDispatchJob(aiChatPanel, request, handle, timeoutController);
    }
}

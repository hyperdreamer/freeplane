package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.chat.ui.AIChatPanel;

public class AddToChatDispatchJobFactory {
    private final AIChatPanel aiChatPanel;

    public AddToChatDispatchJobFactory(AIChatPanel aiChatPanel) {
        this.aiChatPanel = aiChatPanel;
    }

    AddToChatDispatchJob create(ResolvedAiRequest request,
                                AiRequestHandleImpl handle,
                                AiRequestTimeoutController timeoutController) {
        return new AddToChatDispatchJob(aiChatPanel, request, handle, timeoutController);
    }
}

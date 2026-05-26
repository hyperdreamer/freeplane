package org.freeplane.plugin.ai.chat;

class AddToChatDispatchJobFactory {
    private final AIChatPanel aiChatPanel;

    AddToChatDispatchJobFactory(AIChatPanel aiChatPanel) {
        this.aiChatPanel = aiChatPanel;
    }

    AddToChatDispatchJob create(ResolvedAiRequest request,
                                AiRequestHandleImpl handle,
                                AiRequestTimeoutController timeoutController) {
        return new AddToChatDispatchJob(aiChatPanel, request, handle, timeoutController);
    }
}

package org.freeplane.plugin.ai.chat.request;

public class VisibleAiRequestCallbacksFactory {
    public VisibleAiRequestCallbacksBridge create(AiRequestHandleImpl handle) {
        return new VisibleAiRequestCallbacksBridge(handle);
    }
}

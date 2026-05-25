package org.freeplane.plugin.ai.chat;

class VisibleAiRequestCallbacksFactory {
    VisibleAiRequestCallbacksBridge create(AiRequestHandleImpl handle) {
        return new VisibleAiRequestCallbacksBridge(handle);
    }
}

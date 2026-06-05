package org.freeplane.plugin.ai.chat.request;

public class HiddenAiRequestObserverFactory {
    public HiddenAiRequestObserverBridge create(AiRequestHandleImpl handle) {
        return new HiddenAiRequestObserverBridge(handle);
    }
}

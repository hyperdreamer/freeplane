package org.freeplane.plugin.ai.prompt;

import org.freeplane.plugin.ai.chat.AiRequestHandleImpl;

public class HiddenAiRequestObserverFactory {
    public HiddenAiRequestObserverBridge create(AiRequestHandleImpl handle) {
        return new HiddenAiRequestObserverBridge(handle);
    }
}

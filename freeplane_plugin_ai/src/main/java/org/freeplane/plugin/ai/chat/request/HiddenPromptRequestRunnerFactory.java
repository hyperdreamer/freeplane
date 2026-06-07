package org.freeplane.plugin.ai.chat.request;

public class HiddenPromptRequestRunnerFactory {
    public HiddenPromptRequestRunner create(HiddenPromptRequestRunner.Callbacks callbacks) {
        return new HiddenPromptRequestRunner(callbacks);
    }
}

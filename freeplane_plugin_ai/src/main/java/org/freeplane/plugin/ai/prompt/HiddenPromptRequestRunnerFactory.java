package org.freeplane.plugin.ai.prompt;

public class HiddenPromptRequestRunnerFactory {
    public HiddenPromptRequestRunner create(HiddenPromptRequestRunner.Callbacks callbacks) {
        return new HiddenPromptRequestRunner(callbacks);
    }
}

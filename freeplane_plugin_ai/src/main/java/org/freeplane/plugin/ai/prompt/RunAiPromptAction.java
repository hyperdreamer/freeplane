package org.freeplane.plugin.ai.prompt;

import java.awt.event.ActionEvent;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;

class RunAiPromptAction extends AFreeplaneAction {
    private static final long serialVersionUID = 1L;

    private AiPrompt prompt;
    private final AIChatPanel aiChatPanel;

    RunAiPromptAction(String actionKey, AiPrompt prompt, AIChatPanel aiChatPanel) {
        super(actionKey, prompt == null ? "" : prompt.getName(), null);
        this.prompt = prompt == null ? new AiPrompt() : prompt.copy();
        this.aiChatPanel = aiChatPanel;
    }

    void updatePrompt(AiPrompt prompt) {
        this.prompt = prompt == null ? new AiPrompt() : prompt.copy();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        aiChatPanel.runPrompt(prompt.copy());
    }
}

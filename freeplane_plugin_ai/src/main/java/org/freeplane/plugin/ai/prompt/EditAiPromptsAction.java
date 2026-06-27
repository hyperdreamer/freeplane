package org.freeplane.plugin.ai.prompt;

import java.awt.event.ActionEvent;
import org.freeplane.core.ui.AFreeplaneAction;

class EditAiPromptsAction extends AFreeplaneAction {
    private static final long serialVersionUID = 1L;

    private final AiPromptActionRegistry promptActionRegistry;

    EditAiPromptsAction(AiPromptActionRegistry promptActionRegistry) {
        super("EditAiPromptsAction");
        this.promptActionRegistry = promptActionRegistry;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        promptActionRegistry.openPromptManager();
    }
}

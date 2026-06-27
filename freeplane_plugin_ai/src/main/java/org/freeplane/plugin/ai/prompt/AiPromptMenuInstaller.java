package org.freeplane.plugin.ai.prompt;

import org.freeplane.core.ui.menubuilders.generic.ChildActionEntryRemover;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor.Phase;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;

public final class AiPromptMenuInstaller {
    private AiPromptMenuInstaller() {
    }

    public static AiPromptActionRegistry install(ModeController modeController, AIChatPanel aiChatPanel) {
        AiPromptActionRegistry promptActionRegistry = new AiPromptActionRegistry(
            new AiPromptStore(),
            aiChatPanel,
            () -> modeController.getUserInputListenerFactory().rebuildMenus("aiPromptMenu"));
        modeController.addAction(new EditAiPromptsAction(promptActionRegistry));
        modeController.addUiBuilder(
            Phase.ACTIONS,
            "ai_prompt_actions",
            new AiPromptMenuBuilder(modeController, promptActionRegistry),
            new ChildActionEntryRemover(modeController));
        return promptActionRegistry;
    }
}

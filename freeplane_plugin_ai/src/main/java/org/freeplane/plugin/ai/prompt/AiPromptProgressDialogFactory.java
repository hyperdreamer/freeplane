package org.freeplane.plugin.ai.prompt;

import java.awt.Component;
import javax.swing.Icon;
import org.freeplane.plugin.ai.prompt.ui.AiPromptProgressDialog;

public class AiPromptProgressDialogFactory {
    public AiPromptProgressDialog create(Component owner,
                                         Component locationAnchor,
                                         Icon aiTabIcon,
                                         Icon stopIcon,
                                         String cancelTooltipText,
                                         Runnable cancelAction) {
        return new AiPromptProgressDialog(
            owner,
            locationAnchor,
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            cancelAction);
    }
}

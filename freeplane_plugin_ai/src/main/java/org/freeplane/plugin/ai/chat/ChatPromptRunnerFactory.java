package org.freeplane.plugin.ai.chat;

import dev.langchain4j.memory.ChatMemory;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.freeplane.plugin.ai.code.AttachedEditorProvider;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunnerFactory;

class ChatPromptRunnerFactory {
    private final Icon aiTabIcon;
    private final Icon stopIcon;
    private final String cancelTooltipText;
    private final AvailableMaps availableMaps;
    private final AiPromptRequestComposer aiPromptRequestComposer;
    private final ChatPromptRunner.VisiblePromptChatLauncher visiblePromptChatLauncher;
    private final Supplier<AttachedEditorProvider> attachedEditorProviderSupplier;
    private final HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory;
    private final AiPromptProgressDialogFactory aiPromptProgressDialogFactory;

    ChatPromptRunnerFactory(Icon aiTabIcon,
                            Icon stopIcon,
                            String cancelTooltipText,
                            AvailableMaps availableMaps,
                            AiPromptRequestComposer aiPromptRequestComposer,
                            ChatPromptRunner.VisiblePromptChatLauncher visiblePromptChatLauncher,
                            Supplier<AttachedEditorProvider> attachedEditorProviderSupplier,
                            HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory,
                            AiPromptProgressDialogFactory aiPromptProgressDialogFactory) {
        this.aiTabIcon = aiTabIcon;
        this.stopIcon = stopIcon;
        this.cancelTooltipText = cancelTooltipText;
        this.availableMaps = availableMaps;
        this.aiPromptRequestComposer = aiPromptRequestComposer;
        this.visiblePromptChatLauncher = visiblePromptChatLauncher;
        this.attachedEditorProviderSupplier = attachedEditorProviderSupplier;
        this.hiddenPromptRequestRunnerFactory = hiddenPromptRequestRunnerFactory;
        this.aiPromptProgressDialogFactory = aiPromptProgressDialogFactory;
    }

    ChatPromptRunner createShown(ChatMemory promptChatMemory,
                                 AvailableMaps.MapAccessListener mapAccessListener,
                                 ChatRequestFlow shownRequestFlow,
                                 ChatTokenUsageTracker shownRequestTokenUsageTracker,
                                 LiveChatSessionId sessionId) {
        return new ChatPromptRunner(
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            availableMaps,
            aiPromptRequestComposer,
            visiblePromptChatLauncher,
            attachedEditorProviderSupplier == null ? null : attachedEditorProviderSupplier.get(),
            hiddenPromptRequestRunnerFactory,
            aiPromptProgressDialogFactory,
            promptChatMemory,
            mapAccessListener,
            shownRequestFlow,
            shownRequestTokenUsageTracker,
            sessionId);
    }

    ChatPromptRunner createHidden() {
        return new ChatPromptRunner(
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            availableMaps,
            aiPromptRequestComposer,
            visiblePromptChatLauncher,
            attachedEditorProviderSupplier == null ? null : attachedEditorProviderSupplier.get(),
            hiddenPromptRequestRunnerFactory,
            aiPromptProgressDialogFactory,
            null,
            null,
            null,
            null,
            null);
    }
}

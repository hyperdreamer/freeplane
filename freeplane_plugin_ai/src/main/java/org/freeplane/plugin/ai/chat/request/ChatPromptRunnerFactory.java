package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;

public class ChatPromptRunnerFactory {
    private final Icon aiTabIcon;
    private final Icon stopIcon;
    private final String cancelTooltipText;
    private final AvailableMaps availableMaps;
    private final AiPromptRequestComposer aiPromptRequestComposer;
    private final ChatPromptRunner.VisiblePromptChatLauncher visiblePromptChatLauncher;
    private final Supplier<AiCodeHostService> codeHostServiceSupplier;
    private final Function<LiveChatSessionId, AiCodeHostService> sessionCodeHostServiceProvider;
    private final Supplier<ToolAvailabilityLevel> sharedToolAvailabilitySupplier;
    private final Function<LiveChatSessionId, ToolAvailabilityLevel> sessionToolAvailabilityOverrideProvider;
    private final HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory;
    private final AiPromptProgressDialogFactory aiPromptProgressDialogFactory;

    public ChatPromptRunnerFactory(Icon aiTabIcon,
                            Icon stopIcon,
                            String cancelTooltipText,
                            AvailableMaps availableMaps,
                            AiPromptRequestComposer aiPromptRequestComposer,
                            ChatPromptRunner.VisiblePromptChatLauncher visiblePromptChatLauncher,
                            Supplier<AiCodeHostService> codeHostServiceSupplier,
                            Function<LiveChatSessionId, AiCodeHostService> sessionCodeHostServiceProvider,
                            Supplier<ToolAvailabilityLevel> sharedToolAvailabilitySupplier,
                            Function<LiveChatSessionId, ToolAvailabilityLevel> sessionToolAvailabilityOverrideProvider,
                            HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory,
                            AiPromptProgressDialogFactory aiPromptProgressDialogFactory) {
        this.aiTabIcon = aiTabIcon;
        this.stopIcon = stopIcon;
        this.cancelTooltipText = cancelTooltipText;
        this.availableMaps = availableMaps;
        this.aiPromptRequestComposer = aiPromptRequestComposer;
        this.visiblePromptChatLauncher = visiblePromptChatLauncher;
        this.codeHostServiceSupplier = codeHostServiceSupplier;
        this.sessionCodeHostServiceProvider = sessionCodeHostServiceProvider;
        this.sharedToolAvailabilitySupplier = sharedToolAvailabilitySupplier;
        this.sessionToolAvailabilityOverrideProvider = sessionToolAvailabilityOverrideProvider;
        this.hiddenPromptRequestRunnerFactory = hiddenPromptRequestRunnerFactory;
        this.aiPromptProgressDialogFactory = aiPromptProgressDialogFactory;
    }

    public ChatPromptRunner createShown(ChatMemory promptChatMemory,
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
            sessionId == null || sessionCodeHostServiceProvider == null
                ? (codeHostServiceSupplier == null ? null : codeHostServiceSupplier.get())
                : sessionCodeHostServiceProvider.apply(sessionId),
            sharedToolAvailabilitySupplier,
            sessionId == null || sessionToolAvailabilityOverrideProvider == null
                ? null
                : () -> sessionToolAvailabilityOverrideProvider.apply(sessionId),
            hiddenPromptRequestRunnerFactory,
            aiPromptProgressDialogFactory,
            promptChatMemory,
            mapAccessListener,
            shownRequestFlow,
            shownRequestTokenUsageTracker,
            sessionId);
    }

    public ChatPromptRunner createHidden(LiveChatSessionId sessionId) {
        return new ChatPromptRunner(
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            availableMaps,
            aiPromptRequestComposer,
            visiblePromptChatLauncher,
            sessionId == null || sessionCodeHostServiceProvider == null
                ? (codeHostServiceSupplier == null ? null : codeHostServiceSupplier.get())
                : sessionCodeHostServiceProvider.apply(sessionId),
            sharedToolAvailabilitySupplier,
            null,
            hiddenPromptRequestRunnerFactory,
            aiPromptProgressDialogFactory,
            null,
            null,
            null,
            null,
            null);
    }
}

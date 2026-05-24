package org.freeplane.plugin.ai.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.awt.Component;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunner;
import org.freeplane.plugin.ai.prompt.ui.AiPromptProgressDialog;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

class ChatPromptRunner {
    interface VisiblePromptChatLauncher {
        void openPromptChat(ChatMemory promptChatMemory,
                            AIChatService promptService,
                            String preparedMessage,
                            String promptDisplayName,
                            String selectedModelOverride,
                            ChatToolAvailability toolAvailabilityOverride);
    }

    private final Icon aiTabIcon;
    private final Icon stopIcon;
    private final String cancelTooltipText;
    private final Runnable inputStateUpdater;
    private final AvailableMaps availableMaps;
    private final AiPromptRequestComposer aiPromptRequestComposer;
    private final PromptToolSelectionResolver promptToolSelectionResolver;
    private final LiveChatController liveChatController;
    private final ChatRequestFlow chatRequestFlow;
    private final ChatTokenUsageTracker chatTokenUsageTracker;
    private final Supplier<ChatMemory> chatMemoryFactory;
    private final Function<String, String> configurationErrorMessageProvider;
    private final BiConsumer<String, Boolean> userNotifier;
    private final VisiblePromptChatLauncher visiblePromptChatLauncher;
    private final HiddenPromptRequestRunner hiddenPromptRequestRunner;
    private AiPromptProgressDialog hiddenPromptProgressDialog;
    private Component hiddenPromptOwnerComponent;

    ChatPromptRunner(Icon aiTabIcon,
                     Icon stopIcon,
                     String cancelTooltipText,
                     Runnable inputStateUpdater,
                     AvailableMaps availableMaps,
                     AiPromptRequestComposer aiPromptRequestComposer,
                     PromptToolSelectionResolver promptToolSelectionResolver,
                     LiveChatController liveChatController,
                     ChatRequestFlow chatRequestFlow,
                     ChatTokenUsageTracker chatTokenUsageTracker,
                     Supplier<ChatMemory> chatMemoryFactory,
                     Function<String, String> configurationErrorMessageProvider,
                     BiConsumer<String, Boolean> userNotifier,
                     VisiblePromptChatLauncher visiblePromptChatLauncher) {
        this.aiTabIcon = aiTabIcon;
        this.stopIcon = stopIcon;
        this.cancelTooltipText = cancelTooltipText;
        this.inputStateUpdater = inputStateUpdater;
        this.availableMaps = availableMaps;
        this.aiPromptRequestComposer = aiPromptRequestComposer;
        this.promptToolSelectionResolver = promptToolSelectionResolver;
        this.liveChatController = liveChatController;
        this.chatRequestFlow = chatRequestFlow;
        this.chatTokenUsageTracker = chatTokenUsageTracker;
        this.chatMemoryFactory = chatMemoryFactory;
        this.configurationErrorMessageProvider = configurationErrorMessageProvider;
        this.userNotifier = userNotifier;
        this.visiblePromptChatLauncher = visiblePromptChatLauncher;
        this.hiddenPromptRequestRunner = new HiddenPromptRequestRunner(new HiddenPromptRequestRunner.Callbacks() {
            @Override
            public void onRequestStarted(String promptName) {
                SwingUtilities.invokeLater(() -> {
                    inputStateUpdater.run();
                    showHiddenPromptProgressDialog(promptName);
                });
            }

            @Override
            public void onRequestFinished(String promptName) {
                SwingUtilities.invokeLater(() -> {
                    closeHiddenPromptProgressDialog();
                    inputStateUpdater.run();
                });
            }

            @Override
            public void onRequestFailed(String promptName, String errorMessage) {
                UITools.errorMessage(promptFailureMessage(promptName, errorMessage));
            }
        });
    }

    boolean isRequestActive() {
        return hiddenPromptRequestRunner.isRequestActive();
    }

    void cancelActiveRequest() {
        hiddenPromptRequestRunner.cancelActiveRequest();
    }

    void runPrompt(AiPrompt prompt, Component owner) {
        if (prompt == null) {
            return;
        }
        if (chatRequestFlow.isRequestActive() || hiddenPromptRequestRunner.isRequestActive()) {
            userNotifier.accept(TextUtils.getText("ai_prompt_request_active"), false);
            return;
        }
        String selectedModelOverride = normalizeSelectionValue(prompt.getModelSelectionValue());
        String toolAvailabilitySelectionValue = prompt.getToolAvailabilitySelectionValue();
        ChatToolAvailability resolvedToolAvailability =
            promptToolSelectionResolver.resolveEffectiveToolAvailability(toolAvailabilitySelectionValue);
        ChatToolAvailability toolAvailabilityOverride =
            promptToolSelectionResolver.resolveShownChatOverride(toolAvailabilitySelectionValue);
        final String preparedMessage;
        try {
            preparedMessage = aiPromptRequestComposer.compose(prompt, resolvedToolAvailability);
        } catch (RuntimeException error) {
            userNotifier.accept(error.getMessage(), true);
            return;
        }
        ChatMemory promptChatMemory = chatMemoryFactory.get();
        if (prompt.isShowInChat()) {
            AIChatService promptService = createPromptChatService(
                promptChatMemory,
                liveChatController.mapAccessListener(),
                chatRequestFlow::onToolCallSummary,
                chatRequestFlow.cancellationSupplier(),
                chatRequestFlow::onProviderUsage,
                chatTokenUsageTracker,
                selectedModelOverride,
                resolvedToolAvailability);
            if (promptService == null) {
                return;
            }
            visiblePromptChatLauncher.openPromptChat(
                promptChatMemory,
                promptService,
                preparedMessage,
                promptSessionDisplayName(prompt.getName()),
                selectedModelOverride,
                toolAvailabilityOverride);
        } else {
            AIChatService promptService = createPromptChatService(
                promptChatMemory,
                null,
                null,
                hiddenPromptRequestRunner.cancellationSupplier(),
                null,
                new ChatTokenUsageTracker(totals -> {
                }),
                selectedModelOverride,
                resolvedToolAvailability);
            if (promptService == null) {
                return;
            }
            hiddenPromptOwnerComponent = owner;
            hiddenPromptRequestRunner.submit(prompt.getName(), promptService, preparedMessage);
        }
    }

    private AIChatService createPromptChatService(ChatMemory promptChatMemory,
                                                  AvailableMaps.MapAccessListener mapAccessListener,
                                                  ToolCallSummaryHandler toolCallSummaryHandler,
                                                  Supplier<Boolean> cancellationSupplier,
                                                  Consumer<TokenUsage> tokenUsageConsumer,
                                                  ChatTokenUsageTracker tokenUsageTracker,
                                                  String selectedModelOverride,
                                                  ChatToolAvailability toolAvailability) {
        String configurationError = configurationErrorMessageProvider.apply(selectedModelOverride);
        if (configurationError != null) {
            userNotifier.accept(configurationError, true);
            return null;
        }
        return AIChatServiceFactory.createService(new AIToolSetBuilder()
                .toolCallSummaryHandler(toolCallSummaryHandler)
                .availableMaps(availableMaps)
                .mapAccessListener(mapAccessListener)
                .build(),
            promptChatMemory,
            tokenUsageTracker,
            toolCallSummaryHandler,
            cancellationSupplier,
            tokenUsageConsumer,
            () -> toolAvailability,
            selectedModelOverride);
    }

    private String normalizeSelectionValue(String selectionValue) {
        if (selectionValue == null) {
            return null;
        }
        String normalized = selectionValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String promptFailureMessage(String promptName, String errorMessage) {
        String safePromptName = promptName == null ? "" : promptName.trim();
        String safeErrorMessage = errorMessage == null ? "" : errorMessage.trim();
        return TextUtils.format("ai_prompt_hidden_failed",
            safePromptName,
            safeErrorMessage.isEmpty() ? "Unknown error" : safeErrorMessage);
    }

    private String promptSessionDisplayName(String promptName) {
        String safePromptName = promptName == null ? "" : promptName.trim();
        if (safePromptName.isEmpty()) {
            safePromptName = TextUtils.getText("ai_prompt_untitled");
        }
        return TextUtils.getText("ai_prompt_session_prefix") + safePromptName;
    }

    private void showHiddenPromptProgressDialog(String promptName) {
        Component owner = hiddenPromptOwnerComponent != null ? hiddenPromptOwnerComponent : UITools.getCurrentRootComponent();
        Component locationAnchor = hiddenPromptOwnerComponent == null
            ? Controller.getCurrentController().getMapViewManager().getSelectedComponent()
            : null;
        if (hiddenPromptProgressDialog != null) {
            hiddenPromptProgressDialog.closeDialog();
        }
        hiddenPromptProgressDialog = new AiPromptProgressDialog(
            owner,
            locationAnchor,
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            this::cancelActiveRequest);
        hiddenPromptProgressDialog.showPrompt(promptName);
    }

    private void closeHiddenPromptProgressDialog() {
        if (hiddenPromptProgressDialog != null) {
            hiddenPromptProgressDialog.closeDialog();
            hiddenPromptProgressDialog = null;
        }
        hiddenPromptOwnerComponent = null;
    }
}

package org.freeplane.plugin.ai.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.code.AttachedEditorProvider;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.HiddenAiRequestObserverBridge;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunner;
import org.freeplane.plugin.ai.prompt.HiddenPromptRequestRunnerFactory;
import org.freeplane.plugin.ai.prompt.ui.AiPromptProgressDialog;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

class ChatPromptRunner {
    interface VisiblePromptChatLauncher {
        void openPromptChat(LiveChatSessionId sessionId,
                            AIChatService promptService,
                            String preparedMessage,
                            ChatRequestFlow requestFlow,
                            ChatTokenUsageTracker requestTokenUsageTracker,
                            VisiblePromptRequestCallbacks requestCallbacks);
    }

    interface VisiblePromptRequestCallbacks {
        void onResponseAppended(String response);
        void onFailed(String userText, String errorMessage);
        void onCancelled();
    }

    private final Icon aiTabIcon;
    private final Icon stopIcon;
    private final String cancelTooltipText;
    private final AvailableMaps availableMaps;
    private final AiPromptRequestComposer aiPromptRequestComposer;
    private final VisiblePromptChatLauncher visiblePromptChatLauncher;
    private final AttachedEditorProvider attachedEditorProvider;
    private final HiddenPromptRequestRunner hiddenPromptRequestRunner;
    private final AiPromptProgressDialogFactory aiPromptProgressDialogFactory;
    private final ChatMemory shownPromptChatMemory;
    private final AvailableMaps.MapAccessListener shownMapAccessListener;
    private final ChatRequestFlow shownRequestFlow;
    private final ChatTokenUsageTracker shownRequestTokenUsageTracker;
    private final LiveChatSessionId shownSessionId;
    private AiPromptProgressDialog hiddenPromptProgressDialog;
    private Component hiddenPromptOwnerComponent;
    private boolean showHiddenPromptProgressDialog = true;
    private HiddenAiRequestObserverBridge hiddenRequestObserver;

    ChatPromptRunner(Icon aiTabIcon,
                     Icon stopIcon,
                     String cancelTooltipText,
                     AvailableMaps availableMaps,
                     AiPromptRequestComposer aiPromptRequestComposer,
                     VisiblePromptChatLauncher visiblePromptChatLauncher,
                     AttachedEditorProvider attachedEditorProvider,
                     HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory,
                     AiPromptProgressDialogFactory aiPromptProgressDialogFactory,
                     ChatMemory shownPromptChatMemory,
                     AvailableMaps.MapAccessListener shownMapAccessListener,
                     ChatRequestFlow shownRequestFlow,
                     ChatTokenUsageTracker shownRequestTokenUsageTracker,
                     LiveChatSessionId shownSessionId) {
        this.aiTabIcon = aiTabIcon;
        this.stopIcon = stopIcon;
        this.cancelTooltipText = cancelTooltipText;
        this.availableMaps = availableMaps;
        this.aiPromptRequestComposer = aiPromptRequestComposer;
        this.visiblePromptChatLauncher = visiblePromptChatLauncher;
        this.attachedEditorProvider = attachedEditorProvider;
        this.aiPromptProgressDialogFactory = aiPromptProgressDialogFactory;
        this.shownPromptChatMemory = shownPromptChatMemory;
        this.shownMapAccessListener = shownMapAccessListener;
        this.shownRequestFlow = shownRequestFlow;
        this.shownRequestTokenUsageTracker = shownRequestTokenUsageTracker;
        this.shownSessionId = shownSessionId;
        this.hiddenPromptRequestRunner = hiddenPromptRequestRunnerFactory.create(new HiddenPromptRequestRunner.Callbacks() {
            @Override
            public void onRequestStarted(String promptName) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (showHiddenPromptProgressDialog) {
                            showHiddenPromptProgressDialog(promptName);
                        }
                    }
                });
            }

            @Override
            public void onRequestFinished(String promptName) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        closeHiddenPromptProgressDialog();
                        hiddenRequestObserver = null;
                        showHiddenPromptProgressDialog = true;
                    }
                });
            }

            @Override
            public void onRequestSucceeded(String promptName, String response) {
                if (hiddenRequestObserver != null) {
                    hiddenRequestObserver.onSucceeded(response);
                }
            }

            @Override
            public void onRequestCancelled(String promptName) {
                if (hiddenRequestObserver != null) {
                    hiddenRequestObserver.onCancelled();
                }
            }

            @Override
            public void onRequestFailed(String promptName, String errorMessage) {
                if (hiddenRequestObserver != null) {
                    hiddenRequestObserver.onFailed(errorMessage);
                    return;
                }
                UITools.errorMessage(promptFailureMessage(promptName, errorMessage));
            }
        });
    }

    boolean isHiddenRequestActive() {
        return hiddenPromptRequestRunner.isRequestActive();
    }

    HiddenPromptRequestRunner hiddenPromptRequestRunner() {
        return hiddenPromptRequestRunner;
    }

    boolean startShownPrompt(String promptText,
                             String selectedModelOverride,
                             ChatToolAvailability resolvedToolAvailability,
                             SelectionIdentifiersResponse selectionOverride,
                             VisiblePromptRequestCallbacks requestCallbacks) {
        if (shownPromptChatMemory == null || shownMapAccessListener == null
            || shownRequestFlow == null || shownRequestTokenUsageTracker == null
            || shownSessionId == null) {
            throw new IllegalStateException("Shown prompt context is not configured.");
        }
        final String preparedMessage;
        try {
            preparedMessage = aiPromptRequestComposer.compose(promptText, resolvedToolAvailability, selectionOverride);
        } catch (RuntimeException error) {
            return false;
        }
        AIChatService promptService = createPromptChatService(
            shownPromptChatMemory,
            shownMapAccessListener,
            shownRequestFlow::onToolCallSummary,
            shownRequestFlow.cancellationSupplier(),
            shownRequestFlow::onProviderUsage,
            shownRequestTokenUsageTracker,
            selectedModelOverride,
            resolvedToolAvailability);
        if (promptService == null) {
            return false;
        }
        visiblePromptChatLauncher.openPromptChat(
            shownSessionId,
            promptService,
            preparedMessage,
            shownRequestFlow,
            shownRequestTokenUsageTracker,
            requestCallbacks);
        return true;
    }

    boolean submitHiddenRequest(String requestName,
                                String promptText,
                                String selectedModelOverride,
                                ChatToolAvailability resolvedToolAvailability,
                                SelectionIdentifiersResponse selectionOverride,
                                Component owner,
                                boolean showProgressDialog,
                                HiddenAiRequestObserverBridge observer) {
        final String preparedMessage;
        try {
            preparedMessage = aiPromptRequestComposer.compose(promptText, resolvedToolAvailability, selectionOverride);
        } catch (RuntimeException error) {
            return false;
        }
        AIChatService promptService = createPromptChatService(
            AssistantProfileChatMemory.withMaxTokens(new ChatMemorySettings().getMaximumTokenCount()),
            null,
            null,
            hiddenPromptRequestRunner.cancellationSupplier(),
            null,
            new ChatTokenUsageTracker(new Consumer<ChatUsageTotals>() {
                @Override
                public void accept(ChatUsageTotals totals) {
                }
            }),
            selectedModelOverride,
            resolvedToolAvailability);
        if (promptService == null) {
            return false;
        }
        hiddenPromptOwnerComponent = owner;
        hiddenRequestObserver = observer;
        showHiddenPromptProgressDialog = showProgressDialog;
        hiddenPromptRequestRunner.submit(requestName, promptService, preparedMessage);
        return true;
    }

    private AIChatService createPromptChatService(ChatMemory promptChatMemory,
                                                  AvailableMaps.MapAccessListener mapAccessListener,
                                                  ToolCallSummaryHandler toolCallSummaryHandler,
                                                  Supplier<Boolean> cancellationSupplier,
                                                  Consumer<TokenUsage> tokenUsageConsumer,
                                                  ChatTokenUsageTracker tokenUsageTracker,
                                                  String selectedModelOverride,
                                                  ChatToolAvailability toolAvailability) {
        AIToolSetBuilder toolSetBuilder = new AIToolSetBuilder()
            .toolCallSummaryHandler(toolCallSummaryHandler)
            .availableMaps(availableMaps)
            .mapAccessListener(mapAccessListener)
            .attachedEditorProvider(attachedEditorProvider);
        List<Object> toolObjects = toolSetBuilder.buildToolObjects();
        return AIChatServiceFactory.createService(
            (org.freeplane.plugin.ai.tools.AIToolSet) toolObjects.get(0),
            toolObjects,
            promptChatMemory,
            tokenUsageTracker,
            toolCallSummaryHandler,
            cancellationSupplier,
            tokenUsageConsumer,
            new Supplier<ChatToolAvailability>() {
                @Override
                public ChatToolAvailability get() {
                    return toolAvailability;
                }
            },
            selectedModelOverride);
    }

    private String promptFailureMessage(String promptName, String errorMessage) {
        String safePromptName = promptName == null ? "" : promptName.trim();
        String safeErrorMessage = errorMessage == null ? "" : errorMessage.trim();
        return TextUtils.format("ai_prompt_hidden_failed",
            safePromptName,
            safeErrorMessage.isEmpty() ? "Unknown error" : safeErrorMessage);
    }

    private void showHiddenPromptProgressDialog(String promptName) {
        Component owner = hiddenPromptOwnerComponent != null ? hiddenPromptOwnerComponent : UITools.getCurrentRootComponent();
        Component locationAnchor = hiddenPromptOwnerComponent == null
            ? Controller.getCurrentController().getMapViewManager().getSelectedComponent()
            : null;
        if (hiddenPromptProgressDialog != null) {
            hiddenPromptProgressDialog.closeDialog();
        }
        hiddenPromptProgressDialog = aiPromptProgressDialogFactory.create(
            owner,
            locationAnchor,
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            new Runnable() {
                @Override
                public void run() {
                    hiddenPromptRequestRunner.cancelActiveRequest();
                }
            });
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

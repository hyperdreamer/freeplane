package org.freeplane.plugin.ai.chat.request;

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
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemorySettings;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.memory.ChatUsageTotals;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingSettings;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.prompt.ui.AiPromptProgressDialog;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

public class ChatPromptRunner {
    public interface VisiblePromptChatLauncher {
        void openPromptChat(LiveChatSessionId sessionId,
                            AIChatService promptService,
                            String preparedMessage,
                            ChatRequestFlow requestFlow,
                            ChatTokenUsageTracker requestTokenUsageTracker,
                            VisiblePromptRequestCallbacks requestCallbacks,
                            AssistantProfileSwitchMessage requestedProfileMessage);
    }

    public interface VisiblePromptRequestCallbacks {
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
    private final AiCodeHostService codeHostService;
    private final Supplier<ToolAvailabilityLevel> sharedToolAvailabilitySupplier;
    private final Supplier<ToolAvailabilityLevel> shownSessionToolAvailabilityOverrideSupplier;
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
                     AiCodeHostService codeHostService,
                     Supplier<ToolAvailabilityLevel> sharedToolAvailabilitySupplier,
                     Supplier<ToolAvailabilityLevel> shownSessionToolAvailabilityOverrideSupplier,
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
        this.codeHostService = codeHostService;
        this.sharedToolAvailabilitySupplier = sharedToolAvailabilitySupplier;
        this.shownSessionToolAvailabilityOverrideSupplier = shownSessionToolAvailabilityOverrideSupplier;
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

    public HiddenPromptRequestRunner hiddenPromptRequestRunner() {
        return hiddenPromptRequestRunner;
    }

    public boolean startShownPrompt(String promptText,
                             AIModelConfiguration modelConfigurationOverride,
                             ToolAvailabilityLevel resolvedToolAvailability,
                             SelectionIdentifiersResponse selectionOverride,
                             VisiblePromptRequestCallbacks requestCallbacks) {
        return startShownPrompt(promptText, modelConfigurationOverride, resolvedToolAvailability, selectionOverride,
            requestCallbacks, null);
    }

    public boolean startShownPrompt(String promptText,
                             AIModelConfiguration modelConfigurationOverride,
                             ToolAvailabilityLevel resolvedToolAvailability,
                             SelectionIdentifiersResponse selectionOverride,
                             VisiblePromptRequestCallbacks requestCallbacks,
                             AssistantProfileSwitchMessage requestedProfileMessage) {
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
            modelConfigurationWithProfileFallback(modelConfigurationOverride, requestedProfileMessage),
            resolvedToolAvailability,
            capturedSystemMessage(shownPromptChatMemory),
            isSystemMessageExact(shownPromptChatMemory),
            false);
        if (promptService == null) {
            return false;
        }
        visiblePromptChatLauncher.openPromptChat(
            shownSessionId,
            promptService,
            preparedMessage,
            shownRequestFlow,
            shownRequestTokenUsageTracker,
            requestCallbacks,
            requestedProfileMessage);
        return true;
    }

    public boolean submitHiddenRequest(String requestName,
                                String promptText,
                                AIModelConfiguration modelConfigurationOverride,
                                ToolAvailabilityLevel resolvedToolAvailability,
                                SelectionIdentifiersResponse selectionOverride,
                                Component owner,
                                boolean showProgressDialog,
                                HiddenAiRequestObserverBridge observer,
                                String systemMessage,
                                boolean isSystemMessageExact,
                                boolean hiddenRequest,
                                AssistantProfileSwitchMessage requestedProfileMessage) {
        final String preparedMessage;
        try {
            preparedMessage = aiPromptRequestComposer.compose(promptText, resolvedToolAvailability, selectionOverride);
        } catch (RuntimeException error) {
            return false;
        }
        ChatMemory hiddenChatMemory = AssistantProfileChatMemory.withMaxTokens(
            new ChatMemorySettings().getMaximumTokenCount());
        if (requestedProfileMessage != null) {
            hiddenChatMemory.add(requestedProfileMessage);
        }
        AIChatService promptService = createPromptChatService(
            hiddenChatMemory,
            null,
            null,
            hiddenPromptRequestRunner.cancellationSupplier(),
            null,
            new ChatTokenUsageTracker(new Consumer<ChatUsageTotals>() {
                @Override
                public void accept(ChatUsageTotals totals) {
                }
            }),
            modelConfigurationWithProfileFallback(modelConfigurationOverride, requestedProfileMessage),
            resolvedToolAvailability,
            systemMessage,
            isSystemMessageExact,
            hiddenRequest);
        if (promptService == null) {
            return false;
        }
        hiddenPromptOwnerComponent = owner;
        hiddenRequestObserver = observer;
        showHiddenPromptProgressDialog = showProgressDialog;
        hiddenPromptRequestRunner.submit(requestName, promptService, preparedMessage);
        return true;
    }

    private AIModelConfiguration modelConfigurationWithProfileFallback(
        AIModelConfiguration modelConfigurationOverride,
        AssistantProfileSwitchMessage requestedProfileMessage) {
        AIModelConfiguration profileModelConfiguration = requestedProfileMessage == null
            ? null
            : requestedProfileMessage.getModelConfiguration();
        if (modelConfigurationOverride == null) {
            return profileModelConfiguration;
        }
        return modelConfigurationOverride.withFallback(profileModelConfiguration);
    }

    private AIChatService createPromptChatService(ChatMemory promptChatMemory,
                                                  AvailableMaps.MapAccessListener mapAccessListener,
                                                  ToolCallSummaryHandler toolCallSummaryHandler,
                                                  Supplier<Boolean> cancellationSupplier,
                                                  Consumer<TokenUsage> tokenUsageConsumer,
                                                  ChatTokenUsageTracker tokenUsageTracker,
                                                  AIModelConfiguration modelConfigurationOverride,
                                                  ToolAvailabilityLevel toolAvailability,
                                                  String systemMessage,
                                                  boolean isSystemMessageExact,
                                                  boolean hiddenRequest) {
        AIToolSetBuilder toolSetBuilder = new AIToolSetBuilder(availableMaps)
            .toolCallSummaryHandler(toolCallSummaryHandler)
            .mapAccessListener(mapAccessListener)
            .codeHostService(codeHostService)
            .aiCodeOperationAuthorizer(new AiCodeOperationAuthorizer(
                org.freeplane.plugin.ai.tools.utilities.ToolCaller.CHAT,
                toolAvailability != null ? () -> toolAvailability : sharedToolAvailabilitySupplier,
                shownSessionToolAvailabilityOverrideSupplier,
                () -> Boolean.valueOf(new FormulaEditingSettings().isEnabled()),
                codeHostService));
        List<Object> toolObjects = toolSetBuilder.buildToolObjects();
        Supplier<ToolAvailabilityLevel> requestToolAvailabilitySupplier = new Supplier<ToolAvailabilityLevel>() {
            @Override
            public ToolAvailabilityLevel get() {
                return toolAvailability;
            }
        };
        if (systemMessage == null && !hiddenRequest) {
            return AIChatServiceFactory.createService(
                (org.freeplane.plugin.ai.tools.AIToolSet) toolObjects.get(0),
                toolObjects,
                promptChatMemory,
                tokenUsageTracker,
                toolCallSummaryHandler,
                cancellationSupplier,
                tokenUsageConsumer,
                requestToolAvailabilitySupplier,
                modelConfigurationOverride);
        }
        return AIChatServiceFactory.createService(
            (org.freeplane.plugin.ai.tools.AIToolSet) toolObjects.get(0),
            toolObjects,
            promptChatMemory,
            tokenUsageTracker,
            toolCallSummaryHandler,
            cancellationSupplier,
            tokenUsageConsumer,
            requestToolAvailabilitySupplier,
            modelConfigurationOverride,
            systemMessage,
            isSystemMessageExact,
            hiddenRequest);
    }

    private String capturedSystemMessage(ChatMemory chatMemory) {
        if (chatMemory instanceof AssistantProfileChatMemory) {
            String captured = ((AssistantProfileChatMemory) chatMemory).capturedSystemMessage();
            return captured == null ? "" : captured;
        }
        return null;
    }

    private boolean isSystemMessageExact(ChatMemory chatMemory) {
        return chatMemory instanceof AssistantProfileChatMemory
            && ((AssistantProfileChatMemory) chatMemory).isSystemMessageExact();
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

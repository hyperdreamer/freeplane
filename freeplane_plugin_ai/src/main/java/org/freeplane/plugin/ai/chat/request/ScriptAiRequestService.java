package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import javax.swing.SwingUtilities;
import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestOptions;
import org.freeplane.api.ai.AiRequestRejectedException;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestService;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptActionRegistry;

public class ScriptAiRequestService implements AiRequestService {
    interface UiDispatcher {
        void dispatch(Runnable runnable);
    }

    interface RequestStarter {
        void start(ResolvedAiRequest request, AiRequestHandleImpl handle);
    }

    interface SavedPromptResolver {
        AiPrompt findSavedPromptByName(String promptName);
    }

    private final UiDispatcher uiDispatcher;
    private final RequestStarter requestStarter;
    private final SavedPromptResolver savedPromptResolver;

    public ScriptAiRequestService(AIChatPanel aiChatPanel, AiPromptActionRegistry promptActionRegistry) {
        this(new RequestStarter() {
                @Override
                public void start(ResolvedAiRequest request, AiRequestHandleImpl handle) {
                    aiChatPanel.askAi(request, handle);
                }
            },
            new SavedPromptResolver() {
                @Override
                public AiPrompt findSavedPromptByName(String promptName) {
                    return promptActionRegistry.findSavedPromptByName(promptName);
                }
            },
            ScriptAiRequestService::dispatchOnUiThread);
    }

    ScriptAiRequestService(RequestStarter requestStarter,
                           SavedPromptResolver savedPromptResolver,
                           UiDispatcher uiDispatcher) {
        this.requestStarter = Objects.requireNonNull(requestStarter, "requestStarter");
        this.savedPromptResolver = Objects.requireNonNull(savedPromptResolver, "savedPromptResolver");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
    }

    @Override
    public AiRequestHandle askAi(String prompt, AiRequestOptions options, AiRequestCallback callback) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(callback, "callback");
        ResolvedAiRequest request = new ResolvedAiRequest(
            prompt,
            null,
            options.getTimeout(),
            requireMode(options),
            withDefaultModelSelection(options.getModelConfiguration()),
            options.getToolAvailability() == null ? AiToolAvailability.CURRENT : options.getToolAvailability(),
            options.getSelectionOverride(),
            options.getSystemMessage(),
            options.isSystemMessageExact(),
            options.getProfileName(),
            options.getProfileMessage());
        return dispatchAcceptedRequest(request, callback);
    }

    @Override
    public AiRequestHandle runAiPrompt(String promptName, Duration timeout, AiRequestCallback callback) {
        Objects.requireNonNull(callback, "callback");
        return runAiPrompt(
            promptName,
            AiRequestOptions.builder().timeout(timeout).build(),
            callback);
    }

    @Override
    public AiRequestHandle runAiPrompt(String promptName, AiRequestOptions options, AiRequestCallback callback) {
        String normalizedPromptName = normalizeRequiredPromptName(promptName);
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(callback, "callback");
        AiPrompt savedPrompt = savedPromptResolver.findSavedPromptByName(normalizedPromptName);
        if (savedPrompt == null) {
            throw new AiRequestRejectedException(
                AiRequestStatus.PROMPT_NOT_FOUND,
                "Saved AI prompt not found: " + normalizedPromptName);
        }
        AiRequestHandleImpl handle = createHandle(callback);
        SavedPromptResolution resolution = resolveSavedPromptRequest(savedPrompt, options);
        if (resolution.configurationErrorDetail != null) {
            handle.complete(new AiRequestResult(AiRequestStatus.CONFIGURATION_ERROR, null,
                resolution.configurationErrorDetail));
            return handle;
        }
        uiDispatcher.dispatch(() -> requestStarter.start(resolution.request, handle));
        return handle;
    }

    private AiRequestHandle dispatchAcceptedRequest(ResolvedAiRequest request, AiRequestCallback callback) {
        AiRequestHandleImpl handle = createHandle(callback);
        uiDispatcher.dispatch(() -> requestStarter.start(request, handle));
        return handle;
    }

    private AiRequestHandleImpl createHandle(AiRequestCallback callback) {
        return new AiRequestHandleImpl(uiDispatcher::dispatch, callback);
    }

    private AiRequestMode requireMode(AiRequestOptions options) {
        if (options.getMode() == null) {
            throw new IllegalArgumentException("options.mode must not be null for askAi");
        }
        return options.getMode();
    }

    private String normalizeRequiredPromptName(String promptName) {
        if (promptName == null) {
            throw new IllegalArgumentException("promptName must not be null or blank");
        }
        String normalizedPromptName = promptName.trim();
        if (normalizedPromptName.isEmpty()) {
            throw new IllegalArgumentException("promptName must not be null or blank");
        }
        return normalizedPromptName;
    }

    private SavedPromptResolution resolveSavedPromptRequest(AiPrompt savedPrompt, AiRequestOptions options) {
        ModelConfigurationResolution modelConfigurationResolution = resolveSavedPromptModelConfiguration(
            savedPrompt,
            options.getModelConfiguration() == null || options.getModelConfiguration().getModelSelection() == null);
        if (modelConfigurationResolution.configurationErrorDetail != null) {
            return SavedPromptResolution.configurationError(modelConfigurationResolution.configurationErrorDetail);
        }
        AiModelConfiguration resolvedModelConfiguration = options.getModelConfiguration() == null
            ? modelConfigurationResolution.modelConfiguration
            : withDefaultModelSelection(mergeModelConfigurations(
                options.getModelConfiguration(),
                modelConfigurationResolution.modelConfiguration));
        AiToolAvailability toolAvailability = options.getToolAvailability() != null
            ? options.getToolAvailability()
            : resolveSavedPromptToolAvailability(savedPrompt);
        AiRequestMode mode = options.getMode() != null
            ? options.getMode()
            : (savedPrompt.isShowInChat() ? AiRequestMode.SHOW_IN_NEW_CHAT : AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG);
        return SavedPromptResolution.success(new ResolvedAiRequest(
            savedPrompt.getPrompt(),
            savedPrompt.getName(),
            options.getTimeout(),
            mode,
            resolvedModelConfiguration,
            toolAvailability,
            options.getSelectionOverride(),
            options.getSystemMessage(),
            options.isSystemMessageExact(),
            options.getProfileName(),
            options.getProfileMessage()));
    }

    private ModelConfigurationResolution resolveSavedPromptModelConfiguration(AiPrompt savedPrompt,
                                                                              boolean requireValidModelSelection) {
        org.freeplane.plugin.ai.model.AIModelConfiguration savedConfiguration =
            savedPrompt.getModelConfiguration();
        String selectionValue = normalizeOptional(savedPrompt.getModelSelectionValue());
        if (requireValidModelSelection
            && selectionValue != null
            && (savedConfiguration == null || savedConfiguration.getModelSelection() == null)) {
            return ModelConfigurationResolution.configurationError(
                "Malformed saved AI prompt model selection for prompt '" + safePromptName(savedPrompt) + "'.");
        }
        if (savedConfiguration == null) {
            return ModelConfigurationResolution.success(defaultModelConfiguration());
        }
        AiModelConfiguration publicConfiguration = toPublicModelConfiguration(savedConfiguration);
        return ModelConfigurationResolution.success(withDefaultModelSelection(publicConfiguration));
    }

    private AiModelConfiguration mergeModelConfigurations(AiModelConfiguration override,
                                                          AiModelConfiguration fallback) {
        if (override == null) {
            return fallback;
        }
        if (fallback == null) {
            return override;
        }
        return AiModelConfiguration.builder()
            .modelSelection(override.getModelSelection() != null
                ? override.getModelSelection()
                : fallback.getModelSelection())
            .thinkingEffort(override.getThinkingEffort() != null
                ? override.getThinkingEffort()
                : fallback.getThinkingEffort())
            .temperature(override.getTemperature() != null
                ? override.getTemperature()
                : fallback.getTemperature())
            .build();
    }

    private AiModelConfiguration toPublicModelConfiguration(
        org.freeplane.plugin.ai.model.AIModelConfiguration modelConfiguration) {
        AiModelConfiguration.Builder builder = AiModelConfiguration.builder();
        if (modelConfiguration.getModelSelection() != null) {
            org.freeplane.plugin.ai.model.AIModelSelection selection = modelConfiguration.getModelSelection();
            builder.modelSelection(AiModelSelection.explicit(selection.getProviderName(), selection.getModelName()));
        }
        builder.thinkingEffort(modelConfiguration.getThinkingEffort());
        builder.temperature(modelConfiguration.getTemperature());
        return builder.build();
    }

    private AiModelConfiguration withDefaultModelSelection(AiModelConfiguration modelConfiguration) {
        if (modelConfiguration == null) {
            return defaultModelConfiguration();
        }
        if (modelConfiguration.getModelSelection() != null) {
            return modelConfiguration;
        }
        return AiModelConfiguration.builder()
            .modelSelection(AiModelSelection.defaultModel())
            .thinkingEffort(modelConfiguration.getThinkingEffort())
            .temperature(modelConfiguration.getTemperature())
            .build();
    }

    private AiModelConfiguration defaultModelConfiguration() {
        return AiModelConfiguration.builder()
            .modelSelection(AiModelSelection.defaultModel())
            .build();
    }

    private AiToolAvailability resolveSavedPromptToolAvailability(AiPrompt savedPrompt) {
        String selectionValue = normalizeOptional(savedPrompt.getToolAvailabilitySelectionValue());
        if (selectionValue == null) {
            return AiToolAvailability.CURRENT;
        }
        switch (selectionValue.toLowerCase(Locale.ROOT)) {
            case "disabled":
                return AiToolAvailability.DISABLED;
            case "reading":
                return AiToolAvailability.READING;
            case "script_execution":
                return AiToolAvailability.SCRIPT_EXECUTION;
            case "editing":
            default:
                return AiToolAvailability.EDITING;
        }
    }

    private String safePromptName(AiPrompt savedPrompt) {
        return savedPrompt == null || savedPrompt.getName() == null || savedPrompt.getName().trim().isEmpty()
            ? ""
            : savedPrompt.getName().trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void dispatchOnUiThread(Runnable runnable) {
        Controller controller = Controller.getCurrentController();
        if (controller != null && controller.getMainThreadExecutorService() != null) {
            controller.getMainThreadExecutorService().execute(runnable);
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        }
        else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private static class SavedPromptResolution {
        private final ResolvedAiRequest request;
        private final String configurationErrorDetail;

        private SavedPromptResolution(ResolvedAiRequest request, String configurationErrorDetail) {
            this.request = request;
            this.configurationErrorDetail = configurationErrorDetail;
        }

        private static SavedPromptResolution success(ResolvedAiRequest request) {
            return new SavedPromptResolution(request, null);
        }

        private static SavedPromptResolution configurationError(String configurationErrorDetail) {
            return new SavedPromptResolution(null, configurationErrorDetail);
        }
    }

    private static class ModelConfigurationResolution {
        private final AiModelConfiguration modelConfiguration;
        private final String configurationErrorDetail;

        private ModelConfigurationResolution(AiModelConfiguration modelConfiguration, String configurationErrorDetail) {
            this.modelConfiguration = modelConfiguration;
            this.configurationErrorDetail = configurationErrorDetail;
        }

        private static ModelConfigurationResolution success(AiModelConfiguration modelConfiguration) {
            return new ModelConfigurationResolution(modelConfiguration, null);
        }

        private static ModelConfigurationResolution configurationError(String configurationErrorDetail) {
            return new ModelConfigurationResolution(null, configurationErrorDetail);
        }
    }
}

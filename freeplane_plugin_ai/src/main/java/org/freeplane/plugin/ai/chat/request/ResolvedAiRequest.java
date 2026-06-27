package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.Objects;
import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;

public class ResolvedAiRequest {
    private final String promptText;
    private final String promptDisplayName;
    private final Duration timeout;
    private final AiRequestMode mode;
    private final AiModelConfiguration modelConfiguration;
    private final AiToolAvailability toolAvailability;
    private final AiSelectionOverride selectionOverride;
    private final String systemMessage;
    private final boolean isSystemMessageExact;
    private final String profileName;
    private final String profileMessage;

    public ResolvedAiRequest(String promptText,
                      String promptDisplayName,
                      Duration timeout,
                      AiRequestMode mode,
                      AiModelConfiguration modelConfiguration,
                      AiToolAvailability toolAvailability,
                      AiSelectionOverride selectionOverride,
                      String systemMessage,
                      boolean isSystemMessageExact,
                      String profileName,
                      String profileMessage) {
        this.promptText = Objects.requireNonNull(promptText, "promptText");
        this.promptDisplayName = normalizeOptional(promptDisplayName);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.modelConfiguration = Objects.requireNonNull(modelConfiguration, "modelConfiguration");
        this.toolAvailability = Objects.requireNonNull(toolAvailability, "toolAvailability");
        this.selectionOverride = selectionOverride;
        this.systemMessage = normalizeNullable(systemMessage);
        this.isSystemMessageExact = isSystemMessageExact && this.systemMessage != null;
        this.profileName = normalizeNullable(profileName);
        this.profileMessage = normalizeNullable(profileMessage);
    }

    public String getPromptText() {
        return promptText;
    }

    public String getPromptDisplayName() {
        return promptDisplayName;
    }

    Duration getTimeout() {
        return timeout;
    }

    AiRequestMode getMode() {
        return mode;
    }

    public AiModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    public AiToolAvailability getToolAvailability() {
        return toolAvailability;
    }

    public AiSelectionOverride getSelectionOverride() {
        return selectionOverride;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public boolean isSystemMessageExact() {
        return isSystemMessageExact;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getProfileMessage() {
        return profileMessage;
    }

    public boolean hasProfileRequest() {
        return profileName != null || profileMessage != null;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}

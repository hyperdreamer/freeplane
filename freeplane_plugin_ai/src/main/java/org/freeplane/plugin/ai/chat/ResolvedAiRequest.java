package org.freeplane.plugin.ai.chat;

import java.time.Duration;
import java.util.Objects;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;

final class ResolvedAiRequest {
    private final String promptText;
    private final String promptDisplayName;
    private final Duration timeout;
    private final AiRequestMode mode;
    private final AiModelSelection modelSelection;
    private final AiToolAvailability toolAvailability;
    private final AiSelectionOverride selectionOverride;

    ResolvedAiRequest(String promptText,
                      String promptDisplayName,
                      Duration timeout,
                      AiRequestMode mode,
                      AiModelSelection modelSelection,
                      AiToolAvailability toolAvailability,
                      AiSelectionOverride selectionOverride) {
        this.promptText = Objects.requireNonNull(promptText, "promptText");
        this.promptDisplayName = normalizeOptional(promptDisplayName);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.modelSelection = Objects.requireNonNull(modelSelection, "modelSelection");
        this.toolAvailability = Objects.requireNonNull(toolAvailability, "toolAvailability");
        this.selectionOverride = selectionOverride;
    }

    String getPromptText() {
        return promptText;
    }

    String getPromptDisplayName() {
        return promptDisplayName;
    }

    Duration getTimeout() {
        return timeout;
    }

    AiRequestMode getMode() {
        return mode;
    }

    AiModelSelection getModelSelection() {
        return modelSelection;
    }

    AiToolAvailability getToolAvailability() {
        return toolAvailability;
    }

    AiSelectionOverride getSelectionOverride() {
        return selectionOverride;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

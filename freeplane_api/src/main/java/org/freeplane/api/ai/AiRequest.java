package org.freeplane.api.ai;

import java.time.Duration;
import java.util.Objects;

/** Immutable AI request specification.
 * @since 1.13.3 */
public class AiRequest {
    private final String name;
    private final String prompt;
    private final AiModelSelection modelSelection;
    private final AiToolAvailability toolAvailability;
    private final AiRequestMode mode;
    private final Duration timeout;
    private final AiSelectionOverride selectionOverride;

    public AiRequest(String prompt,
                     AiModelSelection modelSelection,
                     AiToolAvailability toolAvailability,
                     AiRequestMode mode,
                     Duration timeout) {
        this(null, prompt, modelSelection, toolAvailability, mode, timeout, null);
    }

    public AiRequest(String name,
                     String prompt,
                     AiModelSelection modelSelection,
                     AiToolAvailability toolAvailability,
                     AiRequestMode mode,
                     Duration timeout) {
        this(name, prompt, modelSelection, toolAvailability, mode, timeout, null);
    }

    public AiRequest(String prompt,
                     AiModelSelection modelSelection,
                     AiToolAvailability toolAvailability,
                     AiRequestMode mode,
                     Duration timeout,
                     AiSelectionOverride selectionOverride) {
        this(null, prompt, modelSelection, toolAvailability, mode, timeout, selectionOverride);
    }

    public AiRequest(String name,
                     String prompt,
                     AiModelSelection modelSelection,
                     AiToolAvailability toolAvailability,
                     AiRequestMode mode,
                     Duration timeout,
                     AiSelectionOverride selectionOverride) {
        this.name = normalizeOptional(name);
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.modelSelection = Objects.requireNonNull(modelSelection, "modelSelection");
        this.toolAvailability = Objects.requireNonNull(toolAvailability, "toolAvailability");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.selectionOverride = selectionOverride;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public AiModelSelection getModelSelection() {
        return modelSelection;
    }

    public AiToolAvailability getToolAvailability() {
        return toolAvailability;
    }

    public AiRequestMode getMode() {
        return mode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public AiSelectionOverride getSelectionOverride() {
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

package org.freeplane.api.ai;

import java.time.Duration;
import java.util.Objects;

/** Immutable public options for script-facing AI requests.
 * @since 1.13.3 */
public class AiRequestOptions {
    private final Duration timeout;
    private final AiRequestMode mode;
    private final AiModelSelection modelSelection;
    private final AiToolAvailability toolAvailability;
    private final AiSelectionOverride selectionOverride;

    private AiRequestOptions(Builder builder) {
        this.timeout = requirePositiveTimeout(builder.timeout);
        this.mode = builder.mode;
        this.modelSelection = builder.modelSelection;
        this.toolAvailability = builder.toolAvailability;
        this.selectionOverride = builder.selectionOverride;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public AiRequestMode getMode() {
        return mode;
    }

    public AiModelSelection getModelSelection() {
        return modelSelection;
    }

    public AiToolAvailability getToolAvailability() {
        return toolAvailability;
    }

    public AiSelectionOverride getSelectionOverride() {
        return selectionOverride;
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        Duration requiredTimeout = Objects.requireNonNull(timeout, "timeout");
        if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return requiredTimeout;
    }

    public static class Builder {
        private Duration timeout;
        private AiRequestMode mode;
        private AiModelSelection modelSelection;
        private AiToolAvailability toolAvailability;
        private AiSelectionOverride selectionOverride;

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder mode(AiRequestMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder modelSelection(AiModelSelection modelSelection) {
            this.modelSelection = modelSelection;
            return this;
        }

        public Builder toolAvailability(AiToolAvailability toolAvailability) {
            this.toolAvailability = toolAvailability;
            return this;
        }

        public Builder selectionOverride(AiSelectionOverride selectionOverride) {
            this.selectionOverride = selectionOverride;
            return this;
        }

        public AiRequestOptions build() {
            return new AiRequestOptions(this);
        }
    }
}

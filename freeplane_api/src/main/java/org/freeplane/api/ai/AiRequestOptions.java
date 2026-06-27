package org.freeplane.api.ai;

import java.time.Duration;
import java.util.Objects;

/** Immutable public options for script-facing AI requests.
 * @since 1.13.3 */
public class AiRequestOptions {
    private final Duration timeout;
    private final AiRequestMode mode;
    private final AiModelConfiguration modelConfiguration;
    private final AiToolAvailability toolAvailability;
    private final AiSelectionOverride selectionOverride;
    private final String systemMessage;
    private final boolean isSystemMessageExact;
    private final String profileName;
    private final String profileMessage;

    private AiRequestOptions(Builder builder) {
        this.timeout = requirePositiveTimeout(builder.timeout);
        this.mode = builder.mode;
        this.modelConfiguration = builder.modelConfiguration;
        this.toolAvailability = builder.toolAvailability;
        this.selectionOverride = builder.selectionOverride;
        this.systemMessage = normalizeNullable(builder.systemMessage);
        this.isSystemMessageExact = builder.isSystemMessageExact && this.systemMessage != null;
        this.profileName = normalizeNullable(builder.profileName);
        this.profileMessage = normalizeNullable(builder.profileMessage);
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

    private static Duration requirePositiveTimeout(Duration timeout) {
        Duration requiredTimeout = Objects.requireNonNull(timeout, "timeout");
        if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return requiredTimeout;
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    public static class Builder {
        private Duration timeout;
        private AiRequestMode mode;
        private AiModelConfiguration modelConfiguration;
        private AiToolAvailability toolAvailability;
        private AiSelectionOverride selectionOverride;
        private String systemMessage;
        private boolean isSystemMessageExact;
        private String profileName;
        private String profileMessage;

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder mode(AiRequestMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder modelConfiguration(AiModelConfiguration modelConfiguration) {
            this.modelConfiguration = modelConfiguration;
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

        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            this.isSystemMessageExact = false;
            return this;
        }

        public Builder exactSystemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            this.isSystemMessageExact = systemMessage != null;
            return this;
        }

        public Builder profile(String name) {
            this.profileName = name == null ? "" : name;
            this.profileMessage = null;
            return this;
        }

        public Builder profile(String name, String message) {
            this.profileName = name == null ? "" : name;
            this.profileMessage = Objects.requireNonNull(message, "message");
            return this;
        }

        public AiRequestOptions build() {
            return new AiRequestOptions(this);
        }
    }
}

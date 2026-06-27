package org.freeplane.api.ai;

import java.util.Locale;

/** Provider-independent thinking effort for AI model configuration.
 * @since 1.13.3 */
public enum AiThinkingEffort {
    MAX,
    XHIGH,
    HIGH,
    MEDIUM,
    LOW,
    MINIMAL,
    NONE;

    public static AiThinkingEffort fromPreferenceValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "inherit".equalsIgnoreCase(normalized)) {
            return null;
        }
        for (AiThinkingEffort effort : values()) {
            if (effort.name().equalsIgnoreCase(normalized)
                || effort.toOpenAiValue().equals(normalized.toLowerCase(Locale.ROOT))) {
                return effort;
            }
        }
        return null;
    }

    public String toOpenAiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}

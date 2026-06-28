package org.freeplane.api.ai;

import java.util.Locale;

/**
 * Provider-independent thinking-effort override for AI model configuration.
 *
 * <p>A {@code null} thinking effort in {@link AiModelConfiguration} means inherit
 * the surrounding configuration.</p>
 *
 * @since 1.13.3
 */
public enum AiThinkingEffort {
    MAX,
    XHIGH,
    HIGH,
    MEDIUM,
    LOW,
    MINIMAL,
    NONE;

    /**
     * Parses a stored preference value.
     *
     * <p>{@code null}, blank, and {@code inherit} return {@code null}. Unknown
     * values also return {@code null}.</p>
     *
     * @param value preference value
     * @return parsed effort, or {@code null} to inherit
     */
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

    /**
     * Returns the lowercase value used by OpenAI-compatible providers.
     *
     * @return lowercase provider value
     */
    public String toOpenAiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}

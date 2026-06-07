package org.freeplane.plugin.ai.chat.memory;

import java.util.Locale;

public enum ChatTokenCounterMode {
    HIDDEN("hidden"),
    CONTEXT_WINDOW("context"),
    TOTAL_CHAT("total"),
    MODEL_RESPONSE("model_response");

    private final String preferenceValue;

    ChatTokenCounterMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    static ChatTokenCounterMode fromPreferenceValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return HIDDEN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ChatTokenCounterMode mode : values()) {
            if (mode.preferenceValue.equals(normalized)) {
                return mode;
            }
        }
        return HIDDEN;
    }
}

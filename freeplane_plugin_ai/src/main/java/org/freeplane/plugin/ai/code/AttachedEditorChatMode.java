package org.freeplane.plugin.ai.code;

import java.util.Locale;

public enum AttachedEditorChatMode {
    NEW_CHAT("new_chat"),
    REUSE_CURRENT_CHAT("reuse_current_chat");

    private final String preferenceValue;

    AttachedEditorChatMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public static AttachedEditorChatMode fromPreferenceValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NEW_CHAT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AttachedEditorChatMode mode : values()) {
            if (mode.preferenceValue.equals(normalized)) {
                return mode;
            }
        }
        return NEW_CHAT;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }
}

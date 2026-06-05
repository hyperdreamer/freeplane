package org.freeplane.plugin.ai.chat.memory;

import org.freeplane.core.resources.ResourceController;

public class ChatTokenCounterSettings {
    public static final String CHAT_TOKEN_COUNTER_MODE_PROPERTY = "ai_chat_token_counter_mode";

    private final ChatTokenCounterMode counterMode;

    public ChatTokenCounterSettings() {
        this(ResourceController.getResourceController());
    }

    ChatTokenCounterSettings(ResourceController resourceController) {
        this.counterMode = ChatTokenCounterMode.fromPreferenceValue(
            resourceController.getProperty(CHAT_TOKEN_COUNTER_MODE_PROPERTY));
    }

    public ChatTokenCounterMode getCounterMode() {
        return counterMode;
    }
}

package org.freeplane.plugin.ai.chat.ui;

import org.freeplane.core.resources.ResourceController;

class AIChatMessageStyleSettings {
    static final String CHAT_FONT_SCALING_PROPERTY = "ai_chat_font_scaling";
    private static final int DEFAULT_CHAT_FONT_SCALING = 100;
    private static final int MIN_CHAT_FONT_SCALING = 25;
    private static final int MAX_CHAT_FONT_SCALING = 400;

    private final int chatFontScaling;

    public AIChatMessageStyleSettings() {
        this(ResourceController.getResourceController());
    }

    AIChatMessageStyleSettings(ResourceController resourceController) {
        this.chatFontScaling = parseChatFontScaling(resourceController.getProperty(CHAT_FONT_SCALING_PROPERTY));
    }

    public int getChatFontScaling() {
        return chatFontScaling;
    }

    static int parseChatFontScaling(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT_CHAT_FONT_SCALING;
        }
        try {
            int parsedValue = Integer.parseInt(value.trim());
            if (parsedValue < MIN_CHAT_FONT_SCALING || parsedValue > MAX_CHAT_FONT_SCALING) {
                return DEFAULT_CHAT_FONT_SCALING;
            }
            return parsedValue;
        }
        catch (NumberFormatException exception) {
            return DEFAULT_CHAT_FONT_SCALING;
        }
    }
}

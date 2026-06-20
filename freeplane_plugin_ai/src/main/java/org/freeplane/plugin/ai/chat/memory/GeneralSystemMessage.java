package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class GeneralSystemMessage extends SystemMessage {
    private static final String NON_EMPTY_SUPER_TEXT = "captured system message";
    private final String text;

    public GeneralSystemMessage(String text) {
        super(normalizeForSuper(text));
        this.text = text == null ? "" : text.trim();
    }

    @Override
    public String text() {
        return text;
    }

    private static String normalizeForSuper(String text) {
        String normalized = text == null ? "" : text.trim();
        return normalized.isEmpty() ? NON_EMPTY_SUPER_TEXT : normalized;
    }
}

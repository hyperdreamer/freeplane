package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class GeneralSystemMessage extends SystemMessage {
    private static final String NON_EMPTY_SUPER_TEXT = "captured system message";
    private final String baseText;
    private final String composedText;
    private final boolean isSystemMessageExact;

    public GeneralSystemMessage(String baseText, String composedText, boolean isSystemMessageExact) {
        super(normalizeForSuper(composedText));
        this.baseText = normalize(baseText);
        this.composedText = normalize(composedText);
        this.isSystemMessageExact = isSystemMessageExact;
    }

    public String baseText() {
        return baseText;
    }

    public String composedText() {
        return composedText;
    }

    public boolean isSystemMessageExact() {
        return isSystemMessageExact;
    }

    @Override
    public String text() {
        return composedText;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private static String normalizeForSuper(String text) {
        String normalized = normalize(text);
        return normalized.isEmpty() ? NON_EMPTY_SUPER_TEXT : normalized;
    }
}

package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class TranscriptHiddenSystemMessage extends SystemMessage {
    public static final String DEFAULT_TEXT =
        "System message: The messages in this session include a restored transcript of a prior chat. "
            + "Treat those messages as the earlier conversation context, not as hallucinations. "
            + "The currently opened map may differ from the maps discussed in that transcript. "
            + "Confirm the map context with the user when needed. The real conversation begins after this message. ";

    public TranscriptHiddenSystemMessage() {
        super(DEFAULT_TEXT);
    }

    public TranscriptHiddenSystemMessage(String text) {
        super(text == null || text.trim().isEmpty() ? DEFAULT_TEXT : text);
    }
}

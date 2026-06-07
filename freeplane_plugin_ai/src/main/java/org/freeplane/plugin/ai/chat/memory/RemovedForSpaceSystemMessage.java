package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class RemovedForSpaceSystemMessage extends SystemMessage {
    public static final String DEFAULT_TEXT =
        "The earlier part of this conversation was removed for space. "
            + "The user is continuing a previous thought.";

    public RemovedForSpaceSystemMessage() {
        super(DEFAULT_TEXT);
    }

    public RemovedForSpaceSystemMessage(String text) {
        super(text);
    }
}

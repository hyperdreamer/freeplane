package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class TranscriptHiddenSystemMessage extends SystemMessage {
    public TranscriptHiddenSystemMessage(String text) {
        super(text);
    }
}

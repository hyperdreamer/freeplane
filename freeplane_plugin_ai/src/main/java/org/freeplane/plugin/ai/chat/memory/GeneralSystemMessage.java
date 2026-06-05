package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;

public class GeneralSystemMessage extends SystemMessage {
    public GeneralSystemMessage(String text) {
        super(text);
    }
}

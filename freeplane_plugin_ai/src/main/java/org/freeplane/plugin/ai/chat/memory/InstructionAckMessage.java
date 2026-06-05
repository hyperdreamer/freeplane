package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;

public class InstructionAckMessage extends AiMessage {
    private static final String ACK_TEXT = "ok";

    public InstructionAckMessage() {
        super(ACK_TEXT);
    }
}

package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.SystemMessage;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

class ToolCallSummaryMessage extends SystemMessage {

    private final ToolCaller toolCaller;

    ToolCallSummaryMessage(String text, ToolCaller toolCaller) {
        super(text == null ? "" : text);
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
    }

    ToolCaller toolCaller() {
        return toolCaller;
    }
}

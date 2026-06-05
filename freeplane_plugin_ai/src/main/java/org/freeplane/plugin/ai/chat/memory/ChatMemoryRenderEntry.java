package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class ChatMemoryRenderEntry {

    private final ChatMessage chatMessage;
    private final String toolSummaryText;
    private final ToolCaller toolCaller;

    private ChatMemoryRenderEntry(ChatMessage chatMessage, String toolSummaryText, ToolCaller toolCaller) {
        this.chatMessage = chatMessage;
        this.toolSummaryText = toolSummaryText;
        this.toolCaller = toolCaller;
    }

    public static ChatMemoryRenderEntry forMessage(ChatMessage message) {
        return new ChatMemoryRenderEntry(message, null, null);
    }

    public static ChatMemoryRenderEntry forToolSummary(String summaryText, ToolCaller toolCaller) {
        return new ChatMemoryRenderEntry(null, summaryText, toolCaller);
    }

    public boolean isToolSummary() {
        return toolSummaryText != null;
    }

    public ChatMessage chatMessage() {
        return chatMessage;
    }

    public String toolSummaryText() {
        return toolSummaryText;
    }

    public ToolCaller toolCaller() {
        return toolCaller;
    }
}

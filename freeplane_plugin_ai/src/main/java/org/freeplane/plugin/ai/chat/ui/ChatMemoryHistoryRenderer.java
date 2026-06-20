package org.freeplane.plugin.ai.chat.ui;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.AutomaticCodeStatusMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.memory.InstructionAckMessage;
import org.freeplane.plugin.ai.chat.memory.RemovedForSpaceSystemMessage;
import org.freeplane.plugin.ai.chat.memory.TranscriptHiddenSystemMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

class ChatMemoryHistoryRenderer {

    private final ChatMessageHistory messageHistory;
    private final ChatMessageRenderer messageRenderer;
    private final ProfileMessageFormatter profileMessageFormatter;
    private InstructionMessageRenderingMode instructionMessageRenderingMode = InstructionMessageRenderingMode.BRIEF;

    ChatMemoryHistoryRenderer(ChatMessageHistory messageHistory, ChatMessageRenderer messageRenderer) {
        this(messageHistory, messageRenderer, profileName -> {
            if (profileName != null && !profileName.trim().isEmpty()) {
                return TextUtils.format("ai_chat_profile_message", profileName.trim());
            }
            return TextUtils.getText("ai_chat_profile_label");
        });
    }

    ChatMemoryHistoryRenderer(ChatMessageHistory messageHistory, ChatMessageRenderer messageRenderer,
                              ProfileMessageFormatter profileMessageFormatter) {
        this.messageHistory = messageHistory;
        this.messageRenderer = messageRenderer;
        this.profileMessageFormatter = profileMessageFormatter;
    }

    void rebuildFromMessages(List<ChatMemoryRenderEntry> entries) {
        messageHistory.clear();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            ChatMemoryRenderEntry entry = entries.get(index);
            if (entry == null) {
                continue;
            }
            if (isHiddenAcknowledgementMessage(entries, index)) {
                continue;
            }
            appendMessage(entry);
        }
    }

    void appendEntry(ChatMemoryRenderEntry entry) {
        if (entry == null) {
            return;
        }
        appendMessage(entry);
    }

    void setInstructionMessageRenderingMode(InstructionMessageRenderingMode instructionMessageRenderingMode) {
        this.instructionMessageRenderingMode = instructionMessageRenderingMode == null
            ? InstructionMessageRenderingMode.BRIEF
            : instructionMessageRenderingMode;
    }

    private void appendMessage(ChatMemoryRenderEntry entry) {
        MessageHistoryEntry historyEntry = toMessageHistoryEntry(entry);
        if (historyEntry == null || historyEntry.sourceText == null) {
            return;
        }
        String renderedText = messageRenderer.renderMessage(
            historyEntry.sourceText,
            historyEntry.category == RenderCategory.ASSISTANT);
        messageHistory.appendMessage(
            historyEntry.sourceText,
            renderedText,
            historyEntry.category.getStyleClassName());
    }

    private MessageHistoryEntry toMessageHistoryEntry(ChatMemoryRenderEntry entry) {
        if (entry.isToolSummary()) {
            RenderCategory category = entry.toolCaller() == ToolCaller.MCP
                ? RenderCategory.MCP_CALL
                : RenderCategory.TOOL_CALL;
            return new MessageHistoryEntry(entry.toolSummaryText(), category);
        }
        ChatMessage message = entry.chatMessage();
        if (message == null) {
            return null;
        }
        if (message instanceof AssistantProfileSwitchMessage) {
            AssistantProfileSwitchMessage profileMessage =
                (AssistantProfileSwitchMessage) message;
            String text = instructionMessageRenderingMode == InstructionMessageRenderingMode.FULL
                ? profileMessage.getProfileMessage()
                : buildProfilePaneMessage(profileMessage.getProfileName());
            return new MessageHistoryEntry(text, RenderCategory.PROFILE);
        }
        if (message instanceof GeneralSystemMessage) {
            if (instructionMessageRenderingMode != InstructionMessageRenderingMode.FULL) {
                return null;
            }
            return new MessageHistoryEntry(((GeneralSystemMessage) message).text(), RenderCategory.SYSTEM);
        }
        if (message instanceof TranscriptHiddenSystemMessage) {
            return null;
        }
        if (message instanceof RemovedForSpaceSystemMessage) {
            return new MessageHistoryEntry(((RemovedForSpaceSystemMessage) message).text(),
                RenderCategory.CONTEXT_BOUNDARY);
        }
        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
            String text = "Tool result [" + toolResult.toolName() + "]: " + toolResult.text();
            return new MessageHistoryEntry(text, RenderCategory.TOOL_CALL);
        }
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                return new MessageHistoryEntry(formatToolRequestSummary(aiMessage), RenderCategory.TOOL_CALL);
            }
            return new MessageHistoryEntry(aiMessage.text(), RenderCategory.ASSISTANT);
        }
        if (message instanceof AutomaticCodeStatusMessage) {
            return new MessageHistoryEntry(((AutomaticCodeStatusMessage) message).singleText(), RenderCategory.SYSTEM);
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            if (text != null && text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                return new MessageHistoryEntry(MessageBuilder.buildSystemInstructionText(text), RenderCategory.SYSTEM);
            }
            return new MessageHistoryEntry(text, RenderCategory.USER);
        }
        if (message instanceof SystemMessage) {
            return new MessageHistoryEntry(MessageBuilder.buildSystemInstructionText(((SystemMessage) message).text()),
                RenderCategory.SYSTEM);
        }
        return new MessageHistoryEntry(String.valueOf(message), RenderCategory.SYSTEM);
    }

    private String formatToolRequestSummary(AiMessage aiMessage) {
        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        if (requests == null || requests.isEmpty()) {
            return "Tool call";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < requests.size(); index++) {
            ToolExecutionRequest request = requests.get(index);
            if (index > 0) {
                builder.append("\n");
            }
            builder.append("Tool call [")
                .append(request.name())
                .append("]");
            String arguments = request.arguments();
            if (arguments != null && !arguments.trim().isEmpty()) {
                builder.append(": ").append(arguments);
            }
        }
        return builder.toString();
    }

    private boolean isHiddenAcknowledgementMessage(List<ChatMemoryRenderEntry> entries, int index) {
        if (entries == null || index <= 0 || index >= entries.size()) {
            return false;
        }
        ChatMemoryRenderEntry currentEntry = entries.get(index);
        ChatMemoryRenderEntry previousEntry = entries.get(index - 1);
        if (currentEntry == null || previousEntry == null
            || currentEntry.isToolSummary() || previousEntry.isToolSummary()) {
            return false;
        }
        ChatMessage current = currentEntry.chatMessage();
        ChatMessage previous = previousEntry.chatMessage();
        if (current == null || previous == null) {
            return false;
        }
        if (!(current instanceof InstructionAckMessage)) {
            return false;
        }
        return previous instanceof AssistantProfileSwitchMessage
            || previous instanceof TranscriptHiddenSystemMessage;
    }

    private String buildProfilePaneMessage(String profileName) {
        return profileMessageFormatter.formatProfileMessage(profileName);
    }

    private enum RenderCategory {
        USER("message-user"),
        ASSISTANT("message-assistant"),
        TOOL_CALL("message-tool"),
        MCP_CALL("message-mcp-call"),
        PROFILE("message-profile"),
        CONTEXT_BOUNDARY("message-context-boundary"),
        SYSTEM("message-system");

        private final String styleClassName;

        RenderCategory(String styleClassName) {
            this.styleClassName = styleClassName;
        }

        String getStyleClassName() {
            return styleClassName;
        }
    }

    private static class MessageHistoryEntry {
        private final String sourceText;
        private final RenderCategory category;

        private MessageHistoryEntry(String sourceText, RenderCategory category) {
            this.sourceText = sourceText;
            this.category = category;
        }
    }

    interface ProfileMessageFormatter {
        String formatProfileMessage(String profileName);
    }
}

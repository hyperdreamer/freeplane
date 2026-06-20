package org.freeplane.plugin.ai.chat.ui;

import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatUsageTotals;
import org.freeplane.plugin.ai.chat.session.LiveChatController;

class ChatOutputView {
    private final ChatMessageHistory messageHistory;
    private final ChatMessageRenderer messageRenderer;
    private final ChatMemoryHistoryRenderer chatMemoryHistoryRenderer;
    private final LiveChatController liveChatController;
    private final JLabel tokenUsageLabel;

    ChatOutputView(ChatMessageHistory messageHistory,
                   LiveChatController liveChatController,
                   JLabel tokenUsageLabel) {
        this.messageHistory = messageHistory;
        this.messageRenderer = new ChatMessageRenderer();
        this.chatMemoryHistoryRenderer = new ChatMemoryHistoryRenderer(messageHistory, messageRenderer);
        this.liveChatController = liveChatController;
        this.tokenUsageLabel = tokenUsageLabel;
    }

    void appendUserMessage(String text) {
        appendChatMessage(text, ChatMessageCategory.USER);
    }

    void appendAssistantMessage(String text) {
        appendChatMessage(text, ChatMessageCategory.ASSISTANT);
    }

    void appendProfileMessage(String profileName) {
        String normalizedName = profileName == null ? "" : profileName.trim();
        String messageText = normalizedName.isEmpty()
            ? TextUtils.getText("ai_chat_profile_label")
            : TextUtils.format("ai_chat_profile_message", normalizedName);
        appendChatMessage(messageText, ChatMessageCategory.PROFILE);
    }

    void appendFailureMessages(String userText, String errorMessage) {
        String normalizedUserMessage = userText == null ? "" : userText.trim();
        if (!normalizedUserMessage.isEmpty()) {
            appendTransientMessage(normalizedUserMessage, ChatMessageCategory.SYSTEM, false);
        }
        String normalizedErrorMessage = errorMessage == null ? "" : errorMessage.trim();
        String errorNotice = normalizedErrorMessage.isEmpty()
            ? "Request failed. Check model availability, account balance, or provider settings."
            : "Request failed: " + normalizedErrorMessage;
        appendFailureNotice(errorNotice);
    }

    void rebuildHistory(List<ChatMemoryRenderEntry> entries) {
        chatMemoryHistoryRenderer.rebuildFromMessages(entries);
    }

    void setInstructionMessageRenderingMode(InstructionMessageRenderingMode instructionMessageRenderingMode) {
        chatMemoryHistoryRenderer.setInstructionMessageRenderingMode(instructionMessageRenderingMode);
    }

    void appendHistoryEntry(ChatMemoryRenderEntry entry) {
        if (SwingUtilities.isEventDispatchThread()) {
            chatMemoryHistoryRenderer.appendEntry(entry);
        } else {
            SwingUtilities.invokeLater(() -> chatMemoryHistoryRenderer.appendEntry(entry));
        }
    }

    void updateTokenUsageLabel(ChatUsageTotals totals) {
        SwingUtilities.invokeLater(() -> {
            tokenUsageLabel.setVisible(totals.isVisible());
            tokenUsageLabel.setText(totals.formatStatusLine());
        });
    }

    private void appendChatMessage(String text, ChatMessageCategory category) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendChatMessageInternal(text, category);
        } else {
            SwingUtilities.invokeLater(() -> appendChatMessageInternal(text, category));
        }
    }

    private void appendChatMessageInternal(String text, ChatMessageCategory category) {
        if (text == null || category == null) {
            return;
        }
        String messageText = messageRenderer.renderMessage(text, category == ChatMessageCategory.ASSISTANT);
        messageHistory.appendMessage(text, messageText, category.getStyleClassName());
        if (category == ChatMessageCategory.USER) {
            liveChatController.recordUserMessage(text);
        } else if (category == ChatMessageCategory.ASSISTANT) {
            liveChatController.recordAssistantMessage(text);
        }
    }

    private void appendTransientMessage(String sourceText, ChatMessageCategory category, boolean renderAsAssistant) {
        if (sourceText == null || category == null) {
            return;
        }
        String renderedText = messageRenderer.renderMessage(sourceText, renderAsAssistant);
        messageHistory.appendMessage(sourceText, renderedText, category.getStyleClassName());
    }

    private void appendFailureNotice(String sourceText) {
        if (sourceText == null) {
            return;
        }
        String renderedText = messageRenderer.renderFailureMessage(sourceText);
        messageHistory.appendMessage(sourceText, renderedText, ChatMessageCategory.ERROR.getStyleClassName());
    }

    private enum ChatMessageCategory {
        USER("message-user"),
        ASSISTANT("message-assistant"),
        PROFILE("message-profile"),
        ERROR("message-error"),
        SYSTEM("message-system");

        private final String styleClassName;

        ChatMessageCategory(String styleClassName) {
            this.styleClassName = styleClassName;
        }

        String getStyleClassName() {
            return styleClassName;
        }
    }
}

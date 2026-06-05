package org.freeplane.plugin.ai.chat.ui;

import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import org.freeplane.core.util.TextUtils;

class ChatMarkdownCopyAction extends AbstractAction {
    private static final long serialVersionUID = 1L;

    private final JEditorPane messageHistoryPane;
    private final ChatMessageHistory messageHistory;
    private final Consumer<String> clipboardWriter;

    ChatMarkdownCopyAction(JEditorPane messageHistoryPane, ChatMessageHistory messageHistory) {
        this(messageHistoryPane, messageHistory, TextUtils::copyToClipboard);
    }

    ChatMarkdownCopyAction(JEditorPane messageHistoryPane,
                           ChatMessageHistory messageHistory,
                           Consumer<String> clipboardWriter) {
        this.messageHistoryPane = Objects.requireNonNull(messageHistoryPane);
        this.messageHistory = Objects.requireNonNull(messageHistory);
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        String markdownText = messageHistory.markdownTextForSelection(
            messageHistoryPane.getSelectionStart(),
            messageHistoryPane.getSelectionEnd());
        if (markdownText == null || markdownText.isEmpty()) {
            return;
        }
        clipboardWriter.accept(markdownText);
    }
}

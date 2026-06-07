package org.freeplane.plugin.ai.chat.ui;

import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.TransferHandler;

class ChatMessageTransferHandler extends TransferHandler {
    private final JEditorPane messageHistoryPane;
    private final ChatMessageHistory messageHistory;

    ChatMessageTransferHandler(JEditorPane messageHistoryPane, ChatMessageHistory messageHistory) {
        this.messageHistoryPane = messageHistoryPane;
        this.messageHistory = messageHistory;
    }

    @Override
    public int getSourceActions(JComponent component) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent component) {
        int selectionStart = messageHistoryPane.getSelectionStart();
        int selectionEnd = messageHistoryPane.getSelectionEnd();
        return messageHistory.createTransferable(selectionStart, selectionEnd);
    }
}

package org.freeplane.plugin.ai.chat.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLEditorKit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMessageTransferHandlerTest {
    @Test
    public void createTransferable_copiesPlainTextWithLineBreaksAndMarkup() throws Exception {
        JEditorPane messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        HTMLEditorKit messageHistoryEditorKit = (HTMLEditorKit) messageHistoryPane.getEditorKit();
        ChatMessageHistory messageHistory = new ChatMessageHistory(messageHistoryPane, messageHistoryEditorKit);
        String sourceText = "Line one\nLine two";
        String renderedMarkup = "Line one<br>Line two";
        messageHistory.appendMessage(sourceText, renderedMarkup, "message-assistant");
        ChatMessageTransferHandler transferHandler = new ChatMessageTransferHandler(messageHistoryPane, messageHistory);

        messageHistoryPane.setSelectionStart(0);
        messageHistoryPane.setSelectionEnd(messageHistoryPane.getDocument().getLength());

        Transferable transferable = transferHandler.createTransferable(messageHistoryPane);

        assertThat(transferable).isNotNull();
        assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue();
        Object plainText = transferable.getTransferData(DataFlavor.stringFlavor);
        assertThat(plainText).isEqualTo(sourceText);

        DataFlavor markupFlavor = findMarkupFlavor(transferable.getTransferDataFlavors());
        assertThat(markupFlavor).isNotNull();
        Object markupText = transferable.getTransferData(markupFlavor);
        assertThat(markupText).isInstanceOf(String.class);
        assertThat((String) markupText).contains("<html><body>");
        assertThat((String) markupText).contains("<div class=\"message-assistant\">Line one<br>Line two</div>");
    }

    @Test
    public void createTransferable_joinsMultipleMessagesWithBlankLine() throws Exception {
        JEditorPane messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        HTMLEditorKit messageHistoryEditorKit = (HTMLEditorKit) messageHistoryPane.getEditorKit();
        ChatMessageHistory messageHistory = new ChatMessageHistory(messageHistoryPane, messageHistoryEditorKit);
        messageHistory.appendMessage("First line\nSecond line", "First line<br>Second line", "message-user");
        messageHistory.appendMessage("Third line", "Third line", "message-user");
        ChatMessageTransferHandler transferHandler = new ChatMessageTransferHandler(messageHistoryPane, messageHistory);

        messageHistoryPane.setSelectionStart(0);
        messageHistoryPane.setSelectionEnd(messageHistoryPane.getDocument().getLength());

        Transferable transferable = transferHandler.createTransferable(messageHistoryPane);

        Object plainText = transferable.getTransferData(DataFlavor.stringFlavor);
        assertThat(plainText).isEqualTo("First line\nSecond line\n\nThird line");
    }

    @Test
    public void createTransferable_returnsSelectedSnippetForSingleMessage() throws Exception {
        JEditorPane messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        HTMLEditorKit messageHistoryEditorKit = (HTMLEditorKit) messageHistoryPane.getEditorKit();
        ChatMessageHistory messageHistory = new ChatMessageHistory(messageHistoryPane, messageHistoryEditorKit);
        messageHistory.appendMessage("Alpha Beta Gamma", "Alpha Beta Gamma", "message-assistant");
        ChatMessageTransferHandler transferHandler = new ChatMessageTransferHandler(messageHistoryPane, messageHistory);

        String documentText = messageHistoryPane.getDocument().getText(0, messageHistoryPane.getDocument().getLength());
        int selectionStart = documentText.indexOf("Beta");
        int selectionEnd = selectionStart + "Beta".length();
        messageHistoryPane.setSelectionStart(selectionStart);
        messageHistoryPane.setSelectionEnd(selectionEnd);

        Transferable transferable = transferHandler.createTransferable(messageHistoryPane);

        Object plainText = transferable.getTransferData(DataFlavor.stringFlavor);
        assertThat(plainText).isEqualTo("Beta");

        DataFlavor markupFlavor = findMarkupFlavor(transferable.getTransferDataFlavors());
        Object markupText = transferable.getTransferData(markupFlavor);
        assertThat((String) markupText).doesNotContain("message-assistant");
    }

    @Test
    public void createTransferable_returnsSelectedRangeAcrossMessages() throws Exception {
        JEditorPane messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        HTMLEditorKit messageHistoryEditorKit = (HTMLEditorKit) messageHistoryPane.getEditorKit();
        ChatMessageHistory messageHistory = new ChatMessageHistory(messageHistoryPane, messageHistoryEditorKit);
        messageHistory.appendMessage("First line\nSecond line", "First line<br>Second line", "message-user");
        messageHistory.appendMessage("Third line\nFourth line", "Third line<br>Fourth line", "message-user");
        ChatMessageTransferHandler transferHandler = new ChatMessageTransferHandler(messageHistoryPane, messageHistory);

        String documentText = messageHistoryPane.getDocument().getText(0, messageHistoryPane.getDocument().getLength());
        int selectionStart = documentText.indexOf("Second");
        int selectionEnd = documentText.indexOf("Fourth") + "Fourth".length();
        messageHistoryPane.setSelectionStart(selectionStart);
        messageHistoryPane.setSelectionEnd(selectionEnd);

        Transferable transferable = transferHandler.createTransferable(messageHistoryPane);

        Object plainText = transferable.getTransferData(DataFlavor.stringFlavor);
        String expectedText = documentText.substring(selectionStart, selectionEnd);
        assertThat(plainText).isEqualTo(expectedText);
    }

    private DataFlavor findMarkupFlavor(DataFlavor[] dataFlavors) {
        for (DataFlavor dataFlavor : dataFlavors) {
            if (dataFlavor.getMimeType().startsWith("text/html")) {
                return dataFlavor;
            }
        }
        return null;
    }
}

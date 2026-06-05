package org.freeplane.plugin.ai.chat.ui;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLEditorKit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMessageHistoryTest {
    @Test
    public void markdownTextForSelection_partialSelectionInsideOneMessage_returnsWholeSourceText() throws Exception {
        HistoryFixture fixture = new HistoryFixture();
        fixture.append("Alpha **Beta** Gamma", "Alpha <strong>Beta</strong> Gamma", "message-assistant");

        fixture.selectRenderedText("Beta");

        assertThat(fixture.uut.markdownTextForSelection(
            fixture.pane.getSelectionStart(),
            fixture.pane.getSelectionEnd())).isEqualTo("Alpha **Beta** Gamma");
    }

    @Test
    public void markdownTextForSelection_selectionSpanningMultipleMessages_returnsIntersectedSourceTexts() throws Exception {
        HistoryFixture fixture = new HistoryFixture();
        fixture.append("First **item**", "First <strong>item</strong>", "message-assistant");
        fixture.append("Second `item`", "Second <code>item</code>", "message-user");

        String documentText = fixture.documentText();
        int selectionStart = documentText.indexOf("item");
        int selectionEnd = documentText.lastIndexOf("item") + "item".length();

        assertThat(fixture.uut.markdownTextForSelection(selectionStart, selectionEnd))
            .isEqualTo("First **item**\n\nSecond `item`");
    }

    @Test
    public void markdownTextForSelection_includesVisibleToolBlockWhenIntersected() throws Exception {
        HistoryFixture fixture = new HistoryFixture();
        fixture.append("Assistant text", "Assistant text", "message-assistant");
        fixture.append("Tool call [search]: {\"q\":\"markdown\"}",
            "Tool call [search]: {\"q\":\"markdown\"}",
            "message-tool");

        fixture.selectRenderedText("search");

        assertThat(fixture.uut.markdownTextForSelection(
            fixture.pane.getSelectionStart(),
            fixture.pane.getSelectionEnd())).isEqualTo("Tool call [search]: {\"q\":\"markdown\"}");
    }

    @Test
    public void markdownTextForSelection_emptySelectionInsideOneBlock_returnsWholeSourceText() throws Exception {
        HistoryFixture fixture = new HistoryFixture();
        fixture.append("Alpha **Beta** Gamma", "Alpha <strong>Beta</strong> Gamma", "message-assistant");

        int caretOffset = fixture.documentText().indexOf("Beta") + 1;

        assertThat(fixture.uut.markdownTextForSelection(caretOffset, caretOffset))
            .isEqualTo("Alpha **Beta** Gamma");
    }

    @Test
    public void markdownTextForSelection_emptySelectionOnBoundary_returnsNull() {
        HistoryFixture fixture = new HistoryFixture();
        fixture.append("First message", "First message", "message-assistant");
        int boundaryOffset = fixture.pane.getDocument().getLength();
        fixture.append("Second message", "Second message", "message-user");

        assertThat(fixture.uut.markdownTextForSelection(boundaryOffset, boundaryOffset)).isNull();
    }

    private static class HistoryFixture {
        private final JEditorPane pane;
        private final ChatMessageHistory uut;

        private HistoryFixture() {
            pane = new JEditorPane();
            pane.setContentType("text/html");
            HTMLEditorKit editorKit = (HTMLEditorKit) pane.getEditorKit();
            uut = new ChatMessageHistory(pane, editorKit);
        }

        private void append(String sourceText, String renderedMarkup, String styleClassName) {
            uut.appendMessage(sourceText, renderedMarkup, styleClassName);
        }

        private void selectRenderedText(String renderedText) throws BadLocationException {
            String documentText = documentText();
            int selectionStart = documentText.indexOf(renderedText);
            int selectionEnd = selectionStart + renderedText.length();
            pane.setSelectionStart(selectionStart);
            pane.setSelectionEnd(selectionEnd);
        }

        private String documentText() throws BadLocationException {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        }
    }
}

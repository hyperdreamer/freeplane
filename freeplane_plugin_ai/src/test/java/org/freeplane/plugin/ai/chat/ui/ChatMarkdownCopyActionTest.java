package org.freeplane.plugin.ai.chat.ui;

import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLEditorKit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMarkdownCopyActionTest {
    @Test
    public void actionPerformed_withSelectedBlock_copiesMarkdownText() throws Exception {
        ActionFixture fixture = new ActionFixture();
        fixture.append("Alpha **Beta** Gamma", "Alpha <strong>Beta</strong> Gamma", "message-assistant");
        fixture.selectRenderedText("Beta");

        fixture.uut.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "copy"));

        assertThat(fixture.clipboardText.get()).isEqualTo("Alpha **Beta** Gamma");
    }

    @Test
    public void actionPerformed_withBoundaryCaret_doesNothing() {
        ActionFixture fixture = new ActionFixture();
        fixture.append("First message", "First message", "message-assistant");
        int boundaryOffset = fixture.pane.getDocument().getLength();
        fixture.append("Second message", "Second message", "message-user");
        fixture.pane.setCaretPosition(boundaryOffset);
        fixture.pane.setSelectionStart(boundaryOffset);
        fixture.pane.setSelectionEnd(boundaryOffset);

        fixture.uut.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "copy"));

        assertThat(fixture.clipboardText.get()).isNull();
    }

    private static class ActionFixture {
        private final AtomicReference<String> clipboardText;
        private final JEditorPane pane;
        private final ChatMessageHistory history;
        private final ChatMarkdownCopyAction uut;

        private ActionFixture() {
            clipboardText = new AtomicReference<>();
            pane = new JEditorPane();
            pane.setContentType("text/html");
            HTMLEditorKit editorKit = (HTMLEditorKit) pane.getEditorKit();
            history = new ChatMessageHistory(pane, editorKit);
            uut = new ChatMarkdownCopyAction(pane, history, clipboardText::set);
        }

        private void append(String sourceText, String renderedMarkup, String styleClassName) {
            history.appendMessage(sourceText, renderedMarkup, styleClassName);
        }

        private void selectRenderedText(String renderedText) throws BadLocationException {
            String documentText = pane.getDocument().getText(0, pane.getDocument().getLength());
            int selectionStart = documentText.indexOf(renderedText);
            int selectionEnd = selectionStart + renderedText.length();
            pane.setSelectionStart(selectionStart);
            pane.setSelectionEnd(selectionEnd);
        }
    }
}

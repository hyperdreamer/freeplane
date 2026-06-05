package org.freeplane.plugin.ai.chat.ui;

import java.util.Locale;
import javax.swing.JEditorPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.freeplane.core.ui.components.html.ScaledStyleSheet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMessageStyleApplierTest {

    @Test
    public void applyUsesScaledStyleSheetAndConfiguredScaling() {
        JEditorPane messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        HTMLEditorKit messageHistoryEditorKit = new HTMLEditorKit();

        float baseFontSizePt = 10f;
        new ChatMessageStyleApplier().apply(messageHistoryPane, messageHistoryEditorKit, baseFontSizePt, 150);

        HTMLDocument document = (HTMLDocument) messageHistoryPane.getDocument();
        StyleSheet styleSheet = document.getStyleSheet();
        assertThat(styleSheet).isInstanceOf(ScaledStyleSheet.class);

        assertThat(readFontSize(styleSheet, "body")).isEqualTo(formatPt(baseFontSizePt));
        assertThat(readFontSize(styleSheet, ".message-context-boundary")).isEqualTo(formatPt(baseFontSizePt * 5f / 6f));
        assertThat(readColor(styleSheet, ".message-error", CSS.Attribute.BACKGROUND_COLOR)).isNotNull();
        assertThat(readColor(styleSheet, ".message-error", CSS.Attribute.BORDER_LEFT_COLOR)).isNotNull();
    }

    private String readFontSize(StyleSheet styleSheet, String selector) {
        AttributeSet rule = styleSheet.getRule(selector);
        Object fontSize = rule == null ? null : rule.getAttribute(CSS.Attribute.FONT_SIZE);
        return fontSize == null ? null : fontSize.toString();
    }

    private String readColor(StyleSheet styleSheet, String selector, CSS.Attribute attribute) {
        AttributeSet rule = styleSheet.getRule(selector);
        Object color = rule == null ? null : rule.getAttribute(attribute);
        return color == null ? null : color.toString();
    }

    private String formatPt(float value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0fpt", value);
        }
        return String.format(Locale.ROOT, "%.2fpt", value);
    }
}

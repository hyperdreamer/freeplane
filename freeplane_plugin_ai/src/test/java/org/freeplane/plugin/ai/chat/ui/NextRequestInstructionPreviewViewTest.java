package org.freeplane.plugin.ai.chat.ui;

import java.util.Arrays;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NextRequestInstructionPreviewViewTest {
    @Test
    public void showPreviewRendersBlocksWithSeparatorAndPreviewStyle() {
        NextRequestInstructionPreviewView uut = new NextRequestInstructionPreviewView(new ChatMessageRenderer());
        uut.applyStyles(10f, 100);

        uut.showPreview(Arrays.asList(
            new PreviewInstructionBlock("System message", "system text", PreviewInstructionKind.SYSTEM),
            new PreviewInstructionBlock("Profile message: Reviewer", "profile text", PreviewInstructionKind.PROFILE)));

        String html = uut.previewPane().getText();
        assertThat(uut.isVisible()).isTrue();
        assertThat(html).contains("message-preview-instruction");
        assertThat(html).doesNotContain("Preview: next request instructions");
        assertThat(html).contains("Profile message: Reviewer");
        assertThat(html).contains("profile text");
        assertThat(html).contains("System message");
        assertThat(html).contains("system text");
        int systemTextIndex = html.indexOf("system text");
        int separatorIndex = html.indexOf("<hr");
        int profileTextIndex = html.indexOf("profile text");
        assertThat(systemTextIndex).isLessThan(separatorIndex);
        assertThat(separatorIndex).isLessThan(profileTextIndex);
    }

    @Test
    public void hidePreviewClearsAndHidesComponent() {
        NextRequestInstructionPreviewView uut = new NextRequestInstructionPreviewView(new ChatMessageRenderer());
        uut.showPreview(Arrays.asList(
            new PreviewInstructionBlock("System message", "system text", PreviewInstructionKind.SYSTEM)));

        uut.hidePreview();

        assertThat(uut.isVisible()).isFalse();
        assertThat(uut.previewPane().getText()).doesNotContain("system text");
    }

    @Test
    public void previewStyleIsDefinedSeparatelyFromCommittedProfileStyle() {
        NextRequestInstructionPreviewView uut = new NextRequestInstructionPreviewView(new ChatMessageRenderer());
        uut.applyStyles(10f, 100);

        HTMLDocument document = (HTMLDocument) uut.previewPane().getDocument();
        StyleSheet styleSheet = document.getStyleSheet();

        assertThat(styleSheet.getRule(".message-profile")).isNotNull();
        assertThat(styleSheet.getRule(".message-preview-instruction")).isNotNull();
        assertThat(styleSheet.getRule(".message-preview-instruction")
            .getAttribute(CSS.Attribute.BORDER_LEFT_STYLE).toString()).isEqualTo("dashed");
    }
}

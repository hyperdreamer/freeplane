package org.freeplane.plugin.ai.chat.ui;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.text.html.HTMLEditorKit;
import org.freeplane.core.ui.components.html.ScaledEditorKit;
import org.freeplane.core.util.HtmlUtils;

class NextRequestInstructionPreviewView extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JEditorPane previewPane;
    private final HTMLEditorKit editorKit;
    private final ChatMessageRenderer messageRenderer;

    NextRequestInstructionPreviewView(ChatMessageRenderer messageRenderer) {
        super(new BorderLayout());
        this.messageRenderer = messageRenderer == null ? new ChatMessageRenderer() : messageRenderer;
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        editorKit = ScaledEditorKit.create();
        previewPane.setEditorKit(editorKit);
        previewPane.setEditable(false);
        previewPane.setOpaque(false);
        add(previewPane, BorderLayout.CENTER);
        hidePreview();
    }

    void applyStyles(float baseFontSizePt, int fontScalingPercent) {
        new ChatMessageStyleApplier().apply(
            previewPane,
            editorKit,
            baseFontSizePt,
            fontScalingPercent);
    }

    void showPreview(List<PreviewInstructionBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            hidePreview();
            return;
        }
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<div class=\"message-preview-instruction\">");
        PreviewInstructionKind previousRenderedKind = null;
        boolean renderedAnyBlock = false;
        for (PreviewInstructionBlock block : blocks) {
            if (block == null || block.getText().isEmpty()) {
                continue;
            }
            if (previousRenderedKind == PreviewInstructionKind.SYSTEM
                && block.getKind() == PreviewInstructionKind.PROFILE) {
                html.append("<hr>");
            }
            html.append("<div class=\"message-preview-instruction-block\">")
                .append("<b>")
                .append(HtmlUtils.toXMLEscapedText(block.getLabel()))
                .append("</b><br>")
                .append(messageRenderer.renderMessage(block.getText(), false))
                .append("</div>");
            previousRenderedKind = block.getKind();
            renderedAnyBlock = true;
        }
        if (!renderedAnyBlock) {
            hidePreview();
            return;
        }
        html.append("</div></body></html>");
        previewPane.setText(html.toString());
        previewPane.setCaretPosition(0);
        setVisible(true);
    }

    void hidePreview() {
        previewPane.setText("<html><body></body></html>");
        setVisible(false);
    }

    JEditorPane previewPane() {
        return previewPane;
    }
}

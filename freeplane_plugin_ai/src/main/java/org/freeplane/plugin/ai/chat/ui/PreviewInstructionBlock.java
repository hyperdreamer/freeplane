package org.freeplane.plugin.ai.chat.ui;

final class PreviewInstructionBlock {
    private final String label;
    private final String text;
    private final PreviewInstructionKind kind;

    PreviewInstructionBlock(String label, String text, PreviewInstructionKind kind) {
        this.label = label == null ? "" : label.trim();
        this.text = text == null ? "" : text.trim();
        this.kind = kind == null ? PreviewInstructionKind.SYSTEM : kind;
    }

    String getLabel() {
        return label;
    }

    String getText() {
        return text;
    }

    PreviewInstructionKind getKind() {
        return kind;
    }
}

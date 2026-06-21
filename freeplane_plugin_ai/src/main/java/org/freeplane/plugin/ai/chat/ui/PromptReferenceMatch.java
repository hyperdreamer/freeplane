package org.freeplane.plugin.ai.chat.ui;

public class PromptReferenceMatch {
    private final String visibleText;
    private final String modelFacingText;
    private final String promptName;
    private final String promptText;
    private final int referenceStartOffset;
    private final int referenceEndOffset;

    PromptReferenceMatch(String visibleText,
                         String modelFacingText,
                         String promptName,
                         String promptText,
                         int referenceStartOffset,
                         int referenceEndOffset) {
        this.visibleText = visibleText == null ? "" : visibleText;
        this.modelFacingText = modelFacingText == null ? "" : modelFacingText;
        this.promptName = promptName == null ? "" : promptName;
        this.promptText = promptText == null ? "" : promptText;
        this.referenceStartOffset = Math.max(0, referenceStartOffset);
        this.referenceEndOffset = Math.max(this.referenceStartOffset, referenceEndOffset);
    }

    public String getVisibleText() {
        return visibleText;
    }

    public String getModelFacingText() {
        return modelFacingText;
    }

    public String getPromptName() {
        return promptName;
    }

    public String getPromptText() {
        return promptText;
    }

    public int getReferenceStartOffset() {
        return referenceStartOffset;
    }

    public int getReferenceEndOffset() {
        return referenceEndOffset;
    }
}

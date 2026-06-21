package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;

public class PromptReferenceUserMessage extends UserMessage {
    private final String visibleText;
    private final String promptName;
    private final String promptText;
    private final String modelFacingText;
    private final int referenceEndOffset;

    public PromptReferenceUserMessage(String visibleText,
                                      String promptName,
                                      String promptText,
                                      String modelFacingText,
                                      int referenceEndOffset) {
        super(modelFacingText == null ? "" : modelFacingText);
        this.visibleText = visibleText == null ? "" : visibleText;
        this.promptName = promptName == null ? "" : promptName;
        this.promptText = promptText == null ? "" : promptText;
        this.modelFacingText = modelFacingText == null ? "" : modelFacingText;
        this.referenceEndOffset = Math.max(0, Math.min(referenceEndOffset, this.visibleText.length()));
    }

    public String getVisibleText() {
        return visibleText;
    }

    public String getPromptName() {
        return promptName;
    }

    public String getPromptText() {
        return promptText;
    }

    public String getModelFacingText() {
        return modelFacingText;
    }

    public int getReferenceEndOffset() {
        return referenceEndOffset;
    }

    PromptReferenceUserMessage copy() {
        return new PromptReferenceUserMessage(visibleText, promptName, promptText, modelFacingText, referenceEndOffset);
    }
}

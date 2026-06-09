package org.freeplane.features.ai.code;

public interface AiChatAttachableEditor {
    CodeStateContent getCodeStateContent();

    void replaceCodeStateContent(CodeStateContent content);
}

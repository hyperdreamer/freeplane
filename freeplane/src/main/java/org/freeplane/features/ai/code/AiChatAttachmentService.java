package org.freeplane.features.ai.code;

public interface AiChatAttachmentService {
    boolean isAiConfigured();

    AiChatAttachment attachEditor(AiChatAttachableEditor editor, String contentType);
}

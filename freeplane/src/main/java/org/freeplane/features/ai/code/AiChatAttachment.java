package org.freeplane.features.ai.code;

public interface AiChatAttachment {
    void detach();

    void showOwningChat();

    void recordIssue(AiChatCodeOperationResult result);

    void clearIssue();

    void requestRepair(AiChatRepairRequest request);
}

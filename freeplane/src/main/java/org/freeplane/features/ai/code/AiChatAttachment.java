package org.freeplane.features.ai.code;

public interface AiChatAttachment {
    void detach();

    void setDetachHandler(Runnable detachHandler);

    void showOwningChat();

    void recordIssue(AiChatCodeOperationResult result);

    void clearIssue();

    void requestRepair(AiChatRepairRequest request);
}

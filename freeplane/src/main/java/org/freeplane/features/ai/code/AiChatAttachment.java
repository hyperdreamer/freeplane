package org.freeplane.features.ai.code;

public interface AiChatAttachment {
    void detach();

    void setDetachHandler(Runnable detachHandler);

    void showOwningChat();

    void recordCodeState(ReadCodeResponse state);

    void clearCodeState();

    void requestRepair(AiChatRepairRequest request);
}

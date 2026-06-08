package org.freeplane.plugin.ai.chat.memory;

class ChatMemoryViewState {

    private int chatWindowStartIndex;
    private int activeConversationTurnCount;
    private boolean restoredTranscriptSession;

    void clear() {
        chatWindowStartIndex = 0;
        activeConversationTurnCount = 0;
        restoredTranscriptSession = false;
    }

    int chatWindowStartIndex() {
        return chatWindowStartIndex;
    }

    void chatWindowStartIndex(int chatWindowStartIndex) {
        this.chatWindowStartIndex = chatWindowStartIndex;
    }

    int activeConversationTurnCount() {
        return activeConversationTurnCount;
    }

    void activeConversationTurnCount(int activeConversationTurnCount) {
        this.activeConversationTurnCount = activeConversationTurnCount;
    }

    boolean isRestoredTranscriptSession() {
        return restoredTranscriptSession;
    }

    void restoredTranscriptSession(boolean restoredTranscriptSession) {
        this.restoredTranscriptSession = restoredTranscriptSession;
    }
}

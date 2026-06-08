package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class FilteredChatMessages {

    private final List<ChatMessage> messages;
    private final boolean hasOmittedEarlierChat;
    private final int skippedToolWindowGroupCount;
    private final boolean restoredTranscriptSession;

    FilteredChatMessages(List<ChatMessage> messages,
                         boolean hasOmittedEarlierChat,
                         int skippedToolWindowGroupCount,
                         boolean restoredTranscriptSession) {
        this.messages = messages == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(messages));
        this.hasOmittedEarlierChat = hasOmittedEarlierChat;
        this.skippedToolWindowGroupCount = skippedToolWindowGroupCount;
        this.restoredTranscriptSession = restoredTranscriptSession;
    }

    List<ChatMessage> messages() {
        return messages;
    }

    boolean hasOmittedEarlierChat() {
        return hasOmittedEarlierChat;
    }

    int skippedToolWindowGroupCount() {
        return skippedToolWindowGroupCount;
    }

    boolean isRestoredTranscriptSession() {
        return restoredTranscriptSession;
    }
}

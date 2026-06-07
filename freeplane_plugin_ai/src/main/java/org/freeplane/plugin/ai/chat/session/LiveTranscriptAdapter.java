package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;

class LiveTranscriptAdapter {

    void appendUserMessage(LiveChatSession session, String text) {
        appendEntry(session, ChatTranscriptRole.USER, text);
    }

    void appendAssistantMessage(LiveChatSession session, String text) {
        appendEntry(session, ChatTranscriptRole.ASSISTANT, text);
    }

    void appendAssistantProfileMessage(LiveChatSession session, AssistantProfileSwitchMessage message) {
        if (session == null || message == null) {
            return;
        }
        List<ChatTranscriptEntry> entries = session.getTranscriptEntries();
        if (entries == null) {
            entries = new ArrayList<>();
            session.setTranscriptEntries(entries);
        }
        AssistantProfileTranscriptEntry entry = new AssistantProfileTranscriptEntry(
            message.getProfileId(),
            message.getProfileName(),
            false);
        entries.add(entry);
    }

    void setEntries(LiveChatSession session, List<ChatTranscriptEntry> entries) {
        if (session == null) {
            return;
        }
        if (entries == null) {
            session.setTranscriptEntries(new ArrayList<>());
        } else {
            session.setTranscriptEntries(new ArrayList<>(entries));
        }
    }

    private void appendEntry(LiveChatSession session, ChatTranscriptRole role, String text) {
        if (session == null || text == null) {
            return;
        }
        List<ChatTranscriptEntry> entries = session.getTranscriptEntries();
        if (entries == null) {
            entries = new ArrayList<>();
            session.setTranscriptEntries(entries);
        }
        entries.add(new ChatTranscriptEntry(role, text));
    }
}

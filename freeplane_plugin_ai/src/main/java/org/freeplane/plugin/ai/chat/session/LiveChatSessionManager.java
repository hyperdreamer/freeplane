package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

final class LiveChatSessionManager {
    private final Map<LiveChatSessionId, LiveChatSession> sessions = new LinkedHashMap<LiveChatSessionId, LiveChatSession>();
    private LiveChatSessionId currentSessionId;

    LiveChatSession createSession(ChatMemory chatMemory, String displayName) {
        return createSession(chatMemory, displayName, true, null);
    }

    LiveChatSession createSession(ChatMemory chatMemory, String displayName,
                                  boolean assistantProfileEnabled,
                                  ToolAvailabilityLevel toolAvailabilityOverride) {
        LiveChatSessionId id = LiveChatSessionId.create();
        LiveChatSession session = new LiveChatSession(id, chatMemory, displayName,
            assistantProfileEnabled, toolAvailabilityOverride);
        session.setLastActivityTimestamp(System.currentTimeMillis());
        sessions.put(id, session);
        if (currentSessionId == null) {
            currentSessionId = id;
        }
        return session;
    }

    LiveChatSession getCurrentSession() {
        return currentSessionId == null ? null : sessions.get(currentSessionId);
    }

    LiveChatSessionId getCurrentSessionId() {
        return currentSessionId;
    }

    void setCurrentSession(LiveChatSessionId sessionId) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return;
        }
        currentSessionId = sessionId;
    }

    List<LiveChatSessionSummary> listSessions() {
        List<LiveChatSessionSummary> summaries = new ArrayList<>();
        for (LiveChatSession session : sessions.values()) {
            if (session.getTranscriptEntries().isEmpty()) {
                continue;
            }
            summaries.add(new LiveChatSessionSummary(session.getId(), session.getDisplayName(),
                new ArrayList<>(session.getMapIds()), session.getMapRootShortTextCounts(),
                session.getTranscriptId(),
                session.getLastActivityTimestamp()));
        }
        return summaries;
    }

    void rename(LiveChatSessionId sessionId, String displayName) {
        LiveChatSession session = sessions.get(sessionId);
        if (session == null || displayName == null) {
            return;
        }
        session.setDisplayName(displayName);
        session.setNameEdited(true);
    }

    LiveChatSession remove(LiveChatSessionId sessionId) {
        LiveChatSession removed = sessions.remove(sessionId);
        if (sessionId != null && sessionId.equals(currentSessionId)) {
            currentSessionId = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
        }
        return removed;
    }

    LiveChatSession findSession(LiveChatSessionId sessionId) {
        return sessions.get(sessionId);
    }

    void recordMapId(String mapId) {
        LiveChatSession session = getCurrentSession();
        if (session == null || mapId == null || mapId.isEmpty()) {
            return;
        }
        session.getMapIds().add(mapId);
        session.setLastActivityTimestamp(System.currentTimeMillis());
    }

    void updateUserMessageName(String updatedName) {
        LiveChatSession session = getCurrentSession();
        if (session == null || session.isNameEdited() || session.isUserMessageNameApplied()) {
            return;
        }
        session.setDisplayName(updatedName);
        session.setUserMessageNameApplied(true);
    }
}

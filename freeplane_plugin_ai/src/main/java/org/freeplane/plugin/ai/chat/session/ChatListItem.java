package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptId;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;

class ChatListItem {
    private final ChatListItemStatus status;
    private final LiveChatSessionId liveSessionId;
    private final ChatTranscriptId transcriptId;
    private final String displayName;
    private final List<MapRootShortTextCount> mapRootShortTextCounts;
    private final long lastUpdatedTimestamp;
    private final boolean currentLiveSession;

    ChatListItem(ChatListItemStatus status, LiveChatSessionId liveSessionId, ChatTranscriptId transcriptId,
                 String displayName, List<MapRootShortTextCount> mapRootShortTextCounts,
                 long lastUpdatedTimestamp, boolean currentLiveSession) {
        this.status = status;
        this.liveSessionId = liveSessionId;
        this.transcriptId = transcriptId;
        this.displayName = displayName;
        this.mapRootShortTextCounts = mapRootShortTextCounts == null ? new ArrayList<>() : mapRootShortTextCounts;
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
        this.currentLiveSession = currentLiveSession;
    }

    ChatListItemStatus getStatus() {
        return status;
    }

    LiveChatSessionId getLiveSessionId() {
        return liveSessionId;
    }

    ChatTranscriptId getTranscriptId() {
        return transcriptId;
    }

    String getDisplayName() {
        return displayName;
    }

    List<MapRootShortTextCount> getMapRootShortTextCounts() {
        return new ArrayList<>(mapRootShortTextCounts);
    }

    long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    boolean isCurrentLiveSession() {
        return currentLiveSession;
    }

}

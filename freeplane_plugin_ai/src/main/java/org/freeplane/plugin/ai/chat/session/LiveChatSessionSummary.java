package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptId;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;

final class LiveChatSessionSummary {
    private final LiveChatSessionId id;
    private final String displayName;
    private final List<String> mapIds;
    private final List<MapRootShortTextCount> mapRootShortTextCounts;
    private final ChatTranscriptId transcriptId;
    private final long lastActivityTimestamp;

    LiveChatSessionSummary(LiveChatSessionId id, String displayName, List<String> mapIds,
                           List<MapRootShortTextCount> mapRootShortTextCounts,
                           ChatTranscriptId transcriptId, long lastActivityTimestamp) {
        this.id = id;
        this.displayName = displayName;
        this.mapIds = mapIds == null ? new ArrayList<>() : new ArrayList<>(mapIds);
        this.mapRootShortTextCounts = mapRootShortTextCounts == null ? new ArrayList<>()
            : new ArrayList<>(mapRootShortTextCounts);
        this.transcriptId = transcriptId;
        this.lastActivityTimestamp = lastActivityTimestamp;
    }

    LiveChatSessionId getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    List<String> getMapIds() {
        return new ArrayList<>(mapIds);
    }

    List<MapRootShortTextCount> getMapRootShortTextCounts() {
        return new ArrayList<>(mapRootShortTextCounts);
    }

    ChatTranscriptId getTranscriptId() {
        return transcriptId;
    }

    long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

}

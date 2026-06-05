package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptId;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatListDialogTest {

    @Test
    public void mergeLiveMapCounts_includesFreshMapsWhenCachedCountsExist() {
        List<MapRootShortTextCount> cached = Arrays.asList(
            new MapRootShortTextCount("Map A", 3));
        List<MapRootShortTextCount> fresh = Arrays.asList(
            new MapRootShortTextCount("Map B", 1));

        List<MapRootShortTextCount> merged = ChatListDialog.mergeLiveMapCounts(cached, fresh);

        assertThat(merged).anySatisfy(entry -> {
            assertThat(entry.getText()).isEqualTo("Map A");
            assertThat(entry.getCount()).isEqualTo(3);
        });
        assertThat(merged).anySatisfy(entry -> {
            assertThat(entry.getText()).isEqualTo("Map B");
            assertThat(entry.getCount()).isEqualTo(1);
        });
    }

    @Test
    public void deletionTargets_collectsAllSelectedLiveSessionsAndTranscripts() {
        LiveChatSessionId firstLiveSessionId = LiveChatSessionId.create();
        LiveChatSessionId secondLiveSessionId = LiveChatSessionId.create();
        ChatTranscriptId transcriptIdFromLiveSession = new ChatTranscriptId("live-chat-1.json");
        ChatTranscriptId transcriptOnlyId = new ChatTranscriptId("archived-chat-1.json");
        List<ChatListItem> selectedItems = Arrays.asList(
            new ChatListItem(ChatListItemStatus.LIVE, firstLiveSessionId, transcriptIdFromLiveSession,
                "Live 1", new ArrayList<MapRootShortTextCount>(), 1L, false),
            new ChatListItem(ChatListItemStatus.LIVE, secondLiveSessionId, null,
                "Live 2", new ArrayList<MapRootShortTextCount>(), 2L, false),
            new ChatListItem(ChatListItemStatus.TRANSCRIPT, null, transcriptOnlyId,
                "Transcript 1", new ArrayList<MapRootShortTextCount>(), 3L, false));

        ChatListDialog.DeletionTargets deletionTargets = ChatListDialog.deletionTargets(selectedItems);

        assertThat(deletionTargets.liveSessionIds())
            .containsExactlyInAnyOrder(firstLiveSessionId, secondLiveSessionId);
        assertThat(deletionTargets.transcriptIds())
            .containsExactlyInAnyOrder(transcriptIdFromLiveSession, transcriptOnlyId);
    }
}

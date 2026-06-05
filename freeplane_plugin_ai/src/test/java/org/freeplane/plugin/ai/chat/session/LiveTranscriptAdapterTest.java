package org.freeplane.plugin.ai.chat.session;

import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LiveTranscriptAdapterTest {

    @Test
    public void appendAssistantProfileMessage_recordsSystemRole() {
        LiveChatSession session = new LiveChatSession(
            LiveChatSessionId.create(),
            null,
            "test");
        LiveTranscriptAdapter adapter = new LiveTranscriptAdapter();
        AssistantProfileSwitchMessage message =
            new AssistantProfileSwitchMessage("profile-a", "A sayer");

        adapter.appendAssistantProfileMessage(session, message);

        List<ChatTranscriptEntry> entries = session.getTranscriptEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getRole()).isEqualTo(ChatTranscriptRole.ASSISTANT_PROFILE_SYSTEM);
        assertThat(entries.get(0)).isInstanceOf(AssistantProfileTranscriptEntry.class);
        AssistantProfileTranscriptEntry entry = (AssistantProfileTranscriptEntry) entries.get(0);
        assertThat(entry.getProfileId()).isEqualTo("profile-a");
        assertThat(entry.getProfileName()).isEqualTo("A sayer");
        assertThat(entry.containsProfileDefinition()).isFalse();
    }
}

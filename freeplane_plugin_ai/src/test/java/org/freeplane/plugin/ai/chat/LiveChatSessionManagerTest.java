package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.memory.ChatMemory;
import org.junit.Test;
import org.mockito.Mockito;

public class LiveChatSessionManagerTest {

    @Test
    public void createSession_allowsPromptSessionMetadata() {
        LiveChatSessionManager uut = new LiveChatSessionManager();
        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);

        LiveChatSession session = uut.createSession(
            chatMemory,
            "Prompt: Rewrite",
            false,
            ToolAvailabilityLevel.EDITING);

        assertThat(session.isAssistantProfileEnabled()).isFalse();
        assertThat(session.getToolAvailabilityOverride()).isEqualTo(ToolAvailabilityLevel.EDITING);
        assertThat(uut.getCurrentSession()).isEqualTo(session);
    }
}

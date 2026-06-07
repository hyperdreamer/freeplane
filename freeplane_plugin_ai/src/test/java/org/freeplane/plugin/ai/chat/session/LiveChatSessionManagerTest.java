package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.memory.ChatMemory;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

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

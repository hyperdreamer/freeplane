package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.memory.ChatMemory;
import java.util.Collections;
import java.util.List;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TranscriptMemoryMapperModelConfigurationTest {
    @Test
    public void preservesProfileModelConfigurationThroughTranscriptMapping() {
        TranscriptMemoryMapper mapper = new TranscriptMemoryMapper();
        AIModelConfiguration modelConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
            AiThinkingEffort.HIGH,
            AiTemperature.of(0.4));
        AssistantProfileSwitchMessage message = new AssistantProfileSwitchMessage(
            "profile-id",
            "Reviewer",
            "Review strictly",
            modelConfiguration);
        ChatMemory memory = org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory.withMaxTokens(500);
        memory.add(message);

        List<ChatTranscriptEntry> entries = mapper.toTranscriptEntries(memory);
        AssistantProfileTranscriptEntry entry = (AssistantProfileTranscriptEntry) entries.get(0);

        assertThat(entry.getModelConfiguration()).isEqualTo(modelConfiguration);

        ChatMemory restored = org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory.withMaxTokens(500);
        mapper.seedTranscriptWithHiddenExchange(restored, Collections.singletonList(entry), null);
        AssistantProfileSwitchMessage restoredMessage =
            ((org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory) restored).latestProfileSwitchMessage();
        assertThat(restoredMessage.getModelConfiguration()).isEqualTo(modelConfiguration);
    }
}

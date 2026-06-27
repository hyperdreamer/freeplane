package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatTranscriptRecordTest {
    @Test
    public void deserializesLegacySelectedModelOverrideIntoModelConfigurationOverride() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        ChatTranscriptRecord record = objectMapper.readValue(
            "{\"selectedModelOverride\":\"openrouter|openai/gpt-4.1-mini\"}",
            ChatTranscriptRecord.class);

        assertThat(record.getSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(record.getModelConfigurationOverride().getModelSelection())
            .isEqualTo(AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"));
    }

    @Test
    public void ignoresInvalidPersistedTemperatureWithoutDroppingModelConfigurationOverride() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        ChatTranscriptRecord record = objectMapper.readValue(
            "{\"modelConfigurationOverride\":{\"thinkingEffort\":\"LOW\",\"temperature\":\"broken\"}}",
            ChatTranscriptRecord.class);

        assertThat(record.getModelConfigurationOverride().getThinkingEffort()).isEqualTo(AiThinkingEffort.LOW);
        assertThat(record.getModelConfigurationOverride().getTemperature()).isNull();
    }

    @Test
    public void serializesModelConfigurationOverrideWithoutLegacySelectedModelOverride() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatTranscriptRecord record = new ChatTranscriptRecord();
        record.setModelConfigurationOverride(AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
            AiThinkingEffort.LOW,
            Double.valueOf(0.2)));

        String json = objectMapper.writeValueAsString(record);

        assertThat(json).contains("modelConfigurationOverride");
        assertThat(json).doesNotContain("selectedModelOverride");
    }
}

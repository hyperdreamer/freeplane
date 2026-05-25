package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiToolAvailability;
import org.junit.Test;

public class AiRequestMappingsTest {

    @Test
    public void mapsExplicitModelSelectionToInternalSelectionValue() {
        assertThat(AiRequestMappings.toSelectedModelOverride(
            AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini")))
            .isEqualTo("openrouter|openai/gpt-4.1-mini");
    }

    @Test
    public void mapsCurrentModelSelectionToNullOverride() {
        assertThat(AiRequestMappings.toSelectedModelOverride(AiModelSelection.current())).isNull();
    }

    @Test
    public void mapsToolAvailabilityValuesToInternalEnum() {
        assertThat(AiRequestMappings.toChatToolAvailability(AiToolAvailability.CURRENT)).isNull();
        assertThat(AiRequestMappings.toChatToolAvailability(AiToolAvailability.DISABLED))
            .isEqualTo(ChatToolAvailability.DISABLED);
        assertThat(AiRequestMappings.toChatToolAvailability(AiToolAvailability.READING))
            .isEqualTo(ChatToolAvailability.READING);
        assertThat(AiRequestMappings.toChatToolAvailability(AiToolAvailability.EDITING))
            .isEqualTo(ChatToolAvailability.EDITING);
    }
}

package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiModelConfiguration;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRequestMappingsTest {

    @Test
    public void mapsExplicitModelSelectionToInternalSelectionValue() {
        assertThat(AiRequestMappings.toSelectedModelOverride(
            AiModelConfiguration.builder().modelSelection(AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini")).build()))
            .isEqualTo("openrouter|openai/gpt-4.1-mini");
    }

    @Test
    public void mapsDefaultModelSelectionToNullOverride() {
        assertThat(AiRequestMappings.toSelectedModelOverride(
            AiModelConfiguration.builder().modelSelection(AiModelSelection.defaultModel()).build())).isNull();
    }

    @Test
    public void mapsToolAvailabilityValuesToInternalEnum() {
        assertThat(AiRequestMappings.toToolAvailabilityLevel(AiToolAvailability.CURRENT)).isNull();
        assertThat(AiRequestMappings.toToolAvailabilityLevel(AiToolAvailability.DISABLED))
            .isEqualTo(ToolAvailabilityLevel.DISABLED);
        assertThat(AiRequestMappings.toToolAvailabilityLevel(AiToolAvailability.READING))
            .isEqualTo(ToolAvailabilityLevel.READING);
        assertThat(AiRequestMappings.toToolAvailabilityLevel(AiToolAvailability.EDITING))
            .isEqualTo(ToolAvailabilityLevel.EDITING);
    }
}

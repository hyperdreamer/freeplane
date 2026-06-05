package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevelSettings;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PromptToolSelectionResolverTest {
    @Test
    public void resolveEffectiveToolAvailability_usesCurrentSetting_whenSelectionIsBlank() {
        ToolAvailabilityLevelSettings settings = mock(ToolAvailabilityLevelSettings.class);
        when(settings.getToolAvailability()).thenReturn(ToolAvailabilityLevel.READING);
        PromptToolSelectionResolver uut = new PromptToolSelectionResolver(settings);

        assertThat(uut.resolveEffectiveToolAvailability("  ")).isEqualTo(ToolAvailabilityLevel.READING);
        assertThat(uut.resolveShownChatOverride("  ")).isNull();
    }

    @Test
    public void resolveEffectiveToolAvailability_usesPromptSelection_whenSpecified() {
        ToolAvailabilityLevelSettings settings = mock(ToolAvailabilityLevelSettings.class);
        when(settings.getToolAvailability()).thenReturn(ToolAvailabilityLevel.READING);
        PromptToolSelectionResolver uut = new PromptToolSelectionResolver(settings);

        assertThat(uut.resolveEffectiveToolAvailability("disabled")).isEqualTo(ToolAvailabilityLevel.DISABLED);
        assertThat(uut.resolveShownChatOverride("disabled")).isEqualTo(ToolAvailabilityLevel.DISABLED);
    }

    @Test
    public void resolveEffectiveToolAvailability_defaultsInvalidSelectionToEditing() {
        ToolAvailabilityLevelSettings settings = mock(ToolAvailabilityLevelSettings.class);
        when(settings.getToolAvailability()).thenReturn(ToolAvailabilityLevel.READING);
        PromptToolSelectionResolver uut = new PromptToolSelectionResolver(settings);

        assertThat(uut.resolveEffectiveToolAvailability("unexpected")).isEqualTo(ToolAvailabilityLevel.EDITING);
        assertThat(uut.resolveShownChatOverride("unexpected")).isEqualTo(ToolAvailabilityLevel.EDITING);
    }
}

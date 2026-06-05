package org.freeplane.plugin.ai.chat.ui;

import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AIChatMessageStyleSettingsTest {

    @Test
    public void parseChatFontScalingReturnsDefaultWhenValueMissing() {
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling(null)).isEqualTo(100);
    }

    @Test
    public void parseChatFontScalingReturnsDefaultWhenValueIsInvalid() {
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("abc")).isEqualTo(100);
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("0")).isEqualTo(100);
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("24")).isEqualTo(100);
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("401")).isEqualTo(100);
    }

    @Test
    public void parseChatFontScalingReturnsParsedValueWhenWithinRange() {
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("25")).isEqualTo(25);
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("150")).isEqualTo(150);
        assertThat(AIChatMessageStyleSettings.parseChatFontScaling("400")).isEqualTo(400);
    }

    @Test
    public void settingsReadFontScalingFromResourceController() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(AIChatMessageStyleSettings.CHAT_FONT_SCALING_PROPERTY))
            .thenReturn("175");

        AIChatMessageStyleSettings uut = new AIChatMessageStyleSettings(resourceController);

        assertThat(uut.getChatFontScaling()).isEqualTo(175);
    }
}

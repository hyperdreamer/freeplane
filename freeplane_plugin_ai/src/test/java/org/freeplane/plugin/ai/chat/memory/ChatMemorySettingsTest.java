package org.freeplane.plugin.ai.chat.memory;

import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChatMemorySettingsTest {

    @Test
    public void parseMaximumTokenCountDefaultsOnEmptyInput() {
        assertThat(ChatMemorySettings.parseMaximumTokenCount(null)).isEqualTo(65536);
        assertThat(ChatMemorySettings.parseMaximumTokenCount("")).isEqualTo(65536);
        assertThat(ChatMemorySettings.parseMaximumTokenCount("   ")).isEqualTo(65536);
    }

    @Test
    public void parseMaximumTokenCountDefaultsOnInvalidInput() {
        assertThat(ChatMemorySettings.parseMaximumTokenCount("nope")).isEqualTo(65536);
        assertThat(ChatMemorySettings.parseMaximumTokenCount("-5")).isEqualTo(65536);
        assertThat(ChatMemorySettings.parseMaximumTokenCount("0")).isEqualTo(65536);
    }

    @Test
    public void parseMaximumTokenCountParsesPositiveInput() {
        assertThat(ChatMemorySettings.parseMaximumTokenCount("8192")).isEqualTo(8192);
        assertThat(ChatMemorySettings.parseMaximumTokenCount(" 9000 ")).isEqualTo(9000);
        assertThat(ChatMemorySettings.parseMaximumTokenCount("3000")).isEqualTo(3000);
    }

    @Test
    public void getMaximumTokenCountReadsCurrentPropertyValueOnEachCall() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty("ai_chat_memory_maximum_token_count"))
            .thenReturn("4096")
            .thenReturn("8192");
        ChatMemorySettings uut = new ChatMemorySettings(resourceController);

        assertThat(uut.getMaximumTokenCount()).isEqualTo(4096);
        assertThat(uut.getMaximumTokenCount()).isEqualTo(8192);
    }
}

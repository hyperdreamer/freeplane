package org.freeplane.plugin.ai.chat.ui;

import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatThinkingEffortSelectorTest {
    @Test
    public void selectorReportsExplicitThinkingEffortWithoutPrefixOrDefaultItem() {
        AtomicReference<AiThinkingEffort> seenSelection = new AtomicReference<AiThinkingEffort>(
            AiThinkingEffort.LOW);
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getDefaultModelConfiguration()).thenReturn(
            AIModelConfiguration.of(null, AiThinkingEffort.MEDIUM, null));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_chat_thinking_effort.tooltip"))
                .thenReturn("tooltip");
            for (AiThinkingEffort effort : AiThinkingEffort.values()) {
                textUtils.when(() -> TextUtils.getText("OptionPanel.AiThinkingEffort." + effort.name()))
                    .thenReturn(effort.name());
            }
            ChatThinkingEffortSelector selector = new ChatThinkingEffortSelector(configuration);
            selector.setExplicitUserThinkingEffortSelectionChangeListener(seenSelection::set);
            assertThat(selector.getComboBox().getItemCount()).isEqualTo(AiThinkingEffort.values().length);
            assertThat(selector.getComboBox().getItemAt(0).getText()).isEqualTo("MAX");
            assertThat(selector.getComboBox().getMinimumSize()).isEqualTo(selector.getComboBox().getPreferredSize());
            assertThat(selector.getComboBox().getMaximumSize()).isEqualTo(selector.getComboBox().getPreferredSize());

            selector.setDisplayedThinkingEffortOverride(AiThinkingEffort.HIGH);
            assertThat(((ChatThinkingEffortSelector.Option) selector.getComboBox().getSelectedItem())
                .getThinkingEffort()).isEqualTo(AiThinkingEffort.HIGH);
            verify(configuration, never()).setThinkingEffortValue(AiThinkingEffort.HIGH);

            selector.getComboBox().setSelectedIndex(3);
            assertThat(seenSelection.get()).isEqualTo(AiThinkingEffort.MEDIUM);
            verify(configuration).setThinkingEffortValue(AiThinkingEffort.MEDIUM);
        }
    }
}

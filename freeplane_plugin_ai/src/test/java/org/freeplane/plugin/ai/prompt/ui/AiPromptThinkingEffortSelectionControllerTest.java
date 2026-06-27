package org.freeplane.plugin.ai.prompt.ui;

import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.util.TextUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

public class AiPromptThinkingEffortSelectionControllerTest {
    @Test
    public void selectorUsesNullCurrentOptionAndExplicitThinkingValues() {
        AtomicReference<AiThinkingEffort> seenSelection = new AtomicReference<AiThinkingEffort>();
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_current"))
                .thenReturn("Current");
            for (AiThinkingEffort effort : AiThinkingEffort.values()) {
                textUtils.when(() -> TextUtils.getText("OptionPanel.AiThinkingEffort." + effort.name()))
                    .thenReturn(effort.name());
            }
            AiPromptThinkingEffortSelectionController controller = new AiPromptThinkingEffortSelectionController();
            controller.setThinkingEffortSelectionChangeListener(seenSelection::set);

            assertThat(controller.getThinkingEffortComboBox().getItemAt(0).toString()).isEqualTo("Current");

            controller.setSelectedThinkingEffort(AiThinkingEffort.LOW);
            assertThat(controller.getSelectedThinkingEffort()).isEqualTo(AiThinkingEffort.LOW);
            assertThat(seenSelection.get()).isNull();

            controller.getThinkingEffortComboBox().setSelectedIndex(0);
            assertThat(seenSelection.get()).isNull();

            controller.getThinkingEffortComboBox().setSelectedIndex(4);
            assertThat(seenSelection.get()).isEqualTo(AiThinkingEffort.MEDIUM);
        }
    }
}

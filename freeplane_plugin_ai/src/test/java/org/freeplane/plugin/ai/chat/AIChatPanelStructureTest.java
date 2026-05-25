package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class AIChatPanelStructureTest {

    @Test
    public void noLongerOwnsSingletonRequestFields() {
        List<String> fieldNames = Arrays.stream(AIChatPanel.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toList());

        assertThat(fieldNames)
            .doesNotContain("chatPromptRunner", "chatRequestFlow", "activeVisibleAiRequestCallbacks");
    }
}

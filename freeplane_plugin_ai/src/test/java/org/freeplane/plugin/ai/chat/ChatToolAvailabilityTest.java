package org.freeplane.plugin.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ChatToolAvailabilityTest {

    @Test
    public void fromPreferenceValueDefaultsToEditingForMissingBlankAndInvalidValues() {
        assertThat(ChatToolAvailability.fromPreferenceValue(null)).isEqualTo(ChatToolAvailability.EDITING);
        assertThat(ChatToolAvailability.fromPreferenceValue("")).isEqualTo(ChatToolAvailability.EDITING);
        assertThat(ChatToolAvailability.fromPreferenceValue("   ")).isEqualTo(ChatToolAvailability.EDITING);
        assertThat(ChatToolAvailability.fromPreferenceValue("unknown")).isEqualTo(ChatToolAvailability.EDITING);
    }

    @Test
    public void readingAllowsOnlyReadingAndNodeSelectionTools() {
        assertThat(ChatToolAvailability.READING.allowedToolNames()).containsExactly(
            "readNodesWithDescendants",
            "readNodesWithDescendantsAsPlainText",
            "getSelectedMapAndNodeIdentifiers",
            "searchNodes",
            "selectSingleNode",
            "getApiDocumentation");
        assertThat(ChatToolAvailability.READING.allowsTool("selectSingleNode")).isTrue();
        assertThat(ChatToolAvailability.READING.allowsTool("getApiDocumentation")).isTrue();
        assertThat(ChatToolAvailability.READING.allowsTool("edit")).isFalse();
        assertThat(ChatToolAvailability.READING.includesTools()).isTrue();
    }

    @Test
    public void editingIncludesReadingAndEditingTools() {
        assertThat(ChatToolAvailability.EDITING.allowedToolNames()).containsAll(
            ChatToolAvailability.READING.allowedToolNames());
        assertThat(ChatToolAvailability.EDITING.allowsTool("fetchNodesForEditing")).isTrue();
        assertThat(ChatToolAvailability.EDITING.allowsTool("edit")).isTrue();
        assertThat(ChatToolAvailability.DISABLED.includesTools()).isFalse();
    }
}

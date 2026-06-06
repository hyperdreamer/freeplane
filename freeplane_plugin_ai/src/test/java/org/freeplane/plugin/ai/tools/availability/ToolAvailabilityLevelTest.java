package org.freeplane.plugin.ai.tools.availability;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolAvailabilityLevelTest {

    @Test
    public void fromPreferenceValueDefaultsToEditingForMissingBlankAndInvalidValues() {
        assertThat(ToolAvailabilityLevel.fromPreferenceValue(null)).isEqualTo(ToolAvailabilityLevel.EDITING);
        assertThat(ToolAvailabilityLevel.fromPreferenceValue("")).isEqualTo(ToolAvailabilityLevel.EDITING);
        assertThat(ToolAvailabilityLevel.fromPreferenceValue("   ")).isEqualTo(ToolAvailabilityLevel.EDITING);
        assertThat(ToolAvailabilityLevel.fromPreferenceValue("unknown")).isEqualTo(ToolAvailabilityLevel.EDITING);
    }

    @Test
    public void readingAllowsOnlyReadingAndNodeSelectionTools() {
        assertThat(ToolAvailabilityLevel.READING.allowedToolNames()).containsExactly(
            "readNodesWithDescendants",
            "readNodesWithDescendantsAsPlainText",
            "getSelectedMapAndNodeIdentifiers",
            "searchNodes",
            "selectSingleNode",
            "getApiDocumentation");
        assertThat(ToolAvailabilityLevel.READING.allowsTool("selectSingleNode")).isTrue();
        assertThat(ToolAvailabilityLevel.READING.allowsTool("getApiDocumentation")).isTrue();
        assertThat(ToolAvailabilityLevel.READING.allowsTool("edit")).isFalse();
        assertThat(ToolAvailabilityLevel.READING.includesTools()).isTrue();
    }

    @Test
    public void editingIncludesReadingEditingAndFormulaTools() {
        assertThat(ToolAvailabilityLevel.EDITING.allowedToolNames()).containsAll(
            ToolAvailabilityLevel.READING.allowedToolNames());
        assertThat(ToolAvailabilityLevel.EDITING.allowsTool("fetchNodesForEditing")).isTrue();
        assertThat(ToolAvailabilityLevel.EDITING.allowsTool("edit")).isTrue();
        assertThat(ToolAvailabilityLevel.EDITING.allowsTool("previewFormulaUpdates")).isTrue();
        assertThat(ToolAvailabilityLevel.EDITING.allowsTool("applyFormulaUpdates")).isTrue();
        assertThat(ToolAvailabilityLevel.DISABLED.includesTools()).isFalse();
    }

    @Test
    public void scriptExecutionIncludesEditingToolSet() {
        assertThat(ToolAvailabilityLevel.SCRIPT_EXECUTION.allowedToolNames())
            .containsAll(ToolAvailabilityLevel.EDITING.allowedToolNames());
    }
}

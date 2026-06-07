package org.freeplane.plugin.ai.prompt.ui;

import javax.swing.JComboBox;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiPromptToolSelectionControllerTest {
    @Test
    public void constructor_createsNonEditableDropdownWithAllOptions() {
        AiPromptToolSelectionController uut = new AiPromptToolSelectionController(
            "Use current tools",
            "Script execution",
            "Editing",
            "Reading",
            "Disabled");
        JComboBox<AiPromptToolSelectionController.ToolSelectionOption> comboBox = uut.getToolSelectionComboBox();

        assertThat(comboBox.isEditable()).isFalse();
        assertThat(comboBox.getItemCount()).isEqualTo(5);
        assertThat(comboBox.getItemAt(0).toString()).isEqualTo("Use current tools");
        assertThat(comboBox.getItemAt(1).toString()).isEqualTo("Script execution");
        assertThat(comboBox.getItemAt(2).toString()).isEqualTo("Editing");
        assertThat(comboBox.getItemAt(3).toString()).isEqualTo("Reading");
        assertThat(comboBox.getItemAt(4).toString()).isEqualTo("Disabled");
    }

    @Test
    public void setSelectedToolAvailabilitySelectionValue_defaultsUnknownValuesToCurrentTools() {
        AiPromptToolSelectionController uut = new AiPromptToolSelectionController(
            "Use current tools",
            "Script execution",
            "Editing",
            "Reading",
            "Disabled");

        uut.setSelectedToolAvailabilitySelectionValue("reading");
        assertThat(uut.getSelectedToolAvailabilitySelectionValue()).isEqualTo("reading");

        uut.setSelectedToolAvailabilitySelectionValue("unsupported");
        assertThat(uut.getSelectedToolAvailabilitySelectionValue()).isEmpty();
    }
}

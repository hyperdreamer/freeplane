package org.freeplane.plugin.ai.prompt.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

public class AiPromptToolSelectionController {
    private final JComboBox<ToolSelectionOption> toolSelectionComboBox;
    private final List<ToolSelectionOption> options;
    private boolean selectionUpdateInProgress;
    private Consumer<String> toolSelectionChangeListener;

    public AiPromptToolSelectionController() {
        this(
            TextUtils.getText("ai_prompt_current"),
            TextUtils.getText("OptionPanel.ToolAvailabilityLevel.SCRIPT_EXECUTION"),
            TextUtils.getText("OptionPanel.ToolAvailabilityLevel.EDITING"),
            TextUtils.getText("OptionPanel.ToolAvailabilityLevel.READING"),
            TextUtils.getText("OptionPanel.ToolAvailabilityLevel.DISABLED"));
    }

    AiPromptToolSelectionController(String useCurrentToolsLabel,
                                    String scriptExecutionLabel,
                                    String editingLabel,
                                    String readingLabel,
                                    String disabledLabel) {
        options = Arrays.asList(
            new ToolSelectionOption("", useCurrentToolsLabel),
            new ToolSelectionOption("script_execution", scriptExecutionLabel),
            new ToolSelectionOption("editing", editingLabel),
            new ToolSelectionOption("reading", readingLabel),
            new ToolSelectionOption("disabled", disabledLabel));
        DefaultComboBoxModel<ToolSelectionOption> comboBoxModel =
            new DefaultComboBoxModel<ToolSelectionOption>(
                options.toArray(new ToolSelectionOption[0]));
        toolSelectionComboBox = JComboBoxFactory.create(comboBoxModel);
        toolSelectionComboBox.setEditable(false);
        toolSelectionComboBox.setPrototypeDisplayValue(createPrototypeDisplayValue());
        toolSelectionComboBox.addActionListener(event -> onToolSelectionChanged());
        setSelectedToolAvailabilitySelectionValue("");
    }

    public JComboBox<ToolSelectionOption> getToolSelectionComboBox() {
        return toolSelectionComboBox;
    }

    public void setToolSelectionChangeListener(Consumer<String> toolSelectionChangeListener) {
        this.toolSelectionChangeListener = toolSelectionChangeListener;
    }

    public void setSelectedToolAvailabilitySelectionValue(String selectionValue) {
        selectionUpdateInProgress = true;
        try {
            toolSelectionComboBox.setSelectedItem(resolveOption(selectionValue));
        } finally {
            selectionUpdateInProgress = false;
        }
    }

    public String getSelectedToolAvailabilitySelectionValue() {
        Object selectedItem = toolSelectionComboBox.getSelectedItem();
        if (!(selectedItem instanceof ToolSelectionOption)) {
            return "";
        }
        return ((ToolSelectionOption) selectedItem).selectionValue;
    }

    private void onToolSelectionChanged() {
        if (selectionUpdateInProgress) {
            return;
        }
        if (toolSelectionChangeListener != null) {
            toolSelectionChangeListener.accept(getSelectedToolAvailabilitySelectionValue());
        }
    }

    private ToolSelectionOption createPrototypeDisplayValue() {
        ToolSelectionOption widestOption = options.get(0);
        int widestWidth = -1;
        for (ToolSelectionOption option : options) {
            int optionWidth = toolSelectionComboBox.getFontMetrics(toolSelectionComboBox.getFont())
                .stringWidth(option.displayText);
            if (optionWidth > widestWidth) {
                widestWidth = optionWidth;
                widestOption = option;
            }
        }
        return new ToolSelectionOption(widestOption.selectionValue, widestOption.displayText + "xxx");
    }

    private ToolSelectionOption resolveOption(String selectionValue) {
        String normalized = normalizeSelectionValue(selectionValue);
        for (ToolSelectionOption option : options) {
            if (option.selectionValue.equals(normalized)) {
                return option;
            }
        }
        return options.get(0);
    }

    private String normalizeSelectionValue(String selectionValue) {
        if (selectionValue == null) {
            return "";
        }
        String normalized = selectionValue.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "" : normalized;
    }

    public static final class ToolSelectionOption {
        private final String selectionValue;
        private final String displayText;

        ToolSelectionOption(String selectionValue, String displayText) {
            this.selectionValue = selectionValue == null ? "" : selectionValue;
            this.displayText = displayText == null ? "" : displayText;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }
}

package org.freeplane.plugin.ai.prompt.ui;

import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.TextUtils;

public class AiPromptThinkingEffortSelectionController {
    private final JComboBox<ThinkingEffortOption> thinkingEffortComboBox;
    private boolean thinkingEffortSelectionUpdateInProgress;
    private Consumer<AiThinkingEffort> thinkingEffortSelectionChangeListener;

    public AiPromptThinkingEffortSelectionController() {
        this.thinkingEffortComboBox = JComboBoxFactory.create(new DefaultComboBoxModel<ThinkingEffortOption>());
        this.thinkingEffortComboBox.setEditable(false);
        this.thinkingEffortComboBox.setModel(createModel());
        this.thinkingEffortComboBox.addActionListener(event -> onThinkingEffortSelectionChanged());
    }

    public JComboBox<ThinkingEffortOption> getThinkingEffortComboBox() {
        return thinkingEffortComboBox;
    }

    public void setThinkingEffortSelectionChangeListener(
        Consumer<AiThinkingEffort> thinkingEffortSelectionChangeListener) {
        this.thinkingEffortSelectionChangeListener = thinkingEffortSelectionChangeListener;
    }

    public void setSelectedThinkingEffort(AiThinkingEffort thinkingEffort) {
        thinkingEffortSelectionUpdateInProgress = true;
        try {
            for (int index = 0; index < thinkingEffortComboBox.getItemCount(); index++) {
                ThinkingEffortOption option = thinkingEffortComboBox.getItemAt(index);
                if (option != null && option.getThinkingEffort() == thinkingEffort) {
                    thinkingEffortComboBox.setSelectedIndex(index);
                    return;
                }
            }
            thinkingEffortComboBox.setSelectedIndex(0);
        }
        finally {
            thinkingEffortSelectionUpdateInProgress = false;
        }
    }

    public AiThinkingEffort getSelectedThinkingEffort() {
        Object selectedItem = thinkingEffortComboBox.getSelectedItem();
        return selectedItem instanceof ThinkingEffortOption
            ? ((ThinkingEffortOption) selectedItem).getThinkingEffort()
            : null;
    }

    private DefaultComboBoxModel<ThinkingEffortOption> createModel() {
        DefaultComboBoxModel<ThinkingEffortOption> model = new DefaultComboBoxModel<ThinkingEffortOption>();
        model.addElement(new ThinkingEffortOption(null, TextUtils.getText("ai_prompt_current")));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.MAX));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.XHIGH));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.HIGH));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.MEDIUM));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.LOW));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.MINIMAL));
        model.addElement(ThinkingEffortOption.of(AiThinkingEffort.NONE));
        return model;
    }

    private void onThinkingEffortSelectionChanged() {
        if (thinkingEffortSelectionUpdateInProgress) {
            return;
        }
        if (thinkingEffortSelectionChangeListener != null) {
            thinkingEffortSelectionChangeListener.accept(getSelectedThinkingEffort());
        }
    }

    public static class ThinkingEffortOption {
        private final AiThinkingEffort thinkingEffort;
        private final String text;

        private ThinkingEffortOption(AiThinkingEffort thinkingEffort, String text) {
            this.thinkingEffort = thinkingEffort;
            this.text = text;
        }

        static ThinkingEffortOption of(AiThinkingEffort thinkingEffort) {
            return new ThinkingEffortOption(
                thinkingEffort,
                TextUtils.getText("OptionPanel.AiThinkingEffort." + thinkingEffort.name()));
        }

        public AiThinkingEffort getThinkingEffort() {
            return thinkingEffort;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}

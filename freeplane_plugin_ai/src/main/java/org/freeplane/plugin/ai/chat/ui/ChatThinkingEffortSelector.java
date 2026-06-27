package org.freeplane.plugin.ai.chat.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;

class ChatThinkingEffortSelector {
    private final AIProviderConfiguration configuration;
    private final JComboBox<Option> comboBox;
    private boolean isSelectionUpdateInProgress;
    private Consumer<AiThinkingEffort> explicitUserThinkingEffortSelectionChangeListener;
    private AiThinkingEffort displayedThinkingEffortOverride;

    ChatThinkingEffortSelector(AIProviderConfiguration configuration) {
        this.configuration = configuration;
        this.comboBox = new JComboBox<Option>();
        this.comboBox.setRenderer(new Renderer());
        this.comboBox.setToolTipText(TextUtils.getText("ai_chat_thinking_effort.tooltip"));
        this.comboBox.setModel(createModel());
        keepConstantSize(this.comboBox);
        this.comboBox.addActionListener(event -> onSelectionChanged());
        applyDisplayedThinkingEffort();
    }

    JComboBox<Option> getComboBox() {
        return comboBox;
    }

    void setExplicitUserThinkingEffortSelectionChangeListener(
        Consumer<AiThinkingEffort> explicitUserThinkingEffortSelectionChangeListener) {
        this.explicitUserThinkingEffortSelectionChangeListener = explicitUserThinkingEffortSelectionChangeListener;
    }

    void setDisplayedThinkingEffortOverride(AiThinkingEffort thinkingEffortOverride) {
        displayedThinkingEffortOverride = thinkingEffortOverride;
        applyDisplayedThinkingEffort();
    }

    private void applyDisplayedThinkingEffort() {
        AiThinkingEffort displayedThinkingEffort = effectiveThinkingEffort();
        isSelectionUpdateInProgress = true;
        try {
            for (int index = 0; index < comboBox.getItemCount(); index++) {
                Option option = comboBox.getItemAt(index);
                if (option != null && option.getThinkingEffort() == displayedThinkingEffort) {
                    comboBox.setSelectedIndex(index);
                    return;
                }
            }
            comboBox.setSelectedIndex(0);
        }
        finally {
            isSelectionUpdateInProgress = false;
        }
    }

    private AiThinkingEffort effectiveThinkingEffort() {
        if (displayedThinkingEffortOverride != null) {
            return displayedThinkingEffortOverride;
        }
        AIModelConfiguration defaultModelConfiguration = configuration.getDefaultModelConfiguration();
        AiThinkingEffort thinkingEffort = defaultModelConfiguration == null
            ? null
            : defaultModelConfiguration.getThinkingEffort();
        return thinkingEffort == null ? AiThinkingEffort.MEDIUM : thinkingEffort;
    }

    private void keepConstantSize(JComboBox<Option> comboBox) {
        Dimension size = comboBox.getPreferredSize();
        comboBox.setMinimumSize(size);
        comboBox.setPreferredSize(size);
        comboBox.setMaximumSize(size);
    }

    private DefaultComboBoxModel<Option> createModel() {
        DefaultComboBoxModel<Option> model = new DefaultComboBoxModel<Option>();
        model.addElement(Option.of(AiThinkingEffort.MAX));
        model.addElement(Option.of(AiThinkingEffort.XHIGH));
        model.addElement(Option.of(AiThinkingEffort.HIGH));
        model.addElement(Option.of(AiThinkingEffort.MEDIUM));
        model.addElement(Option.of(AiThinkingEffort.LOW));
        model.addElement(Option.of(AiThinkingEffort.MINIMAL));
        model.addElement(Option.of(AiThinkingEffort.NONE));
        return model;
    }

    private void onSelectionChanged() {
        if (isSelectionUpdateInProgress) {
            return;
        }
        Object selectedItem = comboBox.getSelectedItem();
        if (!(selectedItem instanceof Option)) {
            return;
        }
        AiThinkingEffort thinkingEffort = ((Option) selectedItem).getThinkingEffort();
        displayedThinkingEffortOverride = null;
        configuration.setThinkingEffortValue(thinkingEffort);
        notifyExplicitUserThinkingEffortSelectionChange(thinkingEffort);
    }

    private void notifyExplicitUserThinkingEffortSelectionChange(AiThinkingEffort thinkingEffort) {
        if (explicitUserThinkingEffortSelectionChangeListener != null) {
            explicitUserThinkingEffortSelectionChangeListener.accept(thinkingEffort);
        }
    }

    static class Option {
        private final AiThinkingEffort thinkingEffort;
        private final String text;

        private Option(AiThinkingEffort thinkingEffort, String text) {
            this.thinkingEffort = thinkingEffort;
            this.text = text;
        }

        static Option of(AiThinkingEffort thinkingEffort) {
            return new Option(thinkingEffort,
                TextUtils.getText("OptionPanel.AiThinkingEffort." + thinkingEffort.name()));
        }

        AiThinkingEffort getThinkingEffort() {
            return thinkingEffort;
        }

        String getText() {
            return text;
        }
    }

    private static class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            String text = value instanceof Option ? ((Option) value).getText() : "";
            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}

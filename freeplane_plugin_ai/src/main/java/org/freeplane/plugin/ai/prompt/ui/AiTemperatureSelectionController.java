package org.freeplane.plugin.ai.prompt.ui;

import java.util.function.Function;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelTemperatureStorage;

public class AiTemperatureSelectionController {
    private static final String[] PRESET_VALUES = {"0", "0.2", "0.5", "0.7", "1.0"};

    private final boolean includeCurrent;
    private final Function<String, String> customInputProvider;
    private final JComboBox<TemperatureOption> comboBox;
    private final DefaultComboBoxModel<TemperatureOption> model;
    private TemperatureOption customValueOption;
    private TemperatureOption previousOption;
    private boolean selectionUpdateInProgress;
    private java.util.function.Consumer<AiTemperature> temperatureSelectionChangeListener;

    public AiTemperatureSelectionController(boolean includeCurrent) {
        this(includeCurrent, null);
    }

    AiTemperatureSelectionController(boolean includeCurrent, Function<String, String> customInputProvider) {
        this.includeCurrent = includeCurrent;
        this.customInputProvider = customInputProvider;
        this.model = new DefaultComboBoxModel<TemperatureOption>();
        this.comboBox = JComboBoxFactory.create(model);
        this.comboBox.setEditable(false);
        buildModel();
        this.comboBox.addActionListener(event -> onSelectionChanged());
        setSelectedTemperature(null);
    }

    public JComboBox<TemperatureOption> getComboBox() {
        return comboBox;
    }

    public void setTemperatureSelectionChangeListener(
        java.util.function.Consumer<AiTemperature> temperatureSelectionChangeListener) {
        this.temperatureSelectionChangeListener = temperatureSelectionChangeListener;
    }

    public void setSelectedTemperature(AiTemperature temperature) {
        selectionUpdateInProgress = true;
        try {
            TemperatureOption option = optionForTemperature(temperature);
            comboBox.setSelectedItem(option);
            previousOption = option;
        }
        finally {
            selectionUpdateInProgress = false;
        }
    }

    public AiTemperature getSelectedTemperature() {
        Object selectedItem = comboBox.getSelectedItem();
        return selectedItem instanceof TemperatureOption
            ? ((TemperatureOption) selectedItem).getTemperature()
            : null;
    }

    private void buildModel() {
        if (includeCurrent) {
            model.addElement(TemperatureOption.current(TextUtils.getText("ai_prompt_current")));
        }
        model.addElement(TemperatureOption.modelDefault(TextUtils.getText("ai_temperature_model_default")));
        for (String presetValue : PRESET_VALUES) {
            model.addElement(TemperatureOption.numeric(presetValue, presetValue));
        }
        model.addElement(TemperatureOption.customAction(TextUtils.getText("ai_temperature_custom")));
    }

    private TemperatureOption optionForTemperature(AiTemperature temperature) {
        if (temperature == null) {
            return includeCurrent ? model.getElementAt(0) : modelDefaultOption();
        }
        if (temperature.isModelDefault()) {
            return modelDefaultOption();
        }
        String value = AIModelTemperatureStorage.toPreferenceValue(temperature);
        for (int index = 0; index < model.getSize(); index++) {
            TemperatureOption option = model.getElementAt(index);
            if (option.isNumeric()
                && AIModelTemperatureStorage.sameNumericValue(option.value, value)) {
                removeCustomValueOptionIfNeeded();
                return option;
            }
        }
        return customValueOption(value);
    }

    private TemperatureOption modelDefaultOption() {
        for (int index = 0; index < model.getSize(); index++) {
            TemperatureOption option = model.getElementAt(index);
            if (option.isModelDefault()) {
                return option;
            }
        }
        return model.getElementAt(0);
    }

    private TemperatureOption customValueOption(String value) {
        if (customValueOption != null
            && AIModelTemperatureStorage.sameNumericValue(customValueOption.value, value)) {
            return customValueOption;
        }
        removeCustomValueOptionIfNeeded();
        customValueOption = TemperatureOption.numeric(value, value);
        model.insertElementAt(customValueOption, model.getSize() - 1);
        return customValueOption;
    }

    private void removeCustomValueOptionIfNeeded() {
        if (customValueOption != null) {
            model.removeElement(customValueOption);
            customValueOption = null;
        }
    }

    private void onSelectionChanged() {
        if (selectionUpdateInProgress) {
            return;
        }
        Object selectedItem = comboBox.getSelectedItem();
        if (!(selectedItem instanceof TemperatureOption)) {
            return;
        }
        TemperatureOption option = (TemperatureOption) selectedItem;
        if (option.isCustomAction()) {
            handleCustomSelection();
            return;
        }
        previousOption = option;
        notifyTemperatureSelectionChange(option.getTemperature());
    }

    private void handleCustomSelection() {
        String input = requestCustomTemperature();
        if (input == null) {
            restorePreviousSelection();
            return;
        }
        AiTemperature temperature = AIModelTemperatureStorage.fromStoredValue(input);
        if (temperature == null || !temperature.isNumeric()) {
            restorePreviousSelection();
            return;
        }
        TemperatureOption option = customValueOption(AIModelTemperatureStorage.toPreferenceValue(temperature));
        selectionUpdateInProgress = true;
        try {
            comboBox.setSelectedItem(option);
            previousOption = option;
        }
        finally {
            selectionUpdateInProgress = false;
        }
        notifyTemperatureSelectionChange(option.getTemperature());
    }

    private String requestCustomTemperature() {
        String initialValue = previousOption != null && previousOption.isNumeric()
            ? previousOption.value
            : "";
        if (customInputProvider != null) {
            return customInputProvider.apply(initialValue);
        }
        Object input = JOptionPane.showInputDialog(
            comboBox,
            TextUtils.getText("ai_temperature_custom_prompt"),
            TextUtils.getText("ai_prompt_temperature_label"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            initialValue);
        return input == null ? null : input.toString();
    }

    private void restorePreviousSelection() {
        selectionUpdateInProgress = true;
        try {
            comboBox.setSelectedItem(previousOption == null ? modelDefaultOption() : previousOption);
        }
        finally {
            selectionUpdateInProgress = false;
        }
    }

    private void notifyTemperatureSelectionChange(AiTemperature temperature) {
        if (temperatureSelectionChangeListener != null) {
            temperatureSelectionChangeListener.accept(temperature);
        }
    }

    public static class TemperatureOption {
        private final String value;
        private final String text;
        private final boolean current;
        private final boolean modelDefault;
        private final boolean customAction;

        private TemperatureOption(String value,
                                  String text,
                                  boolean current,
                                  boolean modelDefault,
                                  boolean customAction) {
            this.value = value;
            this.text = text;
            this.current = current;
            this.modelDefault = modelDefault;
            this.customAction = customAction;
        }

        static TemperatureOption current(String text) {
            return new TemperatureOption("", text, true, false, false);
        }

        static TemperatureOption modelDefault(String text) {
            return new TemperatureOption(AIModelTemperatureStorage.MODEL_DEFAULT_VALUE, text, false, true, false);
        }

        static TemperatureOption numeric(String value, String text) {
            return new TemperatureOption(value, text, false, false, false);
        }

        static TemperatureOption customAction(String text) {
            return new TemperatureOption("", text, false, false, true);
        }

        AiTemperature getTemperature() {
            if (current) {
                return null;
            }
            if (modelDefault) {
                return AiTemperature.modelDefault();
            }
            return AIModelTemperatureStorage.fromStoredValue(value);
        }

        boolean isNumeric() {
            return !current && !modelDefault && !customAction;
        }

        boolean isModelDefault() {
            return modelDefault;
        }

        boolean isCustomAction() {
            return customAction;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}

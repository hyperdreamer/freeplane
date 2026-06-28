package org.freeplane.core.resources.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Vector;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.TextUtils;
import com.jgoodies.forms.builder.DefaultFormBuilder;

public class ChoiceOrNumberProperty extends PropertyBean implements ActionListener {
    private final JComboBox<Option> comboBox;
    private final Vector<String> possibleValues;
    private final String customText;
    private final String blankValue;
    private Option customValueOption;
    private Option previousOption;
    private boolean updateInProgress;

    public ChoiceOrNumberProperty(String name,
                                  Collection<String> possibleValues,
                                  Collection<?> displayedItems,
                                  String customText,
                                  String blankValue) {
        super(name);
        this.possibleValues = new Vector<String>(possibleValues);
        this.customText = customText;
        this.blankValue = normalizeBlankValue(blankValue);
        this.comboBox = JComboBoxFactory.create();
        this.comboBox.setEditable(false);
        DefaultComboBoxModel<Option> model = new DefaultComboBoxModel<Option>();
        java.util.Iterator<String> valueIterator = this.possibleValues.iterator();
        java.util.Iterator<?> textIterator = displayedItems.iterator();
        while (valueIterator.hasNext() && textIterator.hasNext()) {
            model.addElement(Option.value(valueIterator.next(), String.valueOf(textIterator.next())));
        }
        model.addElement(Option.customAction(customText));
        this.comboBox.setModel(model);
        this.comboBox.addActionListener(this);
    }

    @Override
    public String getValue() {
        Object selectedItem = comboBox.getSelectedItem();
        if (selectedItem instanceof Option && !((Option) selectedItem).customAction) {
            return ((Option) selectedItem).value;
        }
        return blankValue;
    }

    @Override
    public JComponent getValueComponent() {
        return comboBox;
    }

    @Override
    public void appendToForm(DefaultFormBuilder builder) {
        appendToForm(builder, comboBox);
    }

    @Override
    public void setEnabled(boolean enabled) {
        comboBox.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    @Override
    public void setValue(String value) {
        updateInProgress = true;
        try {
            Option option = optionForValue(normalizeValue(value));
            comboBox.setSelectedItem(option);
            previousOption = option;
        }
        finally {
            updateInProgress = false;
        }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (updateInProgress) {
            return;
        }
        Object selectedItem = comboBox.getSelectedItem();
        if (!(selectedItem instanceof Option)) {
            return;
        }
        Option option = (Option) selectedItem;
        if (option.customAction) {
            handleCustomSelection();
            return;
        }
        previousOption = option;
        firePropertyChangeEvent();
    }

    private void handleCustomSelection() {
        String input = requestCustomNumber();
        if (input == null || !isFiniteNumber(input)) {
            restorePreviousSelection();
            return;
        }
        Option option = customValueOption(formatNumber(Double.valueOf(input.trim()).doubleValue()));
        updateInProgress = true;
        try {
            comboBox.setSelectedItem(option);
            previousOption = option;
        }
        finally {
            updateInProgress = false;
        }
        firePropertyChangeEvent();
    }

    private String requestCustomNumber() {
        String initialValue = previousOption == null || !isFiniteNumber(previousOption.value)
            ? ""
            : previousOption.value;
        Object input = JOptionPane.showInputDialog(
            comboBox,
            TextUtils.getText("OptionPanel." + getName() + ".custom.prompt"),
            TextUtils.getText(getLabel()),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            initialValue);
        return input == null ? null : input.toString();
    }

    private void restorePreviousSelection() {
        updateInProgress = true;
        try {
            comboBox.setSelectedItem(previousOption == null ? optionForValue(blankValue) : previousOption);
        }
        finally {
            updateInProgress = false;
        }
    }

    private Option optionForValue(String value) {
        String normalized = value == null || value.trim().isEmpty() ? blankValue : value.trim();
        DefaultComboBoxModel<Option> model = model();
        for (int index = 0; index < model.getSize(); index++) {
            Option option = model.getElementAt(index);
            if (!option.customAction && sameValue(option.value, normalized)) {
                removeCustomValueOptionIfNeeded();
                return option;
            }
        }
        if (isFiniteNumber(normalized)) {
            return customValueOption(formatNumber(Double.valueOf(normalized).doubleValue()));
        }
        return optionForValue(blankValue);
    }

    private Option customValueOption(String value) {
        if (customValueOption != null && sameValue(customValueOption.value, value)) {
            return customValueOption;
        }
        removeCustomValueOptionIfNeeded();
        customValueOption = Option.value(value, value);
        model().insertElementAt(customValueOption, model().getSize() - 1);
        return customValueOption;
    }

    private void removeCustomValueOptionIfNeeded() {
        if (customValueOption != null) {
            model().removeElement(customValueOption);
            customValueOption = null;
        }
    }

    private DefaultComboBoxModel<Option> model() {
        @SuppressWarnings("unchecked")
        DefaultComboBoxModel<Option> model = (DefaultComboBoxModel<Option>) comboBox.getModel();
        return model;
    }

    private boolean sameValue(String first, String second) {
        if (first == null) {
            return second == null;
        }
        if (first.equals(second)) {
            return true;
        }
        if (isFiniteNumber(first) && isFiniteNumber(second)) {
            return Double.compare(Double.valueOf(first).doubleValue(), Double.valueOf(second).doubleValue()) == 0;
        }
        return false;
    }

    private String normalizeValue(String value) {
        return value == null || value.trim().isEmpty() ? blankValue : value.trim();
    }

    private String normalizeBlankValue(String value) {
        return value == null || value.trim().isEmpty() ? "" : value.trim();
    }

    private boolean isFiniteNumber(String value) {
        try {
            double number = Double.valueOf(value.trim()).doubleValue();
            return !Double.isNaN(number) && !Double.isInfinite(number);
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    private String formatNumber(double number) {
        for (String possibleValue : possibleValues) {
            if (isFiniteNumber(possibleValue)
                && Double.compare(Double.valueOf(possibleValue).doubleValue(), number) == 0) {
                return possibleValue;
            }
        }
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    private static class Option {
        private final String value;
        private final String text;
        private final boolean customAction;

        private Option(String value, String text, boolean customAction) {
            this.value = value;
            this.text = text;
            this.customAction = customAction;
        }

        static Option value(String value, String text) {
            return new Option(value, text, false);
        }

        static Option customAction(String text) {
            return new Option("", text, true);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}

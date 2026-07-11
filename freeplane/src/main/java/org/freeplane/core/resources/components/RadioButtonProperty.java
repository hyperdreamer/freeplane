package org.freeplane.core.resources.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JRadioButton;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.RenderedContentSupplier;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormSpecs;

public class RadioButtonProperty extends PropertyBean implements IPropertyControl, ActionListener {
    private final ButtonGroup group;
    private final Map<String, JRadioButton> buttonsByValue;
    private Vector<String> possibleValues;

    public RadioButtonProperty(final String name, final Collection<String> possibles,
                               final Collection<?> displayedItems) {
        super(name);
        fillPossibleValues(possibles);
        this.group = new ButtonGroup();
        this.buttonsByValue = new LinkedHashMap<String, JRadioButton>();

        List<?> items = displayedItems instanceof List<?> ? (List<?>) displayedItems : new ArrayList<Object>(displayedItems);
        int index = 0;
        for (final String value : possibleValues) {
            final Object display = index < items.size() ? items.get(index) : value;
            final JRadioButton radio = createRadioButton(display, value);
            this.buttonsByValue.put(value, radio);
            this.group.add(radio);
            index++;
        }
    }

    public RadioButtonProperty(final String name, final String[] values) {
        this(name, Arrays.asList(values), ComboProperty.translate(values));
    }

    public static <T extends Enum<T>> RadioButtonProperty of(String name, Class<T> enumClass) {
        return of(name, enumClass, null);
    }

    public static <T extends Enum<T>> RadioButtonProperty of(String name, Class<T> enumClass,
                                                             RenderedContentSupplier<T> supplier) {
        T[] constants = enumClass.getEnumConstants();
        final Vector<String> choices = new Vector<String>(constants.length);
        final List<Object> displayed = new ArrayList<Object>(constants.length);
        for (T e : constants) {
            final String choice = ((Enum<?>) e).name();
            choices.add(choice);
            if (supplier != null) {
                final String key = enumClass.getSimpleName() + "." + choice;
                final String text = TextUtils.getText("OptionPanel." + key, null);
                final Icon icon = ResourceController.getResourceController().getIcon("OptionPanel." + key + ".icon");
                final String shown = text != null ? text : TextUtils.getText("OptionPanel." + choice, choice);
                final JRadioButton preview = new JRadioButton(shown);
                if (icon != null)
                    preview.setIcon(icon);
                displayed.add(preview.getText());
            }
            else {
                final String key = enumClass.getSimpleName() + "." + choice;
                final String text = TextUtils.getText("OptionPanel." + key, null);
                final Object item = text != null ? text : choice;
                displayed.add(item);
            }
        }
        return new RadioButtonProperty(name, choices, displayed);
    }

    private void fillPossibleValues(final Collection<String> possibles) {
        possibleValues = new Vector<String>();
        possibleValues.addAll(possibles);
    }

    private JRadioButton createRadioButton(Object display, String value) {
        final JRadioButton radio;
        if (display instanceof String) {
            radio = new JRadioButton((String) display);
        }
        else if (display instanceof Icon) {
            radio = new JRadioButton();
            radio.setIcon((Icon) display);
        }
        else if (display instanceof JRadioButton) {
            radio = (JRadioButton) display;
        }
        else {
            radio = new JRadioButton(String.valueOf(display));
        }
        radio.setActionCommand(value);
        radio.addActionListener(this);
        return radio;
    }

    @Override
    public String getValue() {
        if (group.getSelection() == null)
            return null;
        return group.getSelection().getActionCommand();
    }

    @Override
    public JComponent getValueComponent() {
        return buttonsByValue.get(getValue());
    }

    @Override
    public void appendToForm(final DefaultFormBuilder builder) {
        int rowCount = 0;
        for (JRadioButton button : buttonsByValue.values()) {
            if (0 == rowCount++) {
                super.appendToForm(builder, button);
            }
            else {
                builder.append("");
                button.setToolTipText(labelComponent.getToolTipText());
                builder.append(alignValueComponentWithResetControl(button));
                builder.getLayout().setRowSpec(builder.getRowCount() - 1, FormSpecs.NARROW_LINE_GAP_ROWSPEC);
            }
        }
    }

    public Vector<String> getPossibleValues() {
        return possibleValues;
    }

    @Override
	public void setEnabled(final boolean enabled) {
        for (JRadioButton b : buttonsByValue.values())
            b.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    @Override
    public void setValue(final String value) {
        final JRadioButton b = buttonsByValue.get(value);
        if (b != null) {
            b.setSelected(true);
        }
        else {
            LogUtils.severe("Can't set the value:" + value + " into the radio buttons " + getName() + " containing values " + possibleValues);
            if (!buttonsByValue.isEmpty()) {
                buttonsByValue.values().iterator().next().setSelected(true);
            }
        }
    }

    @Override
	public void actionPerformed(final ActionEvent e) {
        firePropertyChangeEvent();
    }
}

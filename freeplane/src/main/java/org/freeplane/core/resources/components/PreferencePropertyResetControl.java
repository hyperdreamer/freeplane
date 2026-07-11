/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2026 Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.resources.components;

import java.awt.Component;
import java.awt.Container;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.styles.mindmapmode.styleeditorpanel.IconFont;

class PreferencePropertyResetControl {
    private static final int PADDING = (int) (UITools.FONT_SCALE_FACTOR * 2);
    private static final Border BORDER = BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING);

    private final PropertyBean property;
    private final String defaultValue;
    private final JButton button;
    private String loadedValue;
    private String resetValue;
    private boolean editorEnabled = true;
    private boolean initialized;
    private boolean settingDefault;
    private boolean userPropertyWasSet;
    private boolean userPropertyRemovalPending;

    PreferencePropertyResetControl(PropertyBean property, String defaultValue) {
        this.property = property;
        this.defaultValue = defaultValue;
        button = IconFont.createIconButton();
        button.setText(IconFont.REVERT_CHARACTER);
        button.setToolTipText(TextUtils.getText("reset_to_default"));
        button.setBorder(BORDER);
        button.addActionListener(event -> resetToDefault());
        property.addPropertyChangeListener(event -> editorValueChanged());
    }

    JComponent decorate(JComponent editor) {
        addTextChangeListeners(editor);
        Box decoratedEditor = Box.createHorizontalBox();
        decoratedEditor.add(button);
        decoratedEditor.add(editor);
        refreshButton();
        return decoratedEditor;
    }

    JComponent alignWithButton(JComponent editor) {
        Box alignedEditor = Box.createHorizontalBox();
        alignedEditor.add(Box.createRigidArea(button.getPreferredSize()));
        alignedEditor.add(editor);
        return alignedEditor;
    }

    void setEditorEnabled(boolean editorEnabled) {
        this.editorEnabled = editorEnabled;
        refreshButton();
    }

    void initialize(boolean userPropertyWasSet) {
        loadedValue = property.getValue();
        this.userPropertyWasSet = userPropertyWasSet;
        initialized = true;
        userPropertyRemovalPending = false;
        refreshButton();
    }

    void cancelPendingRemoval() {
        userPropertyRemovalPending = false;
        refreshButton();
    }

    boolean isValueChanged() {
        return initialized && !property.valuesEqual(property.getValue(), loadedValue);
    }

    boolean isUserPropertyRemovalPending() {
        return userPropertyRemovalPending && property.valuesEqual(property.getValue(), resetValue);
    }

    void resetToDefault() {
        settingDefault = true;
        try {
            property.setValue(defaultValue);
            resetValue = property.getValue();
        }
        finally {
            settingDefault = false;
        }
        userPropertyRemovalPending = true;
        refreshButton();
    }

    private void editorValueChanged() {
        if (!settingDefault) {
            userPropertyRemovalPending = false;
        }
        refreshButton();
    }

    private void refreshButton() {
        final boolean differsFromResetValue = defaultValue != null
                ? !property.valuesEqual(property.getValue(), defaultValue)
                : isValueChanged();
        button.setEnabled(initialized && editorEnabled && !userPropertyRemovalPending
                && (userPropertyWasSet || differsFromResetValue));
    }

    private void addTextChangeListeners(Component component) {
        if (component instanceof JTextComponent) {
            ((JTextComponent) component).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    editorValueChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    editorValueChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    editorValueChanged();
                }
            });
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                addTextChangeListeners(child);
            }
        }
    }
}

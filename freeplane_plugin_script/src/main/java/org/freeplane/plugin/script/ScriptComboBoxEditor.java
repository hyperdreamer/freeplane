/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2009 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
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
package org.freeplane.plugin.script;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;

import javax.swing.ComboBoxEditor;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.JRestrictedSizeScrollPane;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.features.text.mindmapmode.SourceTextEditorUIConfigurator;

public class ScriptComboBoxEditor implements ComboBoxEditor {
    private final ScriptEditorButton scriptEditorButton;
    private Dimension minimumSize;
    private Rectangle bounds;

    public ScriptComboBoxEditor() {
        scriptEditorButton = new ScriptEditorButton(this, this::editScript);
        minimumSize = new Dimension(600, 400);
        bounds = null;
    }

    public Dimension getMinimumSize() {
        return minimumSize;
    }

    public void setMinimumSize(Dimension minimumSize) {
        this.minimumSize = minimumSize;
    }

    protected void editScript(boolean selectAll) {
        JEditorPane textEditor = new JEditorPane();
        SourceTextEditorUIConfigurator.configureColors(textEditor);
        final JRestrictedSizeScrollPane scrollPane = new JRestrictedSizeScrollPane(textEditor);
        UITools.setScrollbarIncrement(scrollPane);
        scrollPane.setMinimumSize(minimumSize);
        textEditor.setContentType("text/groovy");

        final String fontName = ResourceController.getResourceController().getProperty(ScriptEditorPanel.GROOVY_EDITOR_FONT);
        final int fontSize = ResourceController.getResourceController().getIntProperty(ScriptEditorPanel.GROOVY_EDITOR_FONT_SIZE);
        final Font font = UITools.scaleUI(new Font(fontName, Font.PLAIN, fontSize));
        textEditor.setFont(font);

        textEditor.setText((String) scriptEditorButton.getItem());
        if (selectAll) {
            textEditor.selectAll();
        }
        String title = TextUtils.getText("plugins/ScriptEditor/window.title");
        final JOptionPane optionPane = new JOptionPane(scrollPane, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        final JDialog dialog = optionPane.createDialog(scriptEditorButton.getEditorComponent(), title);
        dialog.setResizable(true);
        if (bounds != null) {
            dialog.setBounds(bounds);
        } else {
            dialog.pack();
            UITools.setDialogLocationRelativeTo(dialog, scriptEditorButton.getEditorComponent());
            bounds = dialog.getBounds();
        }
        dialog.setVisible(true);
        final Integer result = (Integer) optionPane.getValue();
        if (result == null || result != JOptionPane.OK_OPTION) {
            return;
        }
        scriptEditorButton.setItemAndNotify(textEditor.getText());
    }

    @Override
    public Component getEditorComponent() {
        return scriptEditorButton.getEditorComponent();
    }

    @Override
    public void setItem(Object anObject) {
        scriptEditorButton.setItem(anObject);
    }

    @Override
    public Object getItem() {
        return scriptEditorButton.getItem();
    }

    @Override
    public void selectAll() {
        scriptEditorButton.selectAll();
    }

    @Override
    public void addActionListener(java.awt.event.ActionListener listener) {
        scriptEditorButton.addActionListener(listener);
    }

    @Override
    public void removeActionListener(java.awt.event.ActionListener listener) {
        scriptEditorButton.removeActionListener(listener);
    }

    public void setPreferredSize(Dimension preferredSize) {
        scriptEditorButton.setPreferredSize(preferredSize);
    }

    public Dimension getPreferredSize() {
        return scriptEditorButton.getPreferredSize();
    }
}

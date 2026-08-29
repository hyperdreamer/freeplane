package org.freeplane.plugin.script.filter;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.ComboBoxEditor;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.plugin.script.ScriptEditorButton;

public class FilterScriptConditionComboBoxEditor implements ComboBoxEditor {
    private final ScriptEditorButton scriptEditorButton;
    private Dimension minimumSize;
    private Rectangle bounds;
    private FilterScriptConditionEditor editor;

    public FilterScriptConditionComboBoxEditor() {
        scriptEditorButton = new ScriptEditorButton(this, this::editScript);
        minimumSize = new Dimension(600, 400);
    }

    public Dimension getMinimumSize() {
        return minimumSize;
    }

    public void setMinimumSize(Dimension minimumSize) {
        this.minimumSize = minimumSize;
    }

    private void editScript(boolean selectAll) {
        if (editor != null && editor.isDisplayable()) {
            editor.toFront();
            editor.requestFocus();
            return;
        }
        final FilterScriptConditionEditor openedEditor = new FilterScriptConditionEditor(
            scriptEditorButton.getEditorComponent(),
            (String) scriptEditorButton.getItem(),
            selectAll,
            scriptEditorButton::setItemAndNotify);
        editor = openedEditor;
        openedEditor.setMinimumSize(minimumSize);
        if (bounds != null) {
            openedEditor.setBounds(bounds);
        } else {
            openedEditor.pack();
            UITools.setDialogLocationRelativeTo(openedEditor, scriptEditorButton.getEditorComponent());
        }
        openedEditor.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                bounds = openedEditor.getBounds();
                if (editor == openedEditor) {
                    editor = null;
                }
            }
        });
        openedEditor.setVisible(true);
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
    public void addActionListener(ActionListener listener) {
        scriptEditorButton.addActionListener(listener);
    }

    @Override
    public void removeActionListener(ActionListener listener) {
        scriptEditorButton.removeActionListener(listener);
    }

    public void setPreferredSize(Dimension preferredSize) {
        scriptEditorButton.setPreferredSize(preferredSize);
    }

    public Dimension getPreferredSize() {
        return scriptEditorButton.getPreferredSize();
    }
}

package org.freeplane.plugin.script.filter;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ComboBoxEditor;
import javax.swing.JButton;
import javax.swing.SwingConstants;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.ui.components.UITools;

public class FilterScriptConditionComboBoxEditor implements ComboBoxEditor {
    private final JButton showEditorButton;
    private final List<ActionListener> actionListeners;
    private String script;
    private Dimension minimumSize;
    private Rectangle bounds;
    private FilterScriptConditionEditor editor;

    public FilterScriptConditionComboBoxEditor() {
        showEditorButton = new JButton();
        showEditorButton.setHorizontalAlignment(SwingConstants.LEFT);
        showEditorButton.addActionListener(event -> editScript(false));
        actionListeners = new LinkedList<ActionListener>();
        script = "";
        minimumSize = new Dimension(600, 400);
        setButtonText();
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
            showEditorButton,
            script,
            selectAll,
            editedScript -> {
                script = editedScript;
                setButtonText();
                fireActionEvent();
            });
        editor = openedEditor;
        openedEditor.setMinimumSize(minimumSize);
        if (bounds != null) {
            openedEditor.setBounds(bounds);
        } else {
            openedEditor.pack();
            UITools.setDialogLocationRelativeTo(openedEditor, showEditorButton);
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

    private void fireActionEvent() {
        ActionEvent actionEvent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : actionListeners) {
            listener.actionPerformed(actionEvent);
        }
    }

    private void setButtonText() {
        String currentScript = script == null ? "" : script;
        String text = currentScript.substring(0, Math.min(40, currentScript.length()))
            .trim()
            .replaceAll("\\s+", " ");
        if (!text.isEmpty()) {
            showEditorButton.setToolTipText(HtmlUtils.plainToHTML(currentScript));
            showEditorButton.setText(text);
        } else {
            showEditorButton.setToolTipText(null);
            showEditorButton.setText(TextUtils.getText("EditScript"));
        }
    }

    @Override
    public Component getEditorComponent() {
        return showEditorButton;
    }

    @Override
    public void setItem(Object anObject) {
        script = anObject == null ? "" : (String) anObject;
        setButtonText();
    }

    @Override
    public Object getItem() {
        return script;
    }

    @Override
    public void selectAll() {
        editScript(true);
    }

    @Override
    public void addActionListener(ActionListener listener) {
        actionListeners.add(listener);
    }

    @Override
    public void removeActionListener(ActionListener listener) {
        actionListeners.remove(listener);
    }

    public void setPreferredSize(Dimension preferredSize) {
        showEditorButton.setPreferredSize(preferredSize);
    }

    public Dimension getPreferredSize() {
        return showEditorButton.getPreferredSize();
    }
}

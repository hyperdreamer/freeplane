package org.freeplane.plugin.script;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.ComboBoxEditor;
import javax.swing.JButton;
import javax.swing.SwingConstants;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.TextUtils;

public final class ScriptEditorButton implements ComboBoxEditor {
    private final JButton button;
    private final Object eventSource;
    private final Consumer<Boolean> editAction;
    private final List<ActionListener> actionListeners;
    private String script;

    public ScriptEditorButton(Object eventSource, Consumer<Boolean> editAction) {
        this.eventSource = Objects.requireNonNull(eventSource, "eventSource");
        this.editAction = Objects.requireNonNull(editAction, "editAction");
        this.button = new JButton();
        this.button.setHorizontalAlignment(SwingConstants.LEFT);
        this.button.addActionListener(event -> this.editAction.accept(false));
        this.actionListeners = new LinkedList<ActionListener>();
        this.script = "";
        updateButtonText();
    }

    @Override
    public Component getEditorComponent() {
        return button;
    }

    @Override
    public void setItem(Object anObject) {
        script = anObject == null ? "" : (String) anObject;
        updateButtonText();
    }

    public void setItemAndNotify(Object anObject) {
        setItem(anObject);
        fireActionEvent();
    }

    @Override
    public Object getItem() {
        return script;
    }

    @Override
    public void selectAll() {
        editAction.accept(true);
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
        button.setPreferredSize(preferredSize);
    }

    public Dimension getPreferredSize() {
        return button.getPreferredSize();
    }

    private void updateButtonText() {
        String currentScript = script == null ? "" : script;
        String text = currentScript.substring(0, Math.min(40, currentScript.length()))
            .trim()
            .replaceAll("\\s+", " ");
        if (!text.isEmpty()) {
            button.setToolTipText(HtmlUtils.plainToHTML(currentScript));
            button.setText(text);
        } else {
            button.setToolTipText(null);
            button.setText(TextUtils.getText("EditScript"));
        }
    }

    private void fireActionEvent() {
        ActionEvent actionEvent = new ActionEvent(eventSource, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : actionListeners) {
            listener.actionPerformed(actionEvent);
        }
    }
}

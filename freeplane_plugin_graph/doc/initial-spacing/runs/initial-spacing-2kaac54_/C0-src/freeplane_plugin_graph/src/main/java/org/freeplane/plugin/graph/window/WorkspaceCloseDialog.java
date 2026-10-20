package org.freeplane.plugin.graph.window;

import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;

final class WorkspaceCloseDialog extends JPanel {
    private static final long serialVersionUID = 1L;

    private final WorkspaceCloseController closeController;
    private final Runnable completion;
    private final JButton retryButton = button("graph_workspace.action.retry", "retry");
    private final JButton discardButton = button("graph_workspace.action.discard", "discard");
    private final JButton cancelButton = button("graph_workspace.action.cancel", "cancel");
    private JDialog window;

    WorkspaceCloseDialog(final WorkspaceCloseController closeController, final Runnable completion) {
        super(new FlowLayout(FlowLayout.TRAILING, 4, 2));
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        this.completion = Objects.requireNonNull(completion, "completion");
        setName("graph-workspace-close-dialog");
        add(retryButton);
        add(discardButton);
        add(cancelButton);
        retryButton.addActionListener(event -> retry());
        discardButton.addActionListener(event -> discard());
        cancelButton.addActionListener(event -> cancel());
    }

    void attachWindow(final JDialog value) {
        window = Objects.requireNonNull(value, "window");
    }

    JButton retryButton() {
        return retryButton;
    }

    JButton discardButton() {
        return discardButton;
    }

    JButton cancelButton() {
        return cancelButton;
    }

    boolean retry() {
        if (!closeController.retrySaveAndClose()) {
            return false;
        }
        dismissWindow();
        completion.run();
        return true;
    }

    boolean discard() {
        if (!closeController.discardAndClose()) {
            return false;
        }
        dismissWindow();
        completion.run();
        return true;
    }

    void cancel() {
        dismissWindow();
        closeController.cancelClose();
    }

    private void dismissWindow() {
        final JDialog value = window;
        window = null;
        if (value != null) {
            value.setVisible(false);
            value.dispose();
        }
    }

    private static JButton button(final String textKey, final String name) {
        final JButton result = new JButton(TextUtils.getText(textKey));
        result.setName("graph-workspace-close-" + name);
        result.setMargin(new Insets(2, 7, 2, 7));
        result.setFocusable(false);
        return result;
    }
}

package org.freeplane.plugin.ai.prompt.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;

public class AiPromptProgressDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JLabel promptNameLabel;
    private final JButton cancelButton;
    private final Runnable cancelAction;
    private final Component ownerLocationAnchor;
    private final Component relativeLocationAnchor;

    public AiPromptProgressDialog(Component owner, Component relativeLocationAnchor,
                                  Icon promptIcon, Icon cancelIcon,
                                  String cancelTooltipText, Runnable cancelAction) {
        super(findOwnerWindow(owner));
        this.cancelAction = cancelAction;
        this.ownerLocationAnchor = owner;
        this.relativeLocationAnchor = relativeLocationAnchor;
        setTitle(TextUtils.getText("ai_prompt_running_title"));
        setModal(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancel();
            }
        });

        promptNameLabel = new JLabel();
        promptNameLabel.setIcon(promptIcon);
        promptNameLabel.setIconTextGap(8);
        promptNameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        cancelButton = new JButton(cancelIcon);
        cancelButton.setToolTipText(cancelTooltipText);
        cancelButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        cancelButton.addActionListener(event -> cancel());

        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        rowPanel.add(promptNameLabel);
        rowPanel.add(Box.createHorizontalStrut(12));
        rowPanel.add(Box.createHorizontalGlue());
        rowPanel.add(cancelButton);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.CENTER;
        contentPanel.add(rowPanel, constraints);

        add(contentPanel, BorderLayout.CENTER);
        setMinimumSize(new Dimension(260, 96));
        installEscapeCancellation();
        pack();
        setLocationRelativeTo(owner);
    }

    public void showPrompt(String promptName) {
        promptNameLabel.setText(displayPromptName(promptName));
        pack();
        setLocationRelativeTo(ownerLocationAnchor);
        if (relativeLocationAnchor != null) {
            UITools.setDialogLocationRelativeTo(this, relativeLocationAnchor);
        }
        setVisible(true);
    }

    public void closeDialog() {
        setVisible(false);
        dispose();
    }

    private void cancel() {
        if (cancelAction != null) {
            cancelAction.run();
        }
    }

    private void installEscapeCancellation() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPrompt");
        ActionMap actionMap = getRootPane().getActionMap();
        actionMap.put("cancelPrompt", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancel();
            }
        });
    }

    private String displayPromptName(String promptName) {
        String safePromptName = promptName == null ? "" : promptName.trim();
        return safePromptName.isEmpty()
            ? TextUtils.getText("ai_prompt_untitled")
            : safePromptName;
    }

    private static Window findOwnerWindow(Component owner) {
        if (owner instanceof Window) {
            return (Window) owner;
        }
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }
}

package org.freeplane.plugin.script.ai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptResponse;

public class AiOwnedScriptDialog extends JDialog implements AiOwnedScriptHostService.DialogHandle {
    private static final long serialVersionUID = 1L;

    private final AiOwnedScriptHostService.CodeStateProvider codeStateProvider;
    private final AiOwnedScriptHostService.DialogCallbacks callbacks;
    private final JTextArea codeTextArea;
    private final JButton runButton;
    private final JButton cancelButton;
    private String displayedCodeId;

    public AiOwnedScriptDialog(AiOwnedScriptHostService.CodeStateProvider codeStateProvider,
                               AiOwnedScriptHostService.DialogCallbacks callbacks) {
        super();
        this.codeStateProvider = codeStateProvider;
        this.callbacks = callbacks;
        this.codeTextArea = new JTextArea();
        this.runButton = new JButton(TextUtils.getText("plugins/ScriptEditor.run"));
        this.cancelButton = new JButton(TextUtils.getText("cancel"));
        setTitle(TextUtils.getText("ai_owned_script_dialog_title"));
        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        configureTextAreas();
        configureLayout();
        configureActions();
        configureAvailabilityListener();
        pack();
        setSize(new Dimension(900, 650));
    }

    @Override
    public void showCode(String codeId) {
        displayedCodeId = codeId;
        ReadCodeResponse state = codeStateProvider == null ? null : codeStateProvider.readCurrentState(codeId);
        if (state == null) {
            codeTextArea.setText("");
            refreshExecutionAuthority();
            return;
        }
        codeTextArea.setText(state.getCodeText() == null ? "" : state.getCodeText());
        codeTextArea.setCaretPosition(0);
        refreshExecutionAuthority();
    }

    @Override
    public void showAndFocus() {
        if (!isVisible()) {
            setVisible(true);
        }
        toFront();
        SwingUtilities.invokeLater(() -> codeTextArea.requestFocusInWindow());
    }

    @Override
    public String currentCodeText() {
        return codeTextArea.getText();
    }

    @Override
    public boolean showsCode(String codeId) {
        return displayedCodeId != null && displayedCodeId.equals(codeId);
    }

    @Override
    public void hideDialog() {
        setVisible(false);
    }

    private void configureTextAreas() {
        codeTextArea.setLineWrap(false);
    }

    private void configureLayout() {
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_code")));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(codeScrollPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private void configureActions() {
        runButton.addActionListener(event -> {
            RunScriptResponse response = callbacks == null ? null : callbacks.runFromDialog(codeTextArea.getText());
            if (displayedCodeId != null) {
                showCode(displayedCodeId);
            }
            if (response != null && response.getStatus() == CodeLifecycleStatus.SUCCEEDED) {
                hideDialog();
            }
        });
        cancelButton.addActionListener(event -> hideAfterCancel());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                hideAfterCancel();
            }
        });
    }

    private void hideAfterCancel() {
        if (callbacks != null) {
            callbacks.dialogCancelled();
        }
        hideDialog();
    }

    private void configureAvailabilityListener() {
        ResourceController resourceController = ResourceController.getResourceController();
        if (resourceController == null) {
            return;
        }
        resourceController.addPropertyChangeListener(new IFreeplanePropertyListener() {
            @Override
            public void propertyChanged(String propertyName, String newValue, String oldValue) {
                if (!AiOwnedScriptHostService.AI_TOOL_AVAILABILITY_PROPERTY.equals(propertyName)) {
                    return;
                }
                SwingUtilities.invokeLater(AiOwnedScriptDialog.this::refreshExecutionAuthority);
            }
        });
        refreshExecutionAuthority();
    }

    private void refreshExecutionAuthority() {
        ResourceController resourceController = ResourceController.getResourceController();
        boolean executionAvailable = resourceController == null;
        if (resourceController != null) {
            String availability = resourceController.getProperty(AiOwnedScriptHostService.AI_TOOL_AVAILABILITY_PROPERTY, null);
            executionAvailable = availability == null
                || availability.trim().isEmpty()
                || "SCRIPT_EXECUTION".equals(availability.trim());
        }
        codeTextArea.setEditable(executionAvailable);
        runButton.setEnabled(executionAvailable && displayedCodeId != null && !displayedCodeId.trim().isEmpty());
    }

}

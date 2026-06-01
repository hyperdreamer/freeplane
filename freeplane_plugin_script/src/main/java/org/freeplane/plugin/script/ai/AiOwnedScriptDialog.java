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
import javax.swing.JSplitPane;
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
    private final JTextArea resultArea;
    private final JButton runButton;
    private final JButton cancelButton;
    private String displayedCodeId;

    public AiOwnedScriptDialog(AiOwnedScriptHostService.CodeStateProvider codeStateProvider,
                               AiOwnedScriptHostService.DialogCallbacks callbacks) {
        super();
        this.codeStateProvider = codeStateProvider;
        this.callbacks = callbacks;
        this.codeTextArea = new JTextArea();
        this.resultArea = new JTextArea();
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
            resultArea.setText("");
            refreshExecutionAuthority();
            return;
        }
        if (state.getCodeText() != null) {
            codeTextArea.setText(state.getCodeText());
            codeTextArea.setCaretPosition(0);
        }
        resultArea.setText(formatState(state));
        resultArea.setCaretPosition(0);
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
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
    }

    private void configureLayout() {
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_code")));
        JScrollPane resultScrollPane = new JScrollPane(resultArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("plugins/ScriptEditor/window.Result")));
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeScrollPane, resultScrollPane);
        splitPane.setResizeWeight(0.75d);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
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

    private String formatState(ReadCodeResponse state) {
        StringBuilder builder = new StringBuilder();
        append(builder, "codeId", state.getCodeId());
        append(builder, "host", state.getHost());
        append(builder, "contentType", state.getContentType());
        append(builder, "status", state.getStatus());
        append(builder, "runInitiator", state.getRunInitiator());
        append(builder, "fingerprint", state.getFingerprint());
        append(builder, "replacementCodeId", state.getReplacementCodeId());
        appendList(builder, "compilerDiagnostics", state.getCompilerDiagnostics());
        append(builder, "errorMessage", state.getErrorMessage());
        append(builder, "lineNumber", state.getLineNumber());
        appendBlock(builder, "stdout", state.getStdout());
        append(builder, "structuredResult", state.getStructuredResult());
        return builder.toString();
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        builder.append(key).append('=').append(value).append('\n');
    }

    private void appendList(StringBuilder builder, String key, java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(key).append(':').append('\n');
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
    }

    private void appendBlock(StringBuilder builder, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        builder.append(key).append('=').append('\n');
        builder.append(value).append('\n');
    }
}

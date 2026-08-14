package org.freeplane.plugin.script.ai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnosticTextFormatter;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeResponse;

public class AiOwnedScriptDialog extends JDialog implements AiOwnedScriptHostService.DialogHandle {
    private static final long serialVersionUID = 1L;

    private final AiOwnedScriptHostService.CodeStateProvider codeStateProvider;
    private final AiOwnedScriptHostService.DialogCallbacks callbacks;
    private final JTextArea codeTextArea;
    private final JTextArea argumentsJsonTextArea;
    private final JTextArea resultTextArea;
    private final JButton runButton;
    private final JButton cancelButton;
    private boolean hasCode;

    public AiOwnedScriptDialog(AiOwnedScriptHostService.CodeStateProvider codeStateProvider,
                               AiOwnedScriptHostService.DialogCallbacks callbacks) {
        super();
        this.codeStateProvider = codeStateProvider;
        this.callbacks = callbacks;
        this.codeTextArea = new JTextArea();
        this.argumentsJsonTextArea = new JTextArea();
        this.resultTextArea = new JTextArea();
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
    public void showCode() {
        ReadCodeResponse state = codeStateProvider == null ? null : codeStateProvider.readCodeState();
        hasCode = state != null && state.getCodeState() != CodeState.NO_CODE;
        if (!hasCode) {
            codeTextArea.setText("");
            argumentsJsonTextArea.setText("");
            resultTextArea.setText("");
            refreshExecutionAuthority();
            return;
        }
        CodeStateContent content = state.getContent();
        codeTextArea.setText(content == null || content.getSourceText() == null ? "" : content.getSourceText());
        argumentsJsonTextArea.setText(content == null || content.getArgumentsJsonText() == null ? "" : content.getArgumentsJsonText());
        codeTextArea.setCaretPosition(0);
        argumentsJsonTextArea.setCaretPosition(0);
        resultTextArea.setText(resultText(state));
        resultTextArea.setCaretPosition(0);
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
    public CodeStateContent currentContent() {
        return new CodeStateContent(codeTextArea.getText(), argumentsJsonTextArea.getText());
    }

    @Override
    public boolean hasCode() {
        return hasCode;
    }

    @Override
    public void hideDialog() {
        setVisible(false);
    }

    private void configureTextAreas() {
        codeTextArea.setLineWrap(false);
        argumentsJsonTextArea.setLineWrap(false);
        resultTextArea.setEditable(false);
        resultTextArea.setLineWrap(false);
        ResourceController resourceController = ResourceController.getResourceController();
        if (resourceController != null) {
            String fontName = resourceController.getProperty("groovy_editor_font", Font.MONOSPACED);
            int fontSize = resourceController.getIntProperty("groovy_editor_font_size", 12);
            Font font = new Font(fontName, Font.PLAIN, fontSize);
            codeTextArea.setFont(font);
            argumentsJsonTextArea.setFont(font);
            resultTextArea.setFont(font);
        }
    }

    private void configureLayout() {
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_code")));
        JScrollPane inputScrollPane = new JScrollPane(argumentsJsonTextArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_input_json")));
        JSplitPane editorSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeScrollPane, inputScrollPane);
        editorSplitPane.setResizeWeight(0.75d);
        editorSplitPane.setContinuousLayout(true);
        JScrollPane resultScrollPane = new JScrollPane(resultTextArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder(TextUtils.getText("plugins/ScriptEditor/window.Result")));
        JSplitPane contentSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorSplitPane, resultScrollPane);
        contentSplitPane.setResizeWeight(0.8d);
        contentSplitPane.setContinuousLayout(true);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(contentSplitPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    private void configureActions() {
        runButton.addActionListener(event -> {
            RunCodeResponse response = callbacks == null ? null : callbacks.runFromDialog(currentContent());
            if (hasCode) {
                showCode();
            }
            if (response != null && response.getCodeState() == CodeState.RUN_SUCCEEDED) {
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
        argumentsJsonTextArea.setEditable(executionAvailable);
        runButton.setEnabled(executionAvailable && hasCode);
    }

    static String resultText(ReadCodeResponse state) {
        if (state == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        appendBlock(result, state.getStdout());
        if (state.getStructuredResult() != null) {
            appendBlock(result, String.valueOf(state.getStructuredResult()));
        }
        String formattedDiagnostics = CodeStateDiagnosticTextFormatter.format(state.getDiagnostics());
        appendBlock(result, formattedDiagnostics);
        appendBlock(result, distinctErrorMessage(state, formattedDiagnostics));
        return result.toString();
    }

    private static void appendBlock(StringBuilder result, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n\n");
        }
        result.append(value);
    }

    private static String distinctErrorMessage(ReadCodeResponse state, String formattedDiagnostics) {
        String errorMessage = trimToNull(state.getErrorMessage());
        if (errorMessage == null) {
            return null;
        }
        if (errorMessage.equals(formattedDiagnostics)) {
            return null;
        }
        if (state.getDiagnostics() != null) {
            for (CodeStateDiagnostic diagnostic : state.getDiagnostics()) {
                if (diagnostic != null && errorMessage.equals(trimToNull(diagnostic.getMessage()))) {
                    return null;
                }
            }
        }
        return errorMessage;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

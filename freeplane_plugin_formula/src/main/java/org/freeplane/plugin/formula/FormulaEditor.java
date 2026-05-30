package org.freeplane.plugin.formula;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Formatter;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatCodeEditor;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.explorer.MapExplorerController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.mindmapmode.EditNodeDialog;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.ScriptingEngine;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.freeplane.view.swing.ui.mindmapmode.CenterPaneNodeSelectionOverlay;
import org.freeplane.view.swing.ui.mindmapmode.INodeSelector;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import de.sciss.syntaxpane.SyntaxDocument;
import de.sciss.syntaxpane.Token;
import de.sciss.syntaxpane.TokenType;

class FormulaEditor extends EditNodeDialog implements INodeSelector, AiChatCodeEditor {

    private static final String PASSED_WIDTH_PROPERTY = "formulaDialog.passed.width";
    private static final String PASSED_HEIGHT_PROPERTY = "formulaDialog.passed.height";
    private static final String REPAIR_PROMPT =
        "Repair the attached Freeplane formula. Keep the result as a valid formula that starts with '='. "
            + "Use the current formula text and the submit diagnostics.";

    static enum EvaluationStatus {

        PASSED(PASSED_WIDTH_PROPERTY, PASSED_HEIGHT_PROPERTY);

        public final String heightPropertyName;
        public final String widthPropertyName;

        private EvaluationStatus(String widthPropertyName, String heightPropertyName) {
            this.heightPropertyName = heightPropertyName;
            this.widthPropertyName = widthPropertyName;
        }

    }

    static final String GROOVY_EDITOR_FONT = "groovy_editor_font";
    static final String GROOVY_EDITOR_FONT_SIZE = "groovy_editor_font_size";

    private final JEditorPane textEditor;
    private final MapExplorerController mapExplorer;
    private final FormulaSubmitValidationSupport formulaSubmitValidationSupport;
    private EvaluationStatus evaluationStatus;
    private AiChatAttachment aiChatAttachment;
    private CenterPaneNodeSelectionOverlay centerPaneNodeSelectionOverlay;

    FormulaEditor(MapExplorerController mapExplorer, NodeModel nodeModel, AWTEvent firstEvent, IEditControl editControl,
                  boolean enableSplit, JEditorPane textEditor) {
        super(nodeModel, firstEvent, true, editControl, enableSplit, textEditor);
        this.mapExplorer = mapExplorer;
        this.textEditor = textEditor;
        this.formulaSubmitValidationSupport = new FormulaSubmitValidationSupport();
        this.evaluationStatus = EvaluationStatus.PASSED;
    }

    @Override
    public void show(Window window) {
        if (window instanceof RootPaneContainer) {
            centerPaneNodeSelectionOverlay = new CenterPaneNodeSelectionOverlay(
                ((RootPaneContainer) window).getRootPane(),
                this);
        }
        super.show(window);
        if (centerPaneNodeSelectionOverlay != null) {
            centerPaneNodeSelectionOverlay.activate();
        }
    }

    @Override
    protected void configureDialog(JDialog dialog) {
        dialog.setModal(false);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cleanupAttachmentAndOverlay();
            }
        });
    }

    @Override
    protected void addAdditionalButtons(JPanel buttonPane) {
        JButton aiButton = TranslatedElementFactory.createButton(new AttachToAiAction(), "formula_editor_ai");
        buttonPane.add(aiButton);
    }

    @Override
    protected boolean submitEditedText(String editedText) {
        if (!startsWithFormulaPrefix(editedText)) {
            if (aiChatAttachment != null) {
                aiChatAttachment.clearIssue();
            }
            getEditControl().ok(editedText);
            return true;
        }
        AiChatCodeOperationResult validationResult = formulaSubmitValidationSupport.validateSubmittedFormula(
            getNode(),
            editedText);
        if (validationResult.isSuccessful()) {
            if (aiChatAttachment != null) {
                aiChatAttachment.clearIssue();
            }
            getEditControl().ok(editedText);
            return true;
        }
        AiChatAttachment issueAttachment = aiChatAttachment;
        if (issueAttachment != null) {
            issueAttachment.recordIssue(validationResult);
        }
        int answer = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(textEditor),
            buildValidationFailureMessage(validationResult),
            "Formula validation failed",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            if (issueAttachment == null) {
                issueAttachment = attachToAi();
                if (issueAttachment != null) {
                    issueAttachment.recordIssue(validationResult);
                }
            }
            if (issueAttachment != null) {
                issueAttachment.requestRepair(new AiChatRepairRequest(REPAIR_PROMPT, editedText, validationResult));
            }
        }
        return false;
    }

    @Override
    public void nodeSelected(final NodeModel node) {
        final String replacement;
        if (isCaretInsideStringToken())
            replacement = mapExplorer.getNodeReferenceSuggestion(node);
        else
            replacement = createReference(node);
        replaceSelectedText(replacement);
    }

    private void replaceSelectedText(final String replacement) {
        textEditor.replaceSelection(replacement);
        textEditor.requestFocus();
        SwingUtilities.getWindowAncestor(textEditor).toFront();
    }

    private String createReference(final NodeModel node) {
        if (node == getNode())
            return "node";
        else if (!mapExplorer.isGlobal(node))
            return node.getID();
        final String alias = mapExplorer.getAlias(node);
        if (alias.isEmpty())
            return node.getID();
        else
            return "at(':~" + alias + "')";
    }

    @Override
    public void tableRowSelected(NodeModel node, String rowName) {
        if (isCaretInsideStringToken())
            return;
        final String replacement = createReference(node) + "['" + rowName + "']";
        replaceSelectedText(replacement);
    }

    private boolean isCaretInsideStringToken() {
        final int caretPosition = textEditor.getCaretPosition();
        SyntaxDocument document = (SyntaxDocument) textEditor.getDocument();
        final Token token = document.getTokenAt(caretPosition);
        return TokenType.isString(token);
    }

    @Override
    protected void saveDialogSize(final JDialog dialog) {
        ResourceController resourceController = ResourceController.getResourceController();
        resourceController.setProperty(evaluationStatus.widthPropertyName, dialog.getWidth());
        resourceController.setProperty(evaluationStatus.heightPropertyName, dialog.getHeight());
    }

    @Override
    protected void restoreDialogSize(final JDialog dialog) {
        Dimension preferredSize = dialog.getPreferredSize();
        ResourceController resourceController = ResourceController.getResourceController();
        preferredSize.width = Math.max(preferredSize.width,
            resourceController.getIntProperty(evaluationStatus.widthPropertyName, 0));
        preferredSize.height = Math.max(preferredSize.height,
            resourceController.getIntProperty(evaluationStatus.heightPropertyName, 0));
        dialog.setPreferredSize(preferredSize);
    }

    @Override
    public String getText() {
        return textEditor.getText();
    }

    @Override
    public void replaceText(String text) {
        textEditor.setText(text == null ? "" : text);
    }

    @Override
    public AiChatCodeOperationResult compileForAi() {
        String formulaText = textEditor.getText();
        if (!startsWithFormulaPrefix(formulaText)) {
            return new AiChatCodeOperationResult(
                "COMPILE",
                "AI",
                false,
                Collections.singletonList("The current content is not a formula."),
                null,
                null,
                "not_formula",
                "The current content is not a formula.",
                null,
                fingerprint(formulaText));
        }
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            FormulaUtils.scriptOf(formulaText),
            ScriptingPermissions.getFormulaPermissions());
        return new AiChatCodeOperationResult(
            "COMPILE",
            "AI",
            compileResult.isSuccessful(),
            compileResult.getCompilerDiagnostics(),
            null,
            null,
            compileResult.isSuccessful() ? null : "compile",
            compileResult.getErrorMessage(),
            compileResult.getLineNumber(),
            fingerprint(formulaText));
    }

    private boolean startsWithFormulaPrefix(String text) {
        return text != null && text.startsWith("=");
    }

    private String buildValidationFailureMessage(AiChatCodeOperationResult validationResult) {
        StringBuilder builder = new StringBuilder();
        builder.append("Formula validation failed.");
        if (validationResult.getErrorMessage() != null && !validationResult.getErrorMessage().trim().isEmpty()) {
            builder.append("\n\n").append(validationResult.getErrorMessage().trim());
        }
        if (!validationResult.getCompilerDiagnostics().isEmpty()) {
            builder.append("\n\nDiagnostics:");
            for (String diagnostic : validationResult.getCompilerDiagnostics()) {
                builder.append("\n- ").append(diagnostic);
            }
        }
        builder.append("\n\nAsk AI to try to fix the formula?");
        return builder.toString();
    }

    private AiChatAttachment attachToAi() {
        AiChatAttachmentService attachmentService = lookupAiChatAttachmentService();
        if (attachmentService == null) {
            LogUtils.severe("AI attachment service is unavailable.");
            return null;
        }
        aiChatAttachment = attachmentService.attachEditor(this, textEditor.getContentType());
        return aiChatAttachment;
    }

    private AiChatAttachmentService lookupAiChatAttachmentService() {
        BundleContext bundleContext = Activator.getBundleContext();
        if (bundleContext == null) {
            return null;
        }
        ServiceReference<AiChatAttachmentService> serviceReference =
            bundleContext.getServiceReference(AiChatAttachmentService.class);
        if (serviceReference == null) {
            return null;
        }
        return bundleContext.getService(serviceReference);
    }

    private void cleanupAttachmentAndOverlay() {
        if (aiChatAttachment != null) {
            aiChatAttachment.detach();
            aiChatAttachment = null;
        }
        if (centerPaneNodeSelectionOverlay != null) {
            centerPaneNodeSelectionOverlay.deactivate();
            centerPaneNodeSelectionOverlay = null;
        }
    }

    private String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            Formatter formatter = new Formatter();
            try {
                for (byte value : hash) {
                    formatter.format("%02x", value);
                }
                return formatter.toString();
            }
            finally {
                formatter.close();
            }
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private final class AttachToAiAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            attachToAi();
        }
    }
}

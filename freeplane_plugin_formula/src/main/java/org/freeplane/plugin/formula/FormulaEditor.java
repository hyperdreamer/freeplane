package org.freeplane.plugin.formula;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RootPaneContainer;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.freeplane.api.LengthUnit;
import org.freeplane.api.Quantity;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnosticTextFormatter;
import org.freeplane.features.ai.code.CodeStateDiagnostics;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.explorer.MapExplorerController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.mindmapmode.EditNodeDialog;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.FormulaValidationSupport;
import org.freeplane.plugin.script.GroovyCompilerDiagnosticsMapper;
import org.freeplane.plugin.script.ScriptingEngine;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.freeplane.view.swing.ui.mindmapmode.CenterPaneNodeSelectionOverlay;
import org.freeplane.view.swing.ui.mindmapmode.INodeSelector;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import de.sciss.syntaxpane.SyntaxDocument;
import de.sciss.syntaxpane.Token;
import de.sciss.syntaxpane.TokenType;

class FormulaEditor extends EditNodeDialog implements INodeSelector, AiCodeEditor {

    private static final String PASSED_WIDTH_PROPERTY = "formulaDialog.passed.width";
    private static final String PASSED_HEIGHT_PROPERTY = "formulaDialog.passed.height";
    private static final String AI_TAB_ICON_RESOURCE = "/images/panelTabs/aiTab.svg?useAccentColor=true";
    private static final Quantity<LengthUnit> EXECUTION_FAILURE_DIALOG_WIDTH = new Quantity<LengthUnit>(170, LengthUnit.mm);
    private static final Quantity<LengthUnit> EXECUTION_FAILURE_DIALOG_HEIGHT = new Quantity<LengthUnit>(85, LengthUnit.mm);
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
    private final FormulaValidationSupport formulaValidationSupport;
    private EvaluationStatus evaluationStatus;
    private AiChatAttachment aiChatAttachment;
    private JToggleButton aiAttachButton;
    private CenterPaneNodeSelectionOverlay centerPaneNodeSelectionOverlay;

    FormulaEditor(MapExplorerController mapExplorer, NodeModel nodeModel, AWTEvent firstEvent, IEditControl editControl,
                  boolean enableSplit, JEditorPane textEditor) {
        super(nodeModel, firstEvent, true, editControl, enableSplit, textEditor);
        this.mapExplorer = mapExplorer;
        this.textEditor = textEditor;
        this.formulaValidationSupport = new FormulaValidationSupport();
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
        aiAttachButton = TranslatedElementFactory.createToggleButton("formula_editor_ai");
        aiAttachButton.setIcon(ResourceController.getResourceController().getImageIcon(AI_TAB_ICON_RESOURCE));
        aiAttachButton.addActionListener(new AttachToAiAction());
        buttonPane.add(aiAttachButton);
        updateAiAttachButtonState();
    }

    @Override
    protected boolean submitEditedText(String editedText) {
        if (!startsWithFormulaPrefix(editedText)) {
            if (aiChatAttachment != null) {
                aiChatAttachment.clearCodeState();
            }
            getEditControl().ok(editedText);
            return true;
        }
        CompileCodeResponse compileResponse = compileFormulaCodeStateContent(new CodeStateContent(editedText, null));
        if (compileResponse.getCodeState() == CodeState.INVALID_SCRIPT) {
            ReadCodeResponse validationFailureState = validationFailureState(editedText, compileResponse);
            AiChatAttachment issueAttachment = aiChatAttachment;
            if (issueAttachment != null) {
                issueAttachment.recordCodeState(validationFailureState);
            }
            if (!canAttachToAi()) {
                showValidationFailureMessage(compileResponse);
                return false;
            }
            int answer = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(textEditor),
                buildValidationFailureDialogMessage(compileResponse),
                FormulaPluginUtils.getFormulaText("execution_failed.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);
            requestFormulaRepairIfAvailable(
                issueAttachment,
                validationFailureState,
                answer,
                true,
                this::attachToAi);
            return false;
        }
        AiChatCodeOperationResult validationResult = formulaValidationSupport.validateFormula(
            getNode(),
            editedText);
        if (validationResult.isSuccessful()) {
            if (aiChatAttachment != null) {
                aiChatAttachment.clearCodeState();
            }
            getEditControl().ok(editedText);
            return true;
        }
        ReadCodeResponse validationFailureState = validationFailureState(editedText, validationResult);
        AiChatAttachment issueAttachment = aiChatAttachment;
        if (issueAttachment != null) {
            issueAttachment.recordCodeState(validationFailureState);
        }
        if (!canAttachToAi()) {
            showValidationFailureMessage(validationResult);
            return false;
        }
        int answer = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(textEditor),
            buildValidationFailureDialogMessage(validationResult),
            FormulaPluginUtils.getFormulaText("execution_failed.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);
        requestFormulaRepairIfAvailable(
            issueAttachment,
            validationFailureState,
            answer,
            true,
            this::attachToAi);
        return false;
    }

    static void requestFormulaRepairIfConfirmed(AiChatAttachment attachment,
                                                ReadCodeResponse validationFailureState,
                                                int confirmationAnswer) {
        requestFormulaRepairIfAvailable(attachment, validationFailureState, confirmationAnswer, true, null);
    }

    static void requestFormulaRepairIfAvailable(AiChatAttachment attachment,
                                                ReadCodeResponse validationFailureState,
                                                int confirmationAnswer,
                                                boolean canRequestAiRepair,
                                                AttachmentSupplier attachmentSupplier) {
        if (!canRequestAiRepair
            || validationFailureState == null
            || confirmationAnswer != JOptionPane.YES_OPTION) {
            return;
        }
        AiChatAttachment repairAttachment = attachment;
        if (repairAttachment == null && attachmentSupplier != null) {
            repairAttachment = attachmentSupplier.attach();
        }
        if (repairAttachment == null) {
            return;
        }
        repairAttachment.recordCodeState(validationFailureState);
        repairAttachment.requestRepair(new AiChatRepairRequest(REPAIR_PROMPT, validationFailureState));
    }

    interface AttachmentSupplier {
        AiChatAttachment attach();
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
    public CodeStateContent getCodeStateContent() {
        return new CodeStateContent(textEditor.getText(), null);
    }

    @Override
    public void replaceCodeStateContent(CodeStateContent content) {
        String argumentsJsonText = content == null ? null : content.getArgumentsJsonText();
        if (argumentsJsonText != null && !argumentsJsonText.trim().isEmpty()) {
            throw new IllegalArgumentException("Formula editors do not accept argumentsJsonText.");
        }
        textEditor.setText(content == null || content.getSourceText() == null ? "" : content.getSourceText());
    }

    static CompileCodeResponse compileFormulaCodeStateContent(CodeStateContent content) {
        String formulaText = content == null ? null : content.getSourceText();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        if (!startsWithFormulaPrefix(formulaText)) {
            return new CompileCodeResponse(
                ScriptHost.ATTACHED_EDITOR,
                FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE,
                CodeState.INVALID_SCRIPT,
                stateToken,
                CodeStateDiagnostics.singleton(
                    org.freeplane.features.ai.code.CodeStateField.SOURCE_TEXT,
                    "The current content is not a formula.",
                    null,
                    null),
                "The current content is not a formula.");
        }
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            FormulaUtils.scriptOf(formulaText),
            ScriptingPermissions.getFormulaPermissions());
        return new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE,
            compileResult.isSuccessful() ? CodeState.RUNNABLE : CodeState.INVALID_SCRIPT,
            stateToken,
            GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(compileResult.getCompilerDiagnostics()),
            compileResult.getErrorMessage());
    }

    @Override
    public CompileCodeResponse compileCode(CompileCodeRequest request) {
        return compileFormulaCodeStateContent(getCodeStateContent());
    }

    @Override
    public RunCodeResponse runCode(RunCodeRequest request) {
        throw new IllegalStateException("Only script content is runnable.");
    }

    private static boolean startsWithFormulaPrefix(String text) {
        return text != null && text.startsWith("=");
    }

    private ReadCodeResponse validationFailureState(String formulaText, CompileCodeResponse compileResponse) {
        CodeStateContent content = new CodeStateContent(formulaText, null);
        CodeStateToken stateToken = compileResponse.getStateToken() == null
            ? CodeStateToken.fromContent(content)
            : compileResponse.getStateToken();
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            stateToken,
            content,
            compileResponse.getDiagnostics(),
            compileResponse.getErrorMessage(),
            null,
            null);
    }

    private ReadCodeResponse validationFailureState(String formulaText, AiChatCodeOperationResult validationResult) {
        CodeStateContent content = new CodeStateContent(formulaText, null);
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        if (validationResult.getSourceFingerprint() != null) {
            stateToken.setCodeFingerprint(validationResult.getSourceFingerprint());
        }
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            stateToken,
            content,
            CodeStateDiagnostics.sourceDiagnostics(validationResult.getCompilerDiagnostics(), validationResult.getLineNumber()),
            validationResult.getErrorMessage(),
            validationResult.getStandardOutput(),
            validationResult.getResult());
    }

    private Object buildValidationFailureDialogMessage(CompileCodeResponse compileResponse) {
        JTextArea messageArea = new JTextArea(buildValidationFailureMessage(compileResponse));
        messageArea.setEditable(false);
        messageArea.setLineWrap(false);
        messageArea.setWrapStyleWord(false);
        messageArea.setFont(textEditor.getFont());
        messageArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
            messageArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(
            EXECUTION_FAILURE_DIALOG_WIDTH.in(LengthUnit.px).toBaseUnitsRounded(),
            EXECUTION_FAILURE_DIALOG_HEIGHT.in(LengthUnit.px).toBaseUnitsRounded()));
        return new Object[] { scrollPane, FormulaPluginUtils.getFormulaText("execution_failed.ask_for_ai_repair") };
    }

    private Object buildValidationFailureDialogMessage(AiChatCodeOperationResult validationResult) {
        JTextArea messageArea = new JTextArea(buildValidationFailureMessage(validationResult));
        messageArea.setEditable(false);
        messageArea.setLineWrap(false);
        messageArea.setWrapStyleWord(false);
        messageArea.setFont(textEditor.getFont());
        messageArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
            messageArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(
            EXECUTION_FAILURE_DIALOG_WIDTH.in(LengthUnit.px).toBaseUnitsRounded(),
            EXECUTION_FAILURE_DIALOG_HEIGHT.in(LengthUnit.px).toBaseUnitsRounded()));
        return new Object[] { scrollPane, FormulaPluginUtils.getFormulaText("execution_failed.ask_for_ai_repair") };
    }

    private String buildValidationFailureMessage(CompileCodeResponse compileResponse) {
        return buildValidationFailureMessage(compileResponse.getDiagnostics(), compileResponse.getErrorMessage());
    }

    private String buildValidationFailureMessage(AiChatCodeOperationResult validationResult) {
        return buildValidationFailureMessage(
            CodeStateDiagnostics.sourceDiagnostics(validationResult.getCompilerDiagnostics(), validationResult.getLineNumber()),
            validationResult.getErrorMessage());
    }

    static String buildValidationFailureMessage(List<CodeStateDiagnostic> diagnostics, String errorMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append(FormulaPluginUtils.getFormulaText("execution_failed.message"));
        String formattedDiagnostics = CodeStateDiagnosticTextFormatter.format(diagnostics);
        if (formattedDiagnostics != null) {
            builder.append("\n\n").append(FormulaPluginUtils.getFormulaText("execution_failed.diagnostics"));
            builder.append("\n").append(formattedDiagnostics);
            return builder.toString();
        }
        String trimmedErrorMessage = trimToNull(errorMessage);
        if (trimmedErrorMessage != null) {
            builder.append("\n\n").append(trimmedErrorMessage);
        }
        return builder.toString();
    }

    private void showValidationFailureMessage(CompileCodeResponse compileResponse) {
        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(textEditor),
            buildValidationFailureMessage(compileResponse),
            FormulaPluginUtils.getFormulaText("execution_failed.title"),
            JOptionPane.ERROR_MESSAGE);
    }

    private void showValidationFailureMessage(AiChatCodeOperationResult validationResult) {
        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(textEditor),
            buildValidationFailureMessage(validationResult),
            FormulaPluginUtils.getFormulaText("execution_failed.title"),
            JOptionPane.ERROR_MESSAGE);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AiChatAttachment attachToAi() {
        AiChatAttachmentService attachmentService = lookupAiChatAttachmentService();
        if (attachmentService == null || !attachmentService.isAiConfigured()) {
            updateAiAttachButtonState();
            return null;
        }
        AiChatAttachment attachment = attachmentService.attachEditor(this, FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE);
        setAiChatAttachment(attachment);
        return attachment;
    }

    private boolean canAttachToAi() {
        AiChatAttachmentService attachmentService = lookupAiChatAttachmentService();
        return attachmentService != null && attachmentService.isAiConfigured();
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
        }
        if (centerPaneNodeSelectionOverlay != null) {
            centerPaneNodeSelectionOverlay.deactivate();
            centerPaneNodeSelectionOverlay = null;
        }
    }

    private void setAiChatAttachment(AiChatAttachment attachment) {
        aiChatAttachment = attachment;
        if (aiChatAttachment != null) {
            aiChatAttachment.setDetachHandler(new Runnable() {
                @Override
                public void run() {
                    aiChatAttachment = null;
                    updateAiAttachButtonState();
                }
            });
        }
        updateAiAttachButtonState();
    }

    private void updateAiAttachButtonState() {
        if (aiAttachButton != null) {
            aiAttachButton.setSelected(aiChatAttachment != null);
            aiAttachButton.setEnabled(shouldEnableAiAttachButton(aiChatAttachment, canAttachToAi()));
        }
    }

    static boolean shouldEnableAiAttachButton(AiChatAttachment attachment, boolean canAttachToAi) {
        return attachment != null || canAttachToAi;
    }

    private String fingerprint(String text) {
        return CodeStateToken.fingerprint(text);
    }

    private final class AttachToAiAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            if (aiChatAttachment != null) {
                aiChatAttachment.detach();
                updateAiAttachButtonState();
                return;
            }
            attachToAi();
        }
    }
}

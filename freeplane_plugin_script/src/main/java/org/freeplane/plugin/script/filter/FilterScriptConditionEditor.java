package org.freeplane.plugin.script.filter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.LabelAndMnemonicSetter;
import org.freeplane.core.ui.components.JRestrictedSizeScrollPane;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.TextUtils;
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
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.mindmapmode.SourceTextEditorUIConfigurator;
import org.freeplane.plugin.script.Activator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class FilterScriptConditionEditor extends JDialog implements AiCodeEditor {
    private static final long serialVersionUID = 1L;
    private static final String AI_TAB_ICON_RESOURCE = "/images/panelTabs/aiTab.svg?useAccentColor=true";
    private static final String EDITOR_CONTENT_TYPE = "text/groovy";
    private static final String GROOVY_EDITOR_FONT = "groovy_editor_font";
    private static final String GROOVY_EDITOR_FONT_SIZE = "groovy_editor_font_size";
    private static final String REPAIR_PROMPT =
        "Repair the attached Freeplane filter condition. Keep it as a valid, argument-free Groovy condition "
            + "that returns Boolean or Number. Use the current condition text and the submit diagnostics.";

    private final JEditorPane textEditor;
    private final Consumer<String> submitHandler;
    private final FilterScriptConditionValidationSupport validationSupport;
    private AiChatAttachment aiChatAttachment;
    private JToggleButton aiAttachButton;

    FilterScriptConditionEditor(Component owner,
                                String script,
                                boolean selectAll,
                                Consumer<String> submitHandler) {
        this(owner, script, selectAll, submitHandler, new FilterScriptConditionValidationSupport());
    }

    FilterScriptConditionEditor(Component owner,
                                String script,
                                boolean selectAll,
                                Consumer<String> submitHandler,
                                FilterScriptConditionValidationSupport validationSupport) {
        super(dialogOwner(owner), TextUtils.getText("plugins/script_filter_editor.window.title"), Dialog.ModalityType.MODELESS);
        this.submitHandler = submitHandler;
        this.validationSupport = validationSupport;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(true);
        UITools.addEscapeActionToDialog(this, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                cleanupAttachment();
            }
        });

        textEditor = new JEditorPane();
        SourceTextEditorUIConfigurator.configureColors(textEditor);
        textEditor.setContentType(EDITOR_CONTENT_TYPE);
        textEditor.setText(script == null ? "" : script);
        final String fontName = ResourceController.getResourceController().getProperty(GROOVY_EDITOR_FONT);
        final int fontSize = ResourceController.getResourceController().getIntProperty(GROOVY_EDITOR_FONT_SIZE);
        textEditor.setFont(UITools.scaleUI(new Font(fontName, Font.PLAIN, fontSize)));
        if (selectAll) {
            textEditor.selectAll();
        } else {
            textEditor.setCaretPosition(textEditor.getDocument().getLength());
        }

        JRestrictedSizeScrollPane scrollPane = new JRestrictedSizeScrollPane(textEditor);
        scrollPane.setMinimumSize(new Dimension(600, 400));
        UITools.setScrollbarIncrement(scrollPane);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPane = new JPanel();
        JButton okButton = new JButton();
        LabelAndMnemonicSetter.setLabelAndMnemonic(okButton, TextUtils.getRawText("ok"));
        okButton.addActionListener(new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                submit();
            }
        });
        JButton cancelButton = new JButton();
        LabelAndMnemonicSetter.setLabelAndMnemonic(cancelButton, TextUtils.getRawText("cancel"));
        cancelButton.addActionListener(new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        buttonPane.add(okButton);
        buttonPane.add(cancelButton);
        aiAttachButton = TranslatedElementFactory.createToggleButton("plugins/ScriptEditor.ai");
        aiAttachButton.setIcon(ResourceController.getResourceController().getImageIcon(AI_TAB_ICON_RESOURCE));
        aiAttachButton.addActionListener(new AttachToAiAction());
        buttonPane.add(aiAttachButton);
        getContentPane().add(buttonPane, BorderLayout.SOUTH);
        updateAiAttachButtonState();
        setMinimumSize(new Dimension(600, 400));
        setPreferredSize(new Dimension(760, 520));
        SwingUtilities.invokeLater(() -> textEditor.requestFocusInWindow());
    }

    private static Window dialogOwner(Component owner) {
        Window ownerWindow = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        return ownerWindow == null ? UITools.getCurrentFrame() : ownerWindow;
    }

    @Override
    public CodeStateContent getCodeStateContent() {
        return new CodeStateContent(textEditor.getText(), null);
    }

    @Override
    public void replaceCodeStateContent(CodeStateContent content) {
        String argumentsJsonText = content == null ? null : content.getArgumentsJsonText();
        if (argumentsJsonText != null && !argumentsJsonText.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter-condition editors do not accept argumentsJsonText.");
        }
        textEditor.setText(content == null || content.getSourceText() == null ? "" : content.getSourceText());
    }

    @Override
    public CompileCodeResponse compileCode(CompileCodeRequest request) {
        return validationSupport.compile(textEditor.getText());
    }

    @Override
    public RunCodeResponse runCode(RunCodeRequest request) {
        throw new IllegalStateException("Only script content is runnable.");
    }

    static boolean shouldEnableAiAttachButton(AiChatAttachment attachment, boolean canAttachToAi) {
        return attachment != null || canAttachToAi;
    }

    static void requestRepairIfConfirmed(AiChatAttachment attachment,
                                         ReadCodeResponse validationFailureState,
                                         int confirmationAnswer) {
        requestRepairIfAvailable(attachment, validationFailureState, confirmationAnswer, true, null);
    }

    static void requestRepairIfAvailable(AiChatAttachment attachment,
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

    private void submit() {
        String editedText = textEditor.getText();
        CompileCodeResponse compileResponse = validationSupport.compile(editedText);
        if (compileResponse.getCodeState() != CodeState.RUNNABLE) {
            ReadCodeResponse failureState = validationFailureState(editedText, compileResponse);
            handleValidationFailure(failureState, buildValidationFailureMessage(compileResponse));
            return;
        }

        AiChatCodeOperationResult validationResult = validationSupport.validate(selectedNode(), editedText);
        if (!validationResult.isSuccessful()) {
            ReadCodeResponse failureState = validationFailureState(editedText, validationResult);
            handleValidationFailure(failureState, buildValidationFailureMessage(validationResult));
            return;
        }
        if (aiChatAttachment != null) {
            aiChatAttachment.clearCodeState();
        }
        if (submitHandler != null) {
            submitHandler.accept(editedText);
        }
        dispose();
    }

    private void handleValidationFailure(ReadCodeResponse failureState, String failureMessage) {
        if (aiChatAttachment != null) {
            aiChatAttachment.recordCodeState(failureState);
        }
        if (!canAttachToAi()) {
            showValidationFailureMessage(failureMessage);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
            this,
            buildValidationFailureDialogMessage(failureMessage),
            TextUtils.getText("plugins/script_filter_editor.execution_failed.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);
        requestRepairIfAvailable(aiChatAttachment, failureState, answer, true, this::attachToAi);
    }

    private NodeModel selectedNode() {
        try {
            Controller controller = Controller.getCurrentController();
            if (controller == null) {
                return null;
            }
            IMapSelection selection = controller.getSelection();
            return selection == null ? null : selection.getSelected();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private ReadCodeResponse validationFailureState(String sourceText, CompileCodeResponse compileResponse) {
        CodeStateContent content = new CodeStateContent(sourceText, null);
        CodeStateToken stateToken = compileResponse.getStateToken() == null
            ? CodeStateToken.fromContent(content)
            : compileResponse.getStateToken();
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FilterScriptConditionValidationSupport.FORMULA_CONDITION_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            stateToken,
            content,
            compileResponse.getDiagnostics(),
            compileResponse.getErrorMessage(),
            null,
            null);
    }

    private ReadCodeResponse validationFailureState(String sourceText, AiChatCodeOperationResult validationResult) {
        CodeStateContent content = new CodeStateContent(sourceText, null);
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        if (validationResult.getSourceFingerprint() != null) {
            stateToken.setCodeFingerprint(validationResult.getSourceFingerprint());
        }
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FilterScriptConditionValidationSupport.FORMULA_CONDITION_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            stateToken,
            content,
            CodeStateDiagnostics.sourceDiagnostics(validationResult.getCompilerDiagnostics(), validationResult.getLineNumber()),
            validationResult.getErrorMessage(),
            validationResult.getStandardOutput(),
            validationResult.getResult());
    }

    private Object buildValidationFailureDialogMessage(String failureMessage) {
        javax.swing.JTextArea messageArea = new javax.swing.JTextArea(failureMessage == null ? "" : failureMessage);
        messageArea.setEditable(false);
        messageArea.setLineWrap(false);
        messageArea.setWrapStyleWord(false);
        messageArea.setFont(textEditor.getFont());
        messageArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
            messageArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(700, 360));
        return new Object[] {
            scrollPane,
            TextUtils.getText("plugins/script_filter_editor.execution_failed.ask_for_ai_repair")
        };
    }

    private String buildValidationFailureMessage(CompileCodeResponse compileResponse) {
        return buildValidationFailureMessage(compileResponse.getDiagnostics(), compileResponse.getErrorMessage());
    }

    private String buildValidationFailureMessage(AiChatCodeOperationResult validationResult) {
        return buildValidationFailureMessage(
            CodeStateDiagnostics.sourceDiagnostics(validationResult.getCompilerDiagnostics(), validationResult.getLineNumber()),
            validationResult.getErrorMessage());
    }

    private String buildValidationFailureMessage(List<CodeStateDiagnostic> diagnostics, String errorMessage) {
        StringBuilder builder = new StringBuilder(TextUtils.getText("plugins/script_filter_editor.execution_failed.message"));
        String formattedDiagnostics = CodeStateDiagnosticTextFormatter.format(diagnostics);
        if (formattedDiagnostics != null) {
            builder.append("\n\n").append(TextUtils.getText("plugins/script_filter_editor.execution_failed.diagnostics"));
            builder.append("\n").append(formattedDiagnostics);
            return builder.toString();
        }
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            builder.append("\n\n").append(errorMessage.trim());
        }
        return builder.toString();
    }

    private void showValidationFailureMessage(String failureMessage) {
        JOptionPane.showMessageDialog(
            this,
            failureMessage,
            TextUtils.getText("plugins/script_filter_editor.execution_failed.title"),
            JOptionPane.ERROR_MESSAGE);
    }

    private AiChatAttachment attachToAi() {
        AiChatAttachmentService attachmentService = lookupAiChatAttachmentService();
        if (attachmentService == null || !attachmentService.isAiConfigured()) {
            updateAiAttachButtonState();
            return null;
        }
        AiChatAttachment attachment = attachmentService.attachEditor(
            this,
            FilterScriptConditionValidationSupport.FORMULA_CONDITION_CONTENT_TYPE);
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

    private void cleanupAttachment() {
        AiChatAttachment attachment = aiChatAttachment;
        aiChatAttachment = null;
        if (attachment != null) {
            attachment.detach();
        }
    }

    private final class AttachToAiAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent event) {
            if (aiChatAttachment != null) {
                aiChatAttachment.detach();
                updateAiAttachButtonState();
                return;
            }
            attachToAi();
        }
    }
}

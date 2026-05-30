package org.freeplane.plugin.ai.code;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Objects;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatAttachableEditor;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatCodeEditor;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.chat.LiveChatSessionId;

public class SingleEditorAttachmentService implements AiChatAttachmentService, AttachedEditorProvider {
    private final AIChatPanel aiChatPanel;
    private final AttachedEditorChatModeSettings attachedEditorChatModeSettings;
    private ActiveAttachment activeAttachment;
    private long nextAttachmentId = 1L;

    public SingleEditorAttachmentService(AIChatPanel aiChatPanel,
                                         AttachedEditorChatModeSettings attachedEditorChatModeSettings) {
        this.aiChatPanel = Objects.requireNonNull(aiChatPanel, "aiChatPanel");
        this.attachedEditorChatModeSettings = Objects.requireNonNull(
            attachedEditorChatModeSettings,
            "attachedEditorChatModeSettings");
    }

    @Override
    public synchronized AiChatAttachment attachEditor(AiChatAttachableEditor editor, String contentType) {
        Objects.requireNonNull(editor, "editor");
        String safeContentType = normalizeContentType(contentType);
        if (activeAttachment != null && activeAttachment.editor == editor
            && activeAttachment.contentType.equals(safeContentType)) {
            activeAttachment.handle.showOwningChat();
            return activeAttachment.handle;
        }
        LiveChatSessionId owningSessionId = chooseOwningSession();
        AttachmentHandle handle = new AttachmentHandle(nextAttachmentId++);
        activeAttachment = new ActiveAttachment(handle.id, editor, safeContentType, owningSessionId, handle);
        handle.showOwningChat();
        return handle;
    }

    @Override
    public synchronized ReadAttachedEditorResponse readAttachedEditor() {
        if (activeAttachment == null) {
            return ReadAttachedEditorResponse.detached();
        }
        return new ReadAttachedEditorResponse(
            true,
            activeAttachment.contentType,
            activeAttachment.editor.getText(),
            fingerprint(activeAttachment.editor.getText()),
            activeAttachment.editor instanceof AiChatCodeEditor,
            activeAttachment.latestIssue != null);
    }

    @Override
    public synchronized OverwriteAttachedEditorContentResponse overwriteAttachedEditorContent(String text) {
        ActiveAttachment attachment = requireActiveAttachment();
        attachment.editor.replaceText(text == null ? "" : text);
        return new OverwriteAttachedEditorContentResponse(fingerprint(attachment.editor.getText()));
    }

    @Override
    public synchronized AiChatCodeOperationResult compileAttachedEditorContent() {
        ActiveAttachment attachment = requireActiveAttachment();
        if (!(attachment.editor instanceof AiChatCodeEditor)) {
            throw new IllegalStateException("Compilation is not supported by the attached editor.");
        }
        AiChatCodeOperationResult result = ((AiChatCodeEditor) attachment.editor).compileForAi();
        if (result != null && result.isSuccessful()) {
            attachment.latestIssue = null;
        }
        else {
            attachment.latestIssue = result;
        }
        return result;
    }

    @Override
    public synchronized ReadAttachedEditorLatestIssueResponse getAttachedEditorLatestIssue() {
        ActiveAttachment attachment = requireActiveAttachment();
        return attachment.latestIssue == null
            ? ReadAttachedEditorLatestIssueResponse.noIssue()
            : new ReadAttachedEditorLatestIssueResponse(true, attachment.latestIssue);
    }

    @Override
    public synchronized boolean hasAttachedEditor() {
        return activeAttachment != null;
    }

    @Override
    public synchronized String attachedContentType() {
        return activeAttachment == null ? null : activeAttachment.contentType;
    }

    private synchronized void detach(long attachmentId) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        activeAttachment = null;
    }

    private synchronized void recordIssue(long attachmentId, AiChatCodeOperationResult result) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        activeAttachment.latestIssue = result;
    }

    private synchronized void clearIssue(long attachmentId) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        activeAttachment.latestIssue = null;
    }

    private synchronized void showOwningChat(long attachmentId) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        aiChatPanel.switchToSession(activeAttachment.owningSessionId);
        aiChatPanel.showAndFocusInput();
    }

    private synchronized void requestRepair(long attachmentId, AiChatRepairRequest request) {
        if (activeAttachment == null || activeAttachment.id != attachmentId || request == null) {
            return;
        }
        showOwningChat(attachmentId);
        boolean started = aiChatPanel.submitMessageToSession(
            activeAttachment.owningSessionId,
            buildRepairMessage(request));
        if (!started) {
            LogUtils.warn("Failed to submit attached-editor repair request.");
        }
    }

    private ActiveAttachment requireActiveAttachment() {
        if (activeAttachment == null) {
            throw new IllegalStateException("No editor is attached.");
        }
        return activeAttachment;
    }

    private LiveChatSessionId chooseOwningSession() {
        AttachedEditorChatMode chatMode = attachedEditorChatModeSettings.get();
        LiveChatSessionId currentSessionId = aiChatPanel.currentSessionId();
        if (chatMode == AttachedEditorChatMode.REUSE_CURRENT_CHAT && currentSessionId != null) {
            return currentSessionId;
        }
        return aiChatPanel.startNewChat();
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.trim().isEmpty()
            ? "text/plain"
            : contentType.trim();
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
            } finally {
                formatter.close();
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }

    private String buildRepairMessage(AiChatRepairRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getPrompt());
        builder.append("\n\nCurrent editor text:\n```\n");
        builder.append(request.getSourceText() == null ? "" : request.getSourceText());
        builder.append("\n```\n\nLatest issue:\n");
        appendIssue(builder, request.getIssue());
        return builder.toString();
    }

    private void appendIssue(StringBuilder builder, AiChatCodeOperationResult issue) {
        if (issue == null) {
            builder.append("No issue details available.");
            return;
        }
        builder.append("successful=").append(issue.isSuccessful());
        if (!issue.getCompilerDiagnostics().isEmpty()) {
            builder.append("\ncompilerDiagnostics:\n");
            for (String diagnostic : issue.getCompilerDiagnostics()) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        if (issue.getErrorCategory() != null) {
            builder.append("errorCategory=").append(issue.getErrorCategory()).append('\n');
        }
        if (issue.getErrorMessage() != null) {
            builder.append("errorMessage=").append(issue.getErrorMessage()).append('\n');
        }
        if (issue.getLineNumber() != null) {
            builder.append("lineNumber=").append(issue.getLineNumber()).append('\n');
        }
        if (issue.getResult() != null) {
            builder.append("result=").append(issue.getResult()).append('\n');
        }
        if (issue.getStandardOutput() != null) {
            builder.append("standardOutput=\n").append(issue.getStandardOutput()).append('\n');
        }
    }

    private static final class ActiveAttachment {
        private final long id;
        private final AiChatAttachableEditor editor;
        private final String contentType;
        private final LiveChatSessionId owningSessionId;
        private final AttachmentHandle handle;
        private AiChatCodeOperationResult latestIssue;

        private ActiveAttachment(long id,
                                 AiChatAttachableEditor editor,
                                 String contentType,
                                 LiveChatSessionId owningSessionId,
                                 AttachmentHandle handle) {
            this.id = id;
            this.editor = editor;
            this.contentType = contentType;
            this.owningSessionId = owningSessionId;
            this.handle = handle;
        }
    }

    private final class AttachmentHandle implements AiChatAttachment {
        private final long id;

        private AttachmentHandle(long id) {
            this.id = id;
        }

        @Override
        public void detach() {
            SingleEditorAttachmentService.this.detach(id);
        }

        @Override
        public void showOwningChat() {
            SingleEditorAttachmentService.this.showOwningChat(id);
        }

        @Override
        public void recordIssue(AiChatCodeOperationResult result) {
            SingleEditorAttachmentService.this.recordIssue(id, result);
        }

        @Override
        public void clearIssue() {
            SingleEditorAttachmentService.this.clearIssue(id);
        }

        @Override
        public void requestRepair(AiChatRepairRequest request) {
            SingleEditorAttachmentService.this.requestRepair(id, request);
        }
    }
}

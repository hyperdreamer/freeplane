package org.freeplane.plugin.ai.code;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatAttachableEditor;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.chat.ChatToolAvailability;
import org.freeplane.plugin.ai.chat.LiveChatSessionId;

public class SingleEditorAttachmentService implements AiChatAttachmentService, AiCodeHostService {
    private static final String ATTACHED_EDITOR_CODE_ID_PREFIX = "attached-editor-";

    private final AIChatPanel aiChatPanel;
    private final AttachedEditorChatModeSettings attachedEditorChatModeSettings;
    private final Map<String, ReadCodeResponse> archivedCodeStates = new HashMap<String, ReadCodeResponse>();
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
        ActiveAttachment previousAttachment = activeAttachment;
        AttachmentHandle previousHandle = previousAttachment == null ? null : previousAttachment.handle;
        long attachmentId = nextAttachmentId++;
        LiveChatSessionId owningSessionId = chooseOwningSession();
        AttachmentHandle handle = new AttachmentHandle(attachmentId);
        activeAttachment = new ActiveAttachment(
            attachmentId,
            codeId(attachmentId),
            editor,
            safeContentType,
            owningSessionId,
            handle);
        if (previousAttachment != null) {
            archivedCodeStates.put(previousAttachment.codeId, replacedState(previousAttachment, activeAttachment.codeId));
        }
        if (previousHandle != null) {
            previousHandle.notifyDetached();
        }
        aiChatPanel.setAttachedEditorIndicatorVisible(true);
        handle.showOwningChat();
        return handle;
    }

    @Override
    public synchronized ReadCodeResponse readCode(ReadCodeRequest request) {
        String codeId = normalizedCodeId(request == null ? null : request.getCodeId());
        if (codeId != null) {
            if (activeAttachment != null && activeAttachment.codeId.equals(codeId)) {
                return activeReadCodeResponse(activeAttachment, request == null ? null : request.getFingerprint());
            }
            ReadCodeResponse archivedState = archivedCodeStates.get(codeId);
            if (archivedState != null) {
                return archivedState;
            }
            if (isAttachedEditorCodeId(codeId)) {
                return noCodeState(codeId, null);
            }
            throw new IllegalArgumentException("Unknown codeId: " + codeId);
        }
        ScriptHost host = request == null ? null : request.getHost();
        if (host == null) {
            throw new IllegalArgumentException("host is required when codeId is absent.");
        }
        if (host != ScriptHost.ATTACHED_EDITOR) {
            throw new IllegalStateException("AI code host is not implemented yet.");
        }
        if (activeAttachment == null) {
            return noCodeState(null, null);
        }
        return activeReadCodeResponse(activeAttachment, request == null ? null : request.getFingerprint());
    }

    @Override
    public synchronized WriteCodeResponse writeCode(WriteCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getText() == null) {
            throw new IllegalArgumentException("text is required.");
        }
        ActiveAttachment attachment = requireWritableAttachment(request.getCodeId(), request.getHost());
        assertExpectedFingerprint(request.getExpectedFingerprint(), attachment);
        attachment.editor.replaceText(request.getText());
        attachment.latestCodeState = null;
        return new WriteCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            CodeLifecycleStatus.READY,
            currentFingerprint(attachment));
    }

    @Override
    public synchronized CompileCodeResponse compileCode(CompileCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        ActiveAttachment attachment = requireWritableAttachment(request.getCodeId(), request.getHost());
        assertExpectedFingerprint(request.getExpectedFingerprint(), attachment);
        if (!(attachment.editor instanceof AiCodeEditor)) {
            throw new IllegalStateException("Compilation is not supported by the attached editor.");
        }
        CompileCodeResponse editorResponse = ((AiCodeEditor) attachment.editor).compileCode(request);
        CompileCodeResponse response = normalizedCompileResponse(attachment, editorResponse);
        if (response.getStatus() == CodeLifecycleStatus.FAILED) {
            attachment.latestCodeState = failureStateFromCompileResponse(attachment, response);
        }
        else {
            attachment.latestCodeState = null;
        }
        return response;
    }

    public synchronized boolean hasAttachedEditor() {
        return activeAttachment != null;
    }

    public synchronized String attachedContentType() {
        return activeAttachment == null ? null : activeAttachment.contentType;
    }

    private synchronized void detach(long attachmentId) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        AttachmentHandle handle = activeAttachment.handle;
        archivedCodeStates.put(activeAttachment.codeId, noCodeState(activeAttachment.codeId, activeAttachment.contentType));
        activeAttachment = null;
        aiChatPanel.setAttachedEditorIndicatorVisible(false);
        handle.notifyDetached();
    }

    private synchronized void recordCodeState(long attachmentId, ReadCodeResponse state) {
        if (activeAttachment == null || activeAttachment.id != attachmentId || state == null) {
            return;
        }
        activeAttachment.latestCodeState = normalizedRecordedState(activeAttachment, state);
    }

    private synchronized void clearCodeState(long attachmentId) {
        if (activeAttachment == null || activeAttachment.id != attachmentId) {
            return;
        }
        activeAttachment.latestCodeState = null;
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
        ReadCodeResponse normalizedCodeState = normalizedRepairState(activeAttachment, request.getCodeState());
        showOwningChat(attachmentId);
        boolean started = aiChatPanel.submitMessageToSession(
            activeAttachment.owningSessionId,
            buildRepairMessage(request, normalizedCodeState));
        if (!started) {
            LogUtils.warn("Failed to submit attached-editor repair request.");
        }
    }

    private ActiveAttachment requireWritableAttachment(String requestedCodeId, ScriptHost requestedHost) {
        String codeId = normalizedCodeId(requestedCodeId);
        if (codeId != null) {
            if (activeAttachment != null && activeAttachment.codeId.equals(codeId)) {
                return activeAttachment;
            }
            throw new IllegalStateException("The requested code is not current.");
        }
        if (requestedHost == null) {
            throw new IllegalArgumentException("host is required when codeId is absent.");
        }
        if (requestedHost != ScriptHost.ATTACHED_EDITOR) {
            throw new IllegalStateException("AI code host is not implemented yet.");
        }
        if (activeAttachment == null) {
            throw new IllegalStateException("No editor is attached.");
        }
        return activeAttachment;
    }

    private void assertExpectedFingerprint(String expectedFingerprint, ActiveAttachment attachment) {
        String normalizedFingerprint = normalizeText(expectedFingerprint);
        if (normalizedFingerprint == null) {
            return;
        }
        String currentFingerprint = currentFingerprint(attachment);
        if (!normalizedFingerprint.equals(currentFingerprint)) {
            throw new IllegalStateException("Expected fingerprint does not match the current code.");
        }
    }

    private LiveChatSessionId chooseOwningSession() {
        AttachedEditorChatMode chatMode = attachedEditorChatModeSettings.get();
        LiveChatSessionId currentSessionId = aiChatPanel.currentSessionId();
        if (chatMode == AttachedEditorChatMode.REUSE_CURRENT_CHAT
            && currentSessionId != null
            && hasReadableTools(currentSessionId)) {
            return currentSessionId;
        }
        LiveChatSessionId newSessionId = aiChatPanel.startNewChat();
        ensureReadableTools(newSessionId);
        return newSessionId;
    }

    private boolean hasReadableTools(LiveChatSessionId sessionId) {
        ChatToolAvailability toolAvailability = aiChatPanel.effectiveToolAvailability(sessionId);
        return toolAvailability != null && toolAvailability.includesTools();
    }

    private void ensureReadableTools(LiveChatSessionId sessionId) {
        if (sessionId == null || hasReadableTools(sessionId)) {
            return;
        }
        aiChatPanel.setSessionToolAvailabilityOverride(sessionId, ChatToolAvailability.READING);
    }

    private CompileCodeResponse normalizedCompileResponse(ActiveAttachment attachment, CompileCodeResponse response) {
        if (response == null) {
            throw new IllegalStateException("Attached editor compile returned no response.");
        }
        String currentFingerprint = currentFingerprint(attachment);
        CodeLifecycleStatus status = response.getStatus();
        if (status == null) {
            status = response.getErrorMessage() != null || response.getLineNumber() != null
                || (response.getCompilerDiagnostics() != null && !response.getCompilerDiagnostics().isEmpty())
                    ? CodeLifecycleStatus.FAILED
                    : CodeLifecycleStatus.READY;
        }
        return new CompileCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            status,
            response.getFingerprint() == null ? currentFingerprint : response.getFingerprint(),
            response.getCompilerDiagnostics(),
            response.getErrorMessage(),
            response.getLineNumber());
    }

    private ReadCodeResponse failureStateFromCompileResponse(ActiveAttachment attachment, CompileCodeResponse response) {
        String currentText = currentText(attachment);
        return new ReadCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            response.getStatus(),
            null,
            response.getFingerprint() == null ? fingerprint(currentText) : response.getFingerprint(),
            currentText,
            null,
            response.getCompilerDiagnostics(),
            response.getErrorMessage(),
            response.getLineNumber(),
            null,
            null);
    }

    private ReadCodeResponse normalizedRecordedState(ActiveAttachment attachment, ReadCodeResponse state) {
        String currentText = currentText(attachment);
        String currentFingerprint = fingerprint(currentText);
        return new ReadCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            state.getStatus() == null ? CodeLifecycleStatus.READY : state.getStatus(),
            state.getRunInitiator(),
            state.getFingerprint() == null ? currentFingerprint : state.getFingerprint(),
            state.getCodeText() == null ? currentText : state.getCodeText(),
            state.getReplacementCodeId(),
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse normalizedRepairState(ActiveAttachment attachment, ReadCodeResponse state) {
        if (state == null) {
            return activeReadCodeResponse(attachment, null);
        }
        return normalizedRecordedState(attachment, state);
    }

    private ReadCodeResponse activeReadCodeResponse(ActiveAttachment attachment, String requestedFingerprint) {
        String currentText = currentText(attachment);
        String currentFingerprint = fingerprint(currentText);
        ReadCodeResponse state = attachment.latestCodeState == null
            ? readyState(attachment, currentText, currentFingerprint)
            : attachment.latestCodeState;
        String codeText = normalizeText(requestedFingerprint) != null && normalizeText(requestedFingerprint).equals(currentFingerprint)
            ? null
            : currentText;
        return new ReadCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            state.getStatus(),
            state.getRunInitiator(),
            currentFingerprint,
            codeText,
            state.getReplacementCodeId(),
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse readyState(ActiveAttachment attachment, String currentText, String currentFingerprint) {
        return new ReadCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            CodeLifecycleStatus.READY,
            null,
            currentFingerprint,
            currentText,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private ReadCodeResponse replacedState(ActiveAttachment attachment, String replacementCodeId) {
        ReadCodeResponse previousState = attachment.latestCodeState;
        return new ReadCodeResponse(
            attachment.codeId,
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            CodeLifecycleStatus.REPLACED,
            previousState == null ? null : previousState.getRunInitiator(),
            currentFingerprint(attachment),
            null,
            replacementCodeId,
            previousState == null ? null : previousState.getCompilerDiagnostics(),
            previousState == null ? null : previousState.getErrorMessage(),
            previousState == null ? null : previousState.getLineNumber(),
            previousState == null ? null : previousState.getStdout(),
            previousState == null ? null : previousState.getStructuredResult());
    }

    private ReadCodeResponse noCodeState(String codeId, String contentType) {
        return new ReadCodeResponse(
            codeId,
            ScriptHost.ATTACHED_EDITOR,
            contentType,
            CodeLifecycleStatus.NO_CODE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.trim().isEmpty()
            ? "text/plain"
            : contentType.trim();
    }

    private String normalizedCodeId(String codeId) {
        return normalizeText(codeId);
    }

    private String normalizeText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean isAttachedEditorCodeId(String codeId) {
        return codeId != null && codeId.startsWith(ATTACHED_EDITOR_CODE_ID_PREFIX);
    }

    private String codeId(long attachmentId) {
        return ATTACHED_EDITOR_CODE_ID_PREFIX + attachmentId;
    }

    private String currentText(ActiveAttachment attachment) {
        return attachment.editor.getText() == null ? "" : attachment.editor.getText();
    }

    private String currentFingerprint(ActiveAttachment attachment) {
        return fingerprint(currentText(attachment));
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

    private String buildRepairMessage(AiChatRepairRequest request, ReadCodeResponse codeState) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getPrompt());
        builder.append("\n\nCurrent code state:\n");
        appendCodeState(builder, codeState);
        return builder.toString();
    }

    private void appendCodeState(StringBuilder builder, ReadCodeResponse codeState) {
        if (codeState == null) {
            builder.append("No code state details available.");
            return;
        }
        builder.append("codeId=").append(codeState.getCodeId()).append('\n');
        builder.append("host=").append(codeState.getHost()).append('\n');
        builder.append("contentType=").append(codeState.getContentType()).append('\n');
        builder.append("status=").append(codeState.getStatus()).append('\n');
        if (codeState.getFingerprint() != null) {
            builder.append("fingerprint=").append(codeState.getFingerprint()).append('\n');
        }
        builder.append("codeText:\n```\n");
        builder.append(codeState.getCodeText() == null ? "" : codeState.getCodeText());
        builder.append("\n```\n");
        if (codeState.getCompilerDiagnostics() != null && !codeState.getCompilerDiagnostics().isEmpty()) {
            builder.append("compilerDiagnostics:\n");
            for (String diagnostic : codeState.getCompilerDiagnostics()) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        if (codeState.getErrorMessage() != null) {
            builder.append("errorMessage=").append(codeState.getErrorMessage()).append('\n');
        }
        if (codeState.getLineNumber() != null) {
            builder.append("lineNumber=").append(codeState.getLineNumber()).append('\n');
        }
        if (codeState.getStdout() != null) {
            builder.append("stdout=\n").append(codeState.getStdout()).append('\n');
        }
        if (codeState.getStructuredResult() != null) {
            builder.append("structuredResult=").append(codeState.getStructuredResult()).append('\n');
        }
    }

    private static final class ActiveAttachment {
        private final long id;
        private final String codeId;
        private final AiChatAttachableEditor editor;
        private final String contentType;
        private final LiveChatSessionId owningSessionId;
        private final AttachmentHandle handle;
        private ReadCodeResponse latestCodeState;

        private ActiveAttachment(long id,
                                 String codeId,
                                 AiChatAttachableEditor editor,
                                 String contentType,
                                 LiveChatSessionId owningSessionId,
                                 AttachmentHandle handle) {
            this.id = id;
            this.codeId = codeId;
            this.editor = editor;
            this.contentType = contentType;
            this.owningSessionId = owningSessionId;
            this.handle = handle;
        }
    }

    private final class AttachmentHandle implements AiChatAttachment {
        private final long id;
        private Runnable detachHandler;

        private AttachmentHandle(long id) {
            this.id = id;
        }

        @Override
        public void detach() {
            SingleEditorAttachmentService.this.detach(id);
        }

        @Override
        public void setDetachHandler(Runnable detachHandler) {
            this.detachHandler = detachHandler;
        }

        @Override
        public void showOwningChat() {
            SingleEditorAttachmentService.this.showOwningChat(id);
        }

        @Override
        public void recordCodeState(ReadCodeResponse state) {
            SingleEditorAttachmentService.this.recordCodeState(id, state);
        }

        @Override
        public void clearCodeState() {
            SingleEditorAttachmentService.this.clearCodeState(id);
        }

        @Override
        public void requestRepair(AiChatRepairRequest request) {
            SingleEditorAttachmentService.this.requestRepair(id, request);
        }

        private void notifyDetached() {
            Runnable handler = detachHandler;
            detachHandler = null;
            if (handler != null) {
                handler.run();
            }
        }
    }
}

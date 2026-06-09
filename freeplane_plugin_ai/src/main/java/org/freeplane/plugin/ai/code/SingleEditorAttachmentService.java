package org.freeplane.plugin.ai.code;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatAttachableEditor;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

public class SingleEditorAttachmentService implements AiChatAttachmentService, AiCodeHostService {

    private final AIChatPanel aiChatPanel;
    private final AttachedEditorChatModeSettings attachedEditorChatModeSettings;
    private final Set<AiCodeRunListener> runListeners = java.util.Collections.newSetFromMap(
        new IdentityHashMap<AiCodeRunListener, Boolean>());
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
            editor,
            safeContentType,
            owningSessionId,
            handle);
        if (previousHandle != null) {
            previousHandle.notifyDetached();
        }
        aiChatPanel.setAttachedEditorIndicatorVisible(true);
        handle.showOwningChat();
        return handle;
    }

    @Override
    public synchronized ReadCodeResponse readCode(ReadCodeRequest request) {
        ScriptHost host = request == null ? null : request.getHost();
        if (host != ScriptHost.ATTACHED_EDITOR) {
            throw new IllegalStateException("AI code host is not implemented yet.");
        }
        if (activeAttachment == null) {
            return noCodeState(null);
        }
        return activeReadCodeResponse(activeAttachment, request == null ? null : request.getKnownStateFingerprint());
    }

    @Override
    public synchronized WriteCodeResponse writeCode(WriteCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getContent() == null) {
            throw new IllegalArgumentException("content is required.");
        }
        ActiveAttachment attachment = requireWritableAttachment(request.getHost());
        requireExpectedStateToken(request.getExpectedStateToken(), attachment);
        attachment.editor.replaceCodeStateContent(request.getContent());
        attachment.latestCodeState = null;
        return new WriteCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            CodeLifecycleStatus.READY,
            currentStateToken(attachment));
    }

    @Override
    public synchronized CompileCodeResponse compileCode(CompileCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        ActiveAttachment attachment = requireWritableAttachment(request.getHost());
        requireExpectedStateToken(request.getExpectedStateToken(), attachment);
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

    @Override
    public synchronized RunCodeResponse runCode(RunCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        ActiveAttachment attachment = requireWritableAttachment(request.getHost());
        requireExpectedStateToken(request.getExpectedStateToken(), attachment);
        if (!(attachment.editor instanceof AiCodeEditor)) {
            throw new IllegalStateException("Only script content is runnable.");
        }
        RunCodeResponse response = normalizedRunResponse(attachment, ((AiCodeEditor) attachment.editor).runCode(request));
        attachment.latestCodeState = stateFromRunResponse(attachment, response);
        fireRunFinished(response);
        return response;
    }

    @Override
    public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
        throw new IllegalStateException("Formula evaluation is not supported for attached editors.");
    }

    @Override
    public synchronized void addRunListener(AiCodeRunListener listener) {
        if (listener != null) {
            runListeners.add(listener);
        }
    }

    @Override
    public synchronized void removeRunListener(AiCodeRunListener listener) {
        if (listener != null) {
            runListeners.remove(listener);
        }
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

    private ActiveAttachment requireWritableAttachment(ScriptHost requestedHost) {
        if (requestedHost != ScriptHost.ATTACHED_EDITOR) {
            throw new IllegalStateException("AI code host is not implemented yet.");
        }
        if (activeAttachment == null) {
            throw new IllegalStateException("No editor is attached.");
        }
        return activeAttachment;
    }

    private void requireExpectedStateToken(CodeStateToken expectedStateToken, ActiveAttachment attachment) {
        String expectedStateFingerprint = CodeStateToken.normalizeFingerprint(
            expectedStateToken == null ? null : expectedStateToken.getStateFingerprint());
        if (expectedStateFingerprint == null) {
            throw new IllegalArgumentException("expectedStateToken is required.");
        }
        String currentStateFingerprint = currentStateToken(attachment).getStateFingerprint();
        if (!expectedStateFingerprint.equals(currentStateFingerprint)) {
            throw new IllegalStateException("Expected state token does not match the current code state.");
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
        ToolAvailabilityLevel toolAvailability = aiChatPanel.effectiveToolAvailability(sessionId);
        return toolAvailability != null && toolAvailability.includesTools();
    }

    private void ensureReadableTools(LiveChatSessionId sessionId) {
        if (sessionId == null || hasReadableTools(sessionId)) {
            return;
        }
        aiChatPanel.setSessionToolAvailabilityOverride(sessionId, ToolAvailabilityLevel.READING);
    }

    private CompileCodeResponse normalizedCompileResponse(ActiveAttachment attachment, CompileCodeResponse response) {
        if (response == null) {
            throw new IllegalStateException("Attached editor compile returned no response.");
        }
        CodeStateToken currentStateToken = currentStateToken(attachment);
        CodeLifecycleStatus status = response.getStatus();
        if (status == null) {
            status = response.getErrorMessage() != null
                || (response.getDiagnostics() != null && !response.getDiagnostics().isEmpty())
                    ? CodeLifecycleStatus.FAILED
                    : CodeLifecycleStatus.READY;
        }
        return new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            status,
            response.getStateToken() == null ? currentStateToken : response.getStateToken(),
            response.getDiagnostics(),
            response.getErrorMessage());
    }

    private ReadCodeResponse failureStateFromCompileResponse(ActiveAttachment attachment, CompileCodeResponse response) {
        CodeStateContent currentContent = currentContent(attachment);
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            response.getStatus(),
            null,
            response.getStateToken() == null ? currentStateToken(attachment) : response.getStateToken(),
            currentContent,
            response.getDiagnostics(),
            response.getErrorMessage(),
            null,
            null);
    }

    private RunCodeResponse normalizedRunResponse(ActiveAttachment attachment, RunCodeResponse response) {
        if (response == null) {
            throw new IllegalStateException("Attached editor run returned no response.");
        }
        return new RunCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            response.getStatus(),
            response.getRunInitiator(),
            response.getStateToken() == null ? currentStateToken(attachment) : response.getStateToken(),
            response.getDiagnostics(),
            response.getErrorMessage(),
            response.getStdout(),
            response.getStructuredResult());
    }

    private ReadCodeResponse stateFromRunResponse(ActiveAttachment attachment, RunCodeResponse response) {
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            response.getStatus(),
            response.getRunInitiator(),
            response.getStateToken() == null ? currentStateToken(attachment) : response.getStateToken(),
            currentContent(attachment),
            response.getDiagnostics(),
            response.getErrorMessage(),
            response.getStdout(),
            response.getStructuredResult());
    }

    private ReadCodeResponse normalizedRecordedState(ActiveAttachment attachment, ReadCodeResponse state) {
        CodeStateContent currentContent = currentContent(attachment);
        CodeStateToken currentStateToken = currentStateToken(attachment);
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            state.getStatus() == null ? CodeLifecycleStatus.READY : state.getStatus(),
            state.getRunInitiator(),
            state.getStateToken() == null ? currentStateToken : state.getStateToken(),
            state.getContent() == null ? currentContent : state.getContent(),
            state.getDiagnostics(),
            state.getErrorMessage(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse normalizedRepairState(ActiveAttachment attachment, ReadCodeResponse state) {
        if (state == null) {
            return activeReadCodeResponse(attachment, null);
        }
        ReadCodeResponse normalizedState = normalizedRecordedState(attachment, state);
        if (state.getContent() != null) {
            return normalizedState;
        }
        return new ReadCodeResponse(
            normalizedState.getHost(),
            normalizedState.getContentType(),
            normalizedState.getStatus(),
            normalizedState.getRunInitiator(),
            normalizedState.getStateToken(),
            null,
            normalizedState.getDiagnostics(),
            normalizedState.getErrorMessage(),
            normalizedState.getStdout(),
            normalizedState.getStructuredResult());
    }

    private ReadCodeResponse activeReadCodeResponse(ActiveAttachment attachment, String knownStateFingerprint) {
        CodeStateContent currentContent = currentContent(attachment);
        CodeStateToken currentStateToken = currentStateToken(attachment);
        ReadCodeResponse state = attachment.latestCodeState == null
            ? readyState(attachment, currentContent, currentStateToken)
            : attachment.latestCodeState;
        CodeStateContent responseContent = CodeStateToken.normalizeFingerprint(knownStateFingerprint) != null
            && CodeStateToken.normalizeFingerprint(knownStateFingerprint).equals(currentStateToken.getStateFingerprint())
                ? null
                : currentContent;
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            state.getStatus(),
            state.getRunInitiator(),
            currentStateToken,
            responseContent,
            state.getDiagnostics(),
            state.getErrorMessage(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse readyState(ActiveAttachment attachment,
                                        CodeStateContent currentContent,
                                        CodeStateToken currentStateToken) {
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            attachment.contentType,
            CodeLifecycleStatus.READY,
            null,
            currentStateToken,
            currentContent,
            null,
            null,
            null,
            null);
    }

    private ReadCodeResponse noCodeState(String contentType) {
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            contentType,
            CodeLifecycleStatus.NO_CODE,
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

    private CodeStateContent currentContent(ActiveAttachment attachment) {
        CodeStateContent content = attachment.editor.getCodeStateContent();
        return content == null ? new CodeStateContent("", null) : content;
    }

    private CodeStateToken currentStateToken(ActiveAttachment attachment) {
        return CodeStateToken.fromContent(currentContent(attachment));
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
        builder.append("host=").append(codeState.getHost()).append('\n');
        builder.append("contentType=").append(codeState.getContentType()).append('\n');
        builder.append("status=").append(codeState.getStatus()).append('\n');
        if (codeState.getStateToken() != null) {
            builder.append("stateFingerprint=").append(codeState.getStateToken().getStateFingerprint()).append('\n');
        }
        if (codeState.getContent() != null) {
            if (codeState.getContent().getSourceText() != null) {
                builder.append("sourceText:\n```\n");
                builder.append(codeState.getContent().getSourceText());
                builder.append("\n```\n");
            }
            if (codeState.getContent().getInputText() != null) {
                builder.append("inputText:\n```\n");
                builder.append(codeState.getContent().getInputText());
                builder.append("\n```\n");
            }
        }
        if (codeState.getDiagnostics() != null && !codeState.getDiagnostics().isEmpty()) {
            builder.append("diagnostics:\n");
            for (CodeStateDiagnostic diagnostic : codeState.getDiagnostics()) {
                builder.append("- ").append(diagnostic.getField()).append(": ").append(diagnostic.getMessage());
                if (diagnostic.getLine() != null) {
                    builder.append(" (line ").append(diagnostic.getLine());
                    if (diagnostic.getColumn() != null) {
                        builder.append(", column ").append(diagnostic.getColumn());
                    }
                    builder.append(')');
                }
                builder.append('\n');
            }
        }
        if (codeState.getErrorMessage() != null) {
            builder.append("errorMessage=").append(codeState.getErrorMessage()).append('\n');
        }
        if (codeState.getStdout() != null) {
            builder.append("stdout=\n").append(codeState.getStdout()).append('\n');
        }
        if (codeState.getStructuredResult() != null) {
            builder.append("structuredResult=").append(codeState.getStructuredResult()).append('\n');
        }
    }

    private void fireRunFinished(RunCodeResponse response) {
        List<AiCodeRunListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<AiCodeRunListener>(runListeners);
        }
        for (AiCodeRunListener listener : listeners) {
            listener.runFinished(response);
        }
    }

    private static final class ActiveAttachment {
        private final long id;
        private final AiChatAttachableEditor editor;
        private final String contentType;
        private final LiveChatSessionId owningSessionId;
        private final AttachmentHandle handle;
        private ReadCodeResponse latestCodeState;

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

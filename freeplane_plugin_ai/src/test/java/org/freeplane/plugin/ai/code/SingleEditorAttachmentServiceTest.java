package org.freeplane.plugin.ai.code;

import java.util.Collections;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnostics;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SingleEditorAttachmentServiceTest {

    @Test
    public void attachingSecondEditorReplacesFirstEditor() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId firstSession = LiveChatSessionId.create();
        LiveChatSessionId secondSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(firstSession, secondSession);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment firstAttachment = uut.attachEditor(new FakeCodeEditor("first"), "text/plain");
        uut.attachEditor(new FakeCodeEditor("second"), "text/plain");
        firstAttachment.detach();

        ReadCodeResponse response = readCurrentState(uut);

        assertThat(response.getCodeState()).isEqualTo(CodeState.EDITED);
        assertThat(response.getContent().getSourceText()).isEqualTo("second");
    }

    @Test
    public void attachModeNewChatStartsNewChatAndShowsIt() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId newSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(newSession);
        when(aiChatPanel.effectiveToolAvailability(newSession)).thenReturn(ToolAvailabilityLevel.READING);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel).startNewChat();
        verify(aiChatPanel, never()).setSessionToolAvailabilityOverride(newSession, ToolAvailabilityLevel.READING);
        verify(aiChatPanel).setAttachedEditorIndicatorVisible(true);
        verify(aiChatPanel).switchToSession(newSession);
        verify(aiChatPanel).showAndFocusInput();
    }

    @Test
    public void attachModeNewChatEnsuresReadingToolsWhenNewSessionWouldOtherwiseBeDisabled() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId newSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(newSession);
        when(aiChatPanel.effectiveToolAvailability(newSession)).thenReturn(ToolAvailabilityLevel.DISABLED);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel).setSessionToolAvailabilityOverride(newSession, ToolAvailabilityLevel.READING);
    }

    @Test
    public void attachModeReuseCurrentChatUsesCurrentSessionWhenAvailabilityIsReading() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId currentSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.REUSE_CURRENT_CHAT);
        when(aiChatPanel.currentSessionId()).thenReturn(currentSession);
        when(aiChatPanel.effectiveToolAvailability(currentSession)).thenReturn(ToolAvailabilityLevel.READING);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel, never()).startNewChat();
        verify(aiChatPanel).setAttachedEditorIndicatorVisible(true);
        verify(aiChatPanel).switchToSession(currentSession);
        verify(aiChatPanel).showAndFocusInput();
    }

    @Test
    public void attachModeReuseCurrentChatUsesCurrentSessionWhenAvailabilityIsEditing() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId currentSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.REUSE_CURRENT_CHAT);
        when(aiChatPanel.currentSessionId()).thenReturn(currentSession);
        when(aiChatPanel.effectiveToolAvailability(currentSession)).thenReturn(ToolAvailabilityLevel.EDITING);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel, never()).startNewChat();
        verify(aiChatPanel).switchToSession(currentSession);
    }

    @Test
    public void attachModeReuseCurrentChatStartsNewChatWhenCurrentSessionAvailabilityIsDisabled() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId currentSession = LiveChatSessionId.create();
        LiveChatSessionId newSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.REUSE_CURRENT_CHAT);
        when(aiChatPanel.currentSessionId()).thenReturn(currentSession);
        when(aiChatPanel.effectiveToolAvailability(currentSession)).thenReturn(ToolAvailabilityLevel.DISABLED);
        when(aiChatPanel.startNewChat()).thenReturn(newSession);
        when(aiChatPanel.effectiveToolAvailability(newSession)).thenReturn(ToolAvailabilityLevel.DISABLED);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel).startNewChat();
        verify(aiChatPanel).setSessionToolAvailabilityOverride(newSession, ToolAvailabilityLevel.READING);
        verify(aiChatPanel).switchToSession(newSession);
    }

    @Test
    public void detachInvokesDetachHandlerOnlyOnce() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment attachment = uut.attachEditor(new FakeCodeEditor("text"), "text/plain");
        final int[] detachCalls = new int[] { 0 };
        attachment.setDetachHandler(new Runnable() {
            @Override
            public void run() {
                detachCalls[0]++;
            }
        });

        attachment.detach();
        attachment.detach();

        assertThat(detachCalls[0]).isEqualTo(1);
        assertThat(readCurrentState(uut).getCodeState()).isEqualTo(CodeState.NO_CODE);
        verify(aiChatPanel).setAttachedEditorIndicatorVisible(false);
    }

    @Test
    public void replacingAttachedEditorInvokesPreviousDetachHandler() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create(), LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment firstAttachment = uut.attachEditor(new FakeCodeEditor("first"), "text/plain");
        final int[] detachCalls = new int[] { 0 };
        firstAttachment.setDetachHandler(new Runnable() {
            @Override
            public void run() {
                detachCalls[0]++;
            }
        });

        uut.attachEditor(new FakeCodeEditor("second"), "text/plain");
        firstAttachment.detach();

        assertThat(detachCalls[0]).isEqualTo(1);
    }

    @Test
    public void manualFailureRecordedOnAttachmentAutoPostsAutomaticCodeStatus() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId sessionId = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(sessionId);
        when(aiChatPanel.effectiveToolAvailability(sessionId)).thenReturn(ToolAvailabilityLevel.READING);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment attachment = uut.attachEditor(new FakeCodeEditor("println 1"), "text/x-freeplane-script-groovy");
        attachment.recordCodeState(new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/x-freeplane-script-groovy",
            CodeState.RUN_FAILED,
            ScriptRunInitiator.USER,
            new CodeStateToken("code", "args"),
            new CodeStateContent("println 1", null),
            Collections.singletonList(new CodeStateDiagnostic(CodeStateField.SOURCE_TEXT, "broken", 1, 1)),
            "broken",
            null,
            null));

        verify(aiChatPanel).submitMessageToSession(eq(sessionId), any());
    }

    @Test
    public void reattachingDetachedEditorUsesCurrentChatAtTimeOfNewAttach() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId firstCurrentSession = LiveChatSessionId.create();
        LiveChatSessionId secondCurrentSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.REUSE_CURRENT_CHAT);
        when(aiChatPanel.currentSessionId()).thenReturn(firstCurrentSession, secondCurrentSession);
        when(aiChatPanel.effectiveToolAvailability(firstCurrentSession)).thenReturn(ToolAvailabilityLevel.READING);
        when(aiChatPanel.effectiveToolAvailability(secondCurrentSession)).thenReturn(ToolAvailabilityLevel.READING);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        FakeCodeEditor editor = new FakeCodeEditor("text");

        AiChatAttachment firstAttachment = uut.attachEditor(editor, "text/plain");
        firstAttachment.detach();
        uut.attachEditor(editor, "text/plain");

        verify(aiChatPanel, never()).startNewChat();
        verify(aiChatPanel).setAttachedEditorIndicatorVisible(false);
        verify(aiChatPanel).switchToSession(firstCurrentSession);
        verify(aiChatPanel).switchToSession(secondCurrentSession);
    }

    @Test
    public void compileStoresFailureStateAndClearsItOnSuccess() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        FakeCodeEditor editor = new FakeCodeEditor("text");
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.INVALID_SCRIPT,
            null,
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken at line 2"), 2),
            "Broken"));
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(editor, "text/plain");

        ReadCodeResponse initialState = readCurrentState(uut);
        CompileCodeResponse firstResult = uut.compileCode(new CompileCodeRequest(
            ScriptHost.ATTACHED_EDITOR,
            initialState.getStateToken()));
        ReadCodeResponse firstState = readCurrentState(uut);
        editor.setCompileResponse(new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.RUNNABLE,
            null,
            Collections.<CodeStateDiagnostic>emptyList(),
            null));
        CompileCodeResponse secondResult = uut.compileCode(new CompileCodeRequest(
            ScriptHost.ATTACHED_EDITOR,
            firstState.getStateToken()));
        ReadCodeResponse secondState = readCurrentState(uut);

        assertThat(firstResult.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(firstState.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(firstState.getDiagnostics()).extracting(CodeStateDiagnostic::getMessage).containsExactly("Broken at line 2");
        assertThat(secondResult.getCodeState()).isEqualTo(CodeState.RUNNABLE);
        assertThat(secondState.getCodeState()).isEqualTo(CodeState.RUNNABLE);
        assertThat(secondState.getDiagnostics()).isEmpty();
    }

    @Test
    public void readCodeAlwaysReturnsCurrentContent() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        ReadCodeResponse currentState = readCurrentState(uut);

        assertThat(currentState.getContent().getSourceText()).isEqualTo("text");
        assertThat(currentState.getContent()).isNotNull();
    }

    @Test
    public void writeCodeUpdatesDraftTextOnly() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        FakeCodeEditor editor = new FakeCodeEditor("before");
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(editor, "text/x-freeplane-formula-groovy");

        uut.writeCode(new WriteCodeRequest(
            ScriptHost.ATTACHED_EDITOR,
            new CodeStateContent("after", null),
            readCurrentState(uut).getStateToken()));

        assertThat(editor.getCodeStateContent().getSourceText()).isEqualTo("after");
        assertThat(readCurrentState(uut).getContentType()).isEqualTo("text/x-freeplane-formula-groovy");
    }

    @Test
    public void recordedCodeStateIsExposedThroughReadCodeAndCanBeCleared() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment attachment = uut.attachEditor(new FakeCodeEditor("=broken"), "text/x-freeplane-formula-groovy");
        attachment.recordCodeState(new ReadCodeResponse(
            null,
            null,
            CodeState.INVALID_SCRIPT,
            null,
            null,
            new CodeStateContent("=broken", null),
            CodeStateDiagnostics.sourceDiagnostics(Collections.singletonList("Broken formula"), 7),
            "Broken formula",
            "stdout",
            "result"));

        ReadCodeResponse failedState = readCurrentState(uut);
        attachment.clearCodeState();
        ReadCodeResponse readyState = readCurrentState(uut);

        assertThat(failedState.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(failedState.getContentType()).isEqualTo("text/x-freeplane-formula-groovy");
        assertThat(failedState.getDiagnostics()).extracting(CodeStateDiagnostic::getMessage).containsExactly("Broken formula");
        assertThat(failedState.getStdout()).isEqualTo("stdout");
        assertThat(failedState.getStructuredResult()).isEqualTo("result");
        assertThat(readyState.getCodeState()).isEqualTo(CodeState.EDITED);
    }

    private ReadCodeResponse readCurrentState(SingleEditorAttachmentService uut) {
        return uut.readCode(new ReadCodeRequest(ScriptHost.ATTACHED_EDITOR));
    }

    private static CodeStateToken token(String argumentsFingerprint) {
        return new CodeStateToken("code", argumentsFingerprint);
    }

    private static class FakeCodeEditor implements AiCodeEditor {
        private CodeStateContent content;
        private CompileCodeResponse compileResponse = new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeState.RUNNABLE,
            token("initial-fingerprint"),
            Collections.<CodeStateDiagnostic>emptyList(),
            null);

        private FakeCodeEditor(String text) {
            this.content = new CodeStateContent(text, null);
        }

        @Override
        public CodeStateContent getCodeStateContent() {
            return content;
        }

        @Override
        public void replaceCodeStateContent(CodeStateContent content) {
            this.content = content == null ? new CodeStateContent("", null) : content;
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            return compileResponse;
        }

        @Override
        public RunCodeResponse runCode(RunCodeRequest request) {
            return new RunCodeResponse(
                ScriptHost.ATTACHED_EDITOR,
                "text/plain",
                CodeState.RUN_SUCCEEDED,
                ScriptRunInitiator.AI,
                token("run-fingerprint"),
                null,
                null,
                null,
                null);
        }

        private void setCompileResponse(CompileCodeResponse compileResponse) {
            this.compileResponse = compileResponse;
        }
    }
}

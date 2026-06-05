package org.freeplane.plugin.ai.code;

import java.util.Collections;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(response.getCodeText()).isEqualTo("second");
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
        assertThat(readCurrentState(uut).getStatus()).isEqualTo(CodeLifecycleStatus.NO_CODE);
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
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.FAILED,
            "failure-fingerprint",
            Collections.singletonList("Broken at line 2"),
            "Broken",
            2));
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(editor, "text/plain");

        CompileCodeResponse firstResult = uut.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        ReadCodeResponse firstState = readCurrentState(uut);
        editor.setCompileResponse(new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            "success-fingerprint",
            Collections.<String>emptyList(),
            null,
            null));
        CompileCodeResponse secondResult = uut.compileCode(new CompileCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        ReadCodeResponse secondState = readCurrentState(uut);

        assertThat(firstResult.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(firstState.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(firstState.getCompilerDiagnostics()).containsExactly("Broken at line 2");
        assertThat(secondResult.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(secondState.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(secondState.getCompilerDiagnostics()).isNull();
    }

    @Test
    public void readCodeUsesFingerprintToSuppressUnchangedText() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        ReadCodeResponse fullState = readCurrentState(uut);
        ReadCodeResponse fingerprintState = uut.readCode(new ReadCodeRequest(
            null,
            ScriptHost.ATTACHED_EDITOR,
            fullState.getFingerprint()));

        assertThat(fullState.getCodeText()).isEqualTo("text");
        assertThat(fingerprintState.getCodeText()).isNull();
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

        uut.writeCode(new WriteCodeRequest(null, ScriptHost.ATTACHED_EDITOR, "after", null));

        assertThat(editor.getText()).isEqualTo("after");
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
            null,
            CodeLifecycleStatus.FAILED,
            null,
            null,
            "=broken",
            null,
            Collections.singletonList("Broken formula"),
            "Broken formula",
            7,
            "stdout",
            "result"));

        ReadCodeResponse failedState = readCurrentState(uut);
        attachment.clearCodeState();
        ReadCodeResponse readyState = readCurrentState(uut);

        assertThat(failedState.getStatus()).isEqualTo(CodeLifecycleStatus.FAILED);
        assertThat(failedState.getContentType()).isEqualTo("text/x-freeplane-formula-groovy");
        assertThat(failedState.getCompilerDiagnostics()).containsExactly("Broken formula");
        assertThat(failedState.getStdout()).isEqualTo("stdout");
        assertThat(failedState.getStructuredResult()).isEqualTo("result");
        assertThat(readyState.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
    }

    private ReadCodeResponse readCurrentState(SingleEditorAttachmentService uut) {
        return uut.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
    }

    private static class FakeCodeEditor implements AiCodeEditor {
        private String text;
        private CompileCodeResponse compileResponse = new CompileCodeResponse(
            null,
            ScriptHost.ATTACHED_EDITOR,
            "text/plain",
            CodeLifecycleStatus.READY,
            "initial-fingerprint",
            Collections.<String>emptyList(),
            null,
            null);

        private FakeCodeEditor(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void replaceText(String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            return compileResponse;
        }

        @Override
        public RunScriptResponse runScript(RunScriptRequest request) {
            return new RunScriptResponse(
                null,
                ScriptHost.ATTACHED_EDITOR,
                "text/plain",
                CodeLifecycleStatus.SUCCEEDED,
                ScriptRunInitiator.AI,
                "run-fingerprint",
                null,
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

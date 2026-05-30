package org.freeplane.plugin.ai.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatCodeEditor;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.chat.LiveChatSessionId;
import org.junit.Test;

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

        ReadAttachedEditorResponse response = uut.readAttachedEditor();

        assertThat(response.isAttached()).isTrue();
        assertThat(response.getText()).isEqualTo("second");
    }

    @Test
    public void attachModeNewChatStartsNewChatAndShowsIt() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId newSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(newSession);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel).startNewChat();
        verify(aiChatPanel).switchToSession(newSession);
        verify(aiChatPanel).showAndFocusInput();
    }

    @Test
    public void attachModeReuseCurrentChatUsesCurrentSessionWhenAvailable() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        LiveChatSessionId currentSession = LiveChatSessionId.create();
        when(settings.get()).thenReturn(AttachedEditorChatMode.REUSE_CURRENT_CHAT);
        when(aiChatPanel.currentSessionId()).thenReturn(currentSession);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        uut.attachEditor(new FakeCodeEditor("text"), "text/plain");

        verify(aiChatPanel, never()).startNewChat();
        verify(aiChatPanel).switchToSession(currentSession);
        verify(aiChatPanel).showAndFocusInput();
    }

    @Test
    public void compileStoresLatestIssueOnlyOnFailureAndClearsItOnSuccess() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        FakeCodeEditor editor = new FakeCodeEditor("text");
        AiChatCodeOperationResult failure = new AiChatCodeOperationResult(
            false,
            Collections.singletonList("Broken at line 2"),
            null,
            null,
            "compile",
            "Broken",
            2,
            "failure-fingerprint");
        AiChatCodeOperationResult success = new AiChatCodeOperationResult(
            true,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            "success-fingerprint");
        editor.setCompileResult(failure);
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);
        uut.attachEditor(editor, "text/plain");

        AiChatCodeOperationResult firstResult = uut.compileAttachedEditorContent();
        ReadAttachedEditorLatestIssueResponse firstIssue = uut.getAttachedEditorLatestIssue();
        editor.setCompileResult(success);
        AiChatCodeOperationResult secondResult = uut.compileAttachedEditorContent();
        ReadAttachedEditorLatestIssueResponse secondIssue = uut.getAttachedEditorLatestIssue();

        assertThat(firstResult).isSameAs(failure);
        assertThat(firstIssue.isHasIssue()).isTrue();
        assertThat(firstIssue.getIssue()).isSameAs(failure);
        assertThat(secondResult).isSameAs(success);
        assertThat(secondIssue.isHasIssue()).isFalse();
    }

    @Test
    public void detachIsIdempotent() {
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AttachedEditorChatModeSettings settings = mock(AttachedEditorChatModeSettings.class);
        when(settings.get()).thenReturn(AttachedEditorChatMode.NEW_CHAT);
        when(aiChatPanel.startNewChat()).thenReturn(LiveChatSessionId.create());
        SingleEditorAttachmentService uut = new SingleEditorAttachmentService(aiChatPanel, settings);

        AiChatAttachment attachment = uut.attachEditor(new FakeCodeEditor("text"), "text/plain");
        attachment.detach();
        attachment.detach();

        assertThat(uut.readAttachedEditor().isAttached()).isFalse();
    }

    private static class FakeCodeEditor implements AiChatCodeEditor {
        private String text;
        private AiChatCodeOperationResult compileResult = new AiChatCodeOperationResult(
            true,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            "initial-fingerprint");

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
        public AiChatCodeOperationResult compileForAi() {
            return compileResult;
        }

        private void setCompileResult(AiChatCodeOperationResult compileResult) {
            this.compileResult = compileResult;
        }
    }
}

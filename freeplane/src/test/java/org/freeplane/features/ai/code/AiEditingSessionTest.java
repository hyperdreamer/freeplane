package org.freeplane.features.ai.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.swing.JToggleButton;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AiEditingSessionTest {
    @Test
    public void startAttachesEditorAndSelectsButton() {
        AiChatAttachableEditor editor = mock(AiChatAttachableEditor.class);
        AiChatAttachmentService service = mock(AiChatAttachmentService.class);
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        when(service.isAiConfigured()).thenReturn(true);
        when(service.attachEditor(editor, "text/test")).thenReturn(attachment);
        JToggleButton button = new JToggleButton();

        AiEditingSession session = new AiEditingSession(editor, "text/test", button, () -> service);

        assertThat(session.canStart()).isTrue();
        assertThat(session.start()).isTrue();
        assertThat(session.isActive()).isTrue();
        assertThat(button.isSelected()).isTrue();
        verify(service).attachEditor(editor, "text/test");
    }

    @Test
    public void externalDetachClearsSessionAndLeavesButtonAvailable() {
        AiChatAttachableEditor editor = mock(AiChatAttachableEditor.class);
        AiChatAttachmentService service = mock(AiChatAttachmentService.class);
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        when(service.isAiConfigured()).thenReturn(true);
        when(service.attachEditor(any(AiChatAttachableEditor.class), eq("text/test"))).thenReturn(attachment);
        ArgumentCaptor<Runnable> detachHandler = ArgumentCaptor.forClass(Runnable.class);
        JToggleButton button = new JToggleButton();

        AiEditingSession session = new AiEditingSession(editor, "text/test", button, () -> service);
        session.start();
        verify(attachment).setDetachHandler(detachHandler.capture());

        detachHandler.getValue().run();

        assertThat(session.isActive()).isFalse();
        assertThat(button.isSelected()).isFalse();
        assertThat(button.isEnabled()).isTrue();
    }

    @Test
    public void repairRequestStartsSessionAndForwardsFailureState() {
        AiChatAttachableEditor editor = mock(AiChatAttachableEditor.class);
        AiChatAttachmentService service = mock(AiChatAttachmentService.class);
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        ReadCodeResponse failureState = mock(ReadCodeResponse.class);
        when(service.isAiConfigured()).thenReturn(true);
        when(service.attachEditor(editor, "text/test")).thenReturn(attachment);
        JToggleButton button = new JToggleButton();

        AiEditingSession session = new AiEditingSession(editor, "text/test", button, () -> service);

        assertThat(session.askForRepair(failureState, "Repair this text.")).isTrue();

        verify(attachment).recordCodeState(failureState);
        ArgumentCaptor<AiChatRepairRequest> request = ArgumentCaptor.forClass(AiChatRepairRequest.class);
        verify(attachment).requestRepair(request.capture());
        assertThat(request.getValue().getCodeState()).isSameAs(failureState);
        assertThat(request.getValue().getPrompt()).isEqualTo("Repair this text.");
    }

    @Test
    public void closeEndsSessionAndPreventsRestart() {
        AiChatAttachableEditor editor = mock(AiChatAttachableEditor.class);
        AiChatAttachmentService service = mock(AiChatAttachmentService.class);
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        when(service.isAiConfigured()).thenReturn(true);
        when(service.attachEditor(editor, "text/test")).thenReturn(attachment);
        JToggleButton button = new JToggleButton();

        AiEditingSession session = new AiEditingSession(editor, "text/test", button, () -> service);
        session.start();
        session.close();

        verify(attachment).detach();
        assertThat(session.isActive()).isFalse();
        assertThat(session.canStart()).isFalse();
        assertThat(session.start()).isFalse();
        assertThat(button.isSelected()).isFalse();
        assertThat(button.isEnabled()).isFalse();
    }
}

package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ScriptEditorPanelTest {

    @Test
    public void recordsFailedManualRunStateWithoutAutoRepairRequest() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        RunCodeResponse response = failedRunResponse();

        CodeStateContent content = new CodeStateContent("println 1", "{");
        ReadCodeResponse codeState = ScriptEditorPanel.recordAttachedManualRunState(attachment, response, content);

        verify(attachment).recordCodeState(codeState);
        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
        assertThat(codeState.getDiagnostics()).containsExactlyElementsOf(response.getDiagnostics());
    }

    @Test
    public void requestsRepairOnlyWhenExplicitlyInvoked() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        ReadCodeResponse codeState = ScriptEditorPanel.manualRunCodeState(failedRunResponse(), new CodeStateContent("println 1", "{"));

        ScriptEditorPanel.requestAttachedManualRepair(attachment, codeState);

        assertRepairRequestedWithCodeState(attachment, codeState);
    }

    @Test
    public void manualAttachedScriptFailureRequestsRepairOnlyAfterAcceptedConfirmation() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        RunCodeResponse response = failedRunResponse();
        ReadCodeResponse codeState = ScriptEditorPanel.manualRunCodeState(response, new CodeStateContent("println 1", "{"));

        ScriptEditorPanel.requestAttachedManualRepairIfConfirmed(
            attachment,
            codeState,
            response,
            runResponse -> JOptionPane.YES_OPTION);

        assertRepairRequestedWithCodeState(attachment, codeState);
    }

    @Test
    public void manualAttachedScriptFailureDoesNotRequestRepairWhenConfirmationDeclined() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        RunCodeResponse response = failedRunResponse();
        ReadCodeResponse codeState = ScriptEditorPanel.manualRunCodeState(response, new CodeStateContent("println 1", "{"));

        ScriptEditorPanel.requestAttachedManualRepairIfConfirmed(
            attachment,
            codeState,
            response,
            runResponse -> JOptionPane.NO_OPTION);

        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
    }

    @Test
    public void manualAttachedScriptFailureAttachesAfterAcceptedConfirmationWhenUnattached() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        RunCodeResponse response = failedRunResponse();
        ReadCodeResponse codeState = ScriptEditorPanel.manualRunCodeState(response, new CodeStateContent("println 1", "{"));

        ScriptEditorPanel.requestAttachedManualRepairIfAvailable(
            null,
            codeState,
            response,
            true,
            runResponse -> JOptionPane.YES_OPTION,
            () -> attachment);

        verify(attachment).recordCodeState(codeState);
        assertRepairRequestedWithCodeState(attachment, codeState);
    }

    @Test
    public void manualAttachedScriptFailureDoesNotConfirmOrAttachWhenAiRepairUnavailable() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        AtomicBoolean confirmationShown = new AtomicBoolean(false);
        AtomicBoolean attachmentRequested = new AtomicBoolean(false);
        RunCodeResponse response = failedRunResponse();
        ReadCodeResponse codeState = ScriptEditorPanel.manualRunCodeState(response, new CodeStateContent("println 1", "{"));

        ScriptEditorPanel.requestAttachedManualRepairIfAvailable(
            null,
            codeState,
            response,
            false,
            runResponse -> {
                confirmationShown.set(true);
                return JOptionPane.YES_OPTION;
            },
            () -> {
                attachmentRequested.set(true);
                return attachment;
            });

        assertThat(confirmationShown.get()).isFalse();
        assertThat(attachmentRequested.get()).isFalse();
        verify(attachment, never()).recordCodeState(any(ReadCodeResponse.class));
        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
    }

    @Test
    public void attachAiButtonIsEnabledOnlyWhenAttachedOrAiRepairAvailable() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);

        assertThat(ScriptEditorPanel.shouldEnableAiAttachButton(null, false)).isFalse();
        assertThat(ScriptEditorPanel.shouldEnableAiAttachButton(null, true)).isTrue();
        assertThat(ScriptEditorPanel.shouldEnableAiAttachButton(attachment, false)).isTrue();
    }

    private void assertRepairRequestedWithCodeState(AiChatAttachment attachment, ReadCodeResponse codeState) {
        ArgumentCaptor<AiChatRepairRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRepairRequest.class);
        verify(attachment).requestRepair(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCodeState()).isSameAs(codeState);
        assertThat(requestCaptor.getValue().getPrompt()).contains("run manually and failed");
    }

    private RunCodeResponse failedRunResponse() {
        CodeStateContent content = new CodeStateContent("println 1", "{");
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        return new RunCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            "text/x-freeplane-script-groovy",
            CodeState.INVALID_ARGUMENTS_JSON,
            ScriptRunInitiator.USER,
            stateToken,
            Collections.singletonList(new CodeStateDiagnostic(CodeStateField.ARGUMENTS_JSON, "broken json", 4, 39)),
            "Arguments JSON is invalid.",
            null,
            null);
    }
}

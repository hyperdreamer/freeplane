package org.freeplane.plugin.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class FormulaEditorTest {

    @Test
    public void formulaValidationFailureRequestsRepairOnlyAfterAcceptedConfirmation() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        ReadCodeResponse validationFailureState = validationFailureState();

        FormulaEditor.requestFormulaRepairIfConfirmed(
            attachment,
            validationFailureState,
            JOptionPane.YES_OPTION);

        ArgumentCaptor<AiChatRepairRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRepairRequest.class);
        verify(attachment).requestRepair(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCodeState()).isSameAs(validationFailureState);
        assertThat(requestCaptor.getValue().getPrompt()).contains("Repair the attached Freeplane formula");
    }

    @Test
    public void formulaValidationFailureDoesNotRequestRepairWhenConfirmationDeclined() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);

        FormulaEditor.requestFormulaRepairIfConfirmed(
            attachment,
            validationFailureState(),
            JOptionPane.NO_OPTION);

        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
    }

    @Test
    public void formulaValidationFailureAttachesAfterAcceptedConfirmationWhenUnattached() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        ReadCodeResponse validationFailureState = validationFailureState();

        FormulaEditor.requestFormulaRepairIfAvailable(
            null,
            validationFailureState,
            JOptionPane.YES_OPTION,
            true,
            () -> attachment);

        verify(attachment).recordCodeState(validationFailureState);
        ArgumentCaptor<AiChatRepairRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRepairRequest.class);
        verify(attachment).requestRepair(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCodeState()).isSameAs(validationFailureState);
    }

    @Test
    public void formulaValidationFailureDoesNotAttachWhenAiRepairUnavailable() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        AtomicBoolean attachmentRequested = new AtomicBoolean(false);

        FormulaEditor.requestFormulaRepairIfAvailable(
            null,
            validationFailureState(),
            JOptionPane.YES_OPTION,
            false,
            () -> {
                attachmentRequested.set(true);
                return attachment;
            });

        assertThat(attachmentRequested.get()).isFalse();
        verify(attachment, never()).recordCodeState(any(ReadCodeResponse.class));
        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
    }

    @Test
    public void attachAiButtonIsEnabledOnlyWhenAttachedOrAiRepairAvailable() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);

        assertThat(FormulaEditor.shouldEnableAiAttachButton(null, false)).isFalse();
        assertThat(FormulaEditor.shouldEnableAiAttachButton(null, true)).isTrue();
        assertThat(FormulaEditor.shouldEnableAiAttachButton(attachment, false)).isTrue();
    }

    private ReadCodeResponse validationFailureState() {
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FormulaTextTransformer.AI_ATTACHMENT_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            null,
            new CodeStateContent("=broken", null),
            null,
            "broken",
            null,
            null);
    }
}

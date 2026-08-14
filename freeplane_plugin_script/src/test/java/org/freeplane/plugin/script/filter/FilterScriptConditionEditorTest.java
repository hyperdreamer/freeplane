package org.freeplane.plugin.script.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import javax.swing.JOptionPane;

import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class FilterScriptConditionEditorTest {

    @Test
    public void requestRepairIfConfirmedUsesAttachedCodeState() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);
        ReadCodeResponse validationFailureState = validationFailureState();

        FilterScriptConditionEditor.requestRepairIfConfirmed(
            attachment,
            validationFailureState,
            JOptionPane.YES_OPTION);

        ArgumentCaptor<AiChatRepairRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRepairRequest.class);
        verify(attachment).requestRepair(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCodeState()).isSameAs(validationFailureState);
        assertThat(requestCaptor.getValue().getPrompt()).contains("filter condition");
    }

    @Test
    public void requestRepairDoesNotRunWhenConfirmationIsDeclined() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);

        FilterScriptConditionEditor.requestRepairIfConfirmed(
            attachment,
            validationFailureState(),
            JOptionPane.NO_OPTION);

        verify(attachment, never()).requestRepair(any(AiChatRepairRequest.class));
    }

    @Test
    public void shouldEnableAiAttachButtonOnlyWhenAttachedOrAiAvailable() {
        AiChatAttachment attachment = mock(AiChatAttachment.class);

        assertThat(FilterScriptConditionEditor.shouldEnableAiAttachButton(null, false)).isFalse();
        assertThat(FilterScriptConditionEditor.shouldEnableAiAttachButton(null, true)).isTrue();
        assertThat(FilterScriptConditionEditor.shouldEnableAiAttachButton(attachment, false)).isTrue();
    }

    private ReadCodeResponse validationFailureState() {
        return new ReadCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FilterScriptConditionValidationSupport.FORMULA_CONDITION_CONTENT_TYPE,
            CodeState.INVALID_SCRIPT,
            null,
            null,
            new CodeStateContent("broken", null),
            Collections.emptyList(),
            "broken",
            null,
            null);
    }
}

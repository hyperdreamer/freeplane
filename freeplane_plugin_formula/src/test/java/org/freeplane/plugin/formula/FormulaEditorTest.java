package org.freeplane.plugin.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.mode.Controller;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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

    @Test
    public void compileCodeReturnsGroovyDiagnosticLocationsAlignedWithVisibleFormulaText() throws Exception {
        ensureScriptClasspath();

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            CompileCodeResponse response = FormulaEditor.compileFormulaCodeStateContent(
                new CodeStateContent("=import a.A\nimport b.B\n1", null));

            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getDiagnostics())
                .extracting(CodeStateDiagnostic::getLine, CodeStateDiagnostic::getColumn)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, 2),
                    org.assertj.core.groups.Tuple.tuple(2, 1));
            assertThat(response.getErrorMessage()).isEqualTo("Groovy compilation failed with 2 diagnostics.");
        }
    }

    @Test
    public void buildValidationFailureMessageShowsDiagnosticsWithoutDuplicatingSummary() {
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            String message = FormulaEditor.buildValidationFailureMessage(
                Collections.singletonList(new CodeStateDiagnostic(
                    org.freeplane.features.ai.code.CodeStateField.SOURCE_TEXT,
                    "Broken",
                    4,
                    9)),
                "Groovy compilation failed with 1 diagnostic.");

            assertThat(message).contains("Diagnostics:");
            assertThat(message).contains("- SOURCE_TEXT (line 4, column 9): Broken");
            assertThat(message).doesNotContain("Groovy compilation failed with 1 diagnostic.");
        }
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

    private void ensureScriptClasspath() throws Exception {
        Method getClasspath = Class.forName("org.freeplane.plugin.script.ScriptResources")
            .getDeclaredMethod("getClasspath");
        getClasspath.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> classpath = (java.util.List<String>) getClasspath.invoke(null);
        if (classpath != null) {
            return;
        }
        Method setClasspath = Class.forName("org.freeplane.plugin.script.ScriptResources")
            .getDeclaredMethod("setClasspath", java.util.List.class);
        setClasspath.setAccessible(true);
        setClasspath.invoke(null, Collections.<String>emptyList());
    }

    private MockedStatic<Controller> mockCurrentController() {
        MockedStatic<Controller> controller = mockStatic(Controller.class);
        Controller currentController = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getBooleanProperty(org.freeplane.plugin.script.ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION))
            .thenReturn(false);
        when(resourceController.getResources()).thenReturn(resourceBundles);
        when(resourceBundles.getResourceString("formula.execution_failed.message"))
            .thenReturn("Formula execution failed.");
        when(resourceBundles.getResourceString("formula.execution_failed.diagnostics"))
            .thenReturn("Diagnostics:");
        controller.when(Controller::getCurrentController).thenReturn(currentController);
        return controller;
    }
}

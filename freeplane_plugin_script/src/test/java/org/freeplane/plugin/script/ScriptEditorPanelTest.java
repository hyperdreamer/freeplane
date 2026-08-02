package org.freeplane.plugin.script;

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
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.mode.Controller;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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

    @Test
    public void failureTextShowsFormattedDiagnosticsForCompileFailure() {
        String failureText = ScriptEditorPanel.failureText(
            Collections.singletonList(new CodeStateDiagnostic(CodeStateField.SOURCE_TEXT, "Broken", 4, 9)),
            "Groovy compilation failed with 1 diagnostic.");

        assertThat(failureText).isEqualTo("- SOURCE_TEXT (line 4, column 9): Broken");
    }

    @Test
    public void compileCodeReturnsGroovyDiagnosticLocations() throws Exception {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            CompileCodeResponse response = ScriptEditorPanel.compileCodeStateContent(
                new CodeStateContent("import a.A\nimport b.B\nprintln 'x'\n", ""));

            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, 1),
                    org.assertj.core.groups.Tuple.tuple(2, 1));
        }
    }

    @Test
    public void runCodeReturnsGroovyDiagnosticLocationsForCompileFailure() throws Exception {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            CompileCodeResponse compileResponse = ScriptEditorPanel.compileCodeStateContent(
                new CodeStateContent("import a.A\nimport b.B\nprintln 'x'\n", ""));
            RunCodeResponse response = ScriptEditorPanel.validationFailureAsRunResponse(
                compileResponse,
                ScriptRunInitiator.USER);

            assertThat(response).isNotNull();
            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getRunInitiator()).isEqualTo(ScriptRunInitiator.USER);
            assertThat(response.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, 1),
                    org.assertj.core.groups.Tuple.tuple(2, 1));
        }
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

    private MockedStatic<Controller> mockCurrentController() {
        MockedStatic<Controller> controller = mockStatic(Controller.class);
        Controller currentController = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getIntProperty("compiled_script_cache_size", 200)).thenReturn(8);
        controller.when(Controller::getCurrentController).thenReturn(currentController);
        return controller;
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

}

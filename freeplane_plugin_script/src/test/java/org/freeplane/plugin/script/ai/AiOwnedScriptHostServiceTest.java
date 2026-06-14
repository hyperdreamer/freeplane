package org.freeplane.plugin.script.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.FormulaValidationSupport;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.junit.Test;

public class AiOwnedScriptHostServiceTest {
    @Test
    public void defaultConstructorDoesNotRequireCurrentController() {
        assertThatCode(() -> new AiOwnedScriptHostService()).doesNotThrowAnyException();
    }

    @Test
    public void writeCodeCreatesCurrentScriptAndUpdatesItInPlace() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

        WriteCodeResponse first = uut.doWriteCode(writeRequest("println 1", null, null));
        WriteCodeResponse second = uut.doWriteCode(writeRequest("println 2", null, first.getStateToken()));
        ReadCodeResponse current = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(first.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(second.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(current.getCodeState()).isEqualTo(CodeState.EDITED);
        assertThat(current.getContent().getSourceText()).isEqualTo("println 2");
        assertThat(current.getStateToken()).isEqualTo(second.getStateToken());
    }

    @Test
    public void writeCodeRejectsMismatchedExpectedFingerprint() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);
        uut.doWriteCode(writeRequest("println 1", null, null));

        assertThatThrownBy(() -> uut.doWriteCode(new WriteCodeRequest(
            ScriptHost.AI,
            new CodeStateContent("println 2", null),
            new CodeStateToken(null, "wrong"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Expected state token does not match the current code state.");
    }

    @Test
    public void shownUserRunPolicyReturnsWaitingStateAndShowsDialog() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        RecordingDialogFactory dialogFactory = new RecordingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(writeRequest("println 1", null, null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getStateToken()));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(state.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(dialogFactory.dialog.codeShown).isTrue();
        assertThat(dialogFactory.dialog.showAndFocusCalls).isEqualTo(1);
    }

    @Test
    public void shownUserRunPolicyPreservesWrittenScriptWhenDialogLoadsCurrentState() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        LoadingDialogFactory dialogFactory = new LoadingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(writeRequest("println 1", null, null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getStateToken()));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(response.getStateToken()).isEqualTo(written.getStateToken());
        assertThat(state.getContent().getSourceText()).isEqualTo("println 1");
        assertThat(state.getStateToken()).isEqualTo(written.getStateToken());
        assertThat(dialogFactory.dialog.currentContent.getSourceText()).isEqualTo("println 1");
    }

    @Test
    public void dialogCancelAfterWaitingUpdatesStateToUserRunCancelled() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        RecordingDialogFactory dialogFactory = new RecordingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(writeRequest("println 1", null, null));

        RunCodeResponse waiting = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getStateToken()));
        uut.dialogCancelled();
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(waiting.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(state.getCodeState()).isEqualTo(CodeState.USER_RUN_CANCELLED);
    }

    @Test
    public void shownAiRunPolicyShowsDialogBeforeAttemptingExecution() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_AI_RUN);
        LoadingDialogFactory dialogFactory = new LoadingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(writeRequest("if (", null, null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getStateToken()));

        assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(dialogFactory.dialog.codeShown).isTrue();
        assertThat(dialogFactory.dialog.showAndFocusCalls).isEqualTo(1);
    }

    @Test
    public void compileFailsForInvalidInputJsonButWriteAllowsIt() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

        WriteCodeResponse written = uut.doWriteCode(writeRequest("return args", "{", null));
        CompileCodeResponse compileResponse = uut.doCompileCode(new org.freeplane.features.ai.code.CompileCodeRequest(
            ScriptHost.AI,
            written.getStateToken()));

        assertThat(compileResponse.getCodeState()).isEqualTo(CodeState.INVALID_ARGUMENTS_JSON);
        assertThat(compileResponse.getDiagnostics()).hasSize(1);
        assertThat(compileResponse.getDiagnostics().get(0).getField()).isEqualTo(CodeStateField.ARGUMENTS_JSON);
    }

    @Test
    public void evaluateFormulaDelegatesToFormulaValidationSupport() {
        FormulaValidationSupport validationSupport = mock(FormulaValidationSupport.class);
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null, new RecordingDialogFactory(), validationSupport);
        NodeModel nodeModel = new NodeModel("=1", new MapModel((source, targetMap, withChildren) -> null, null, null));
        AiChatCodeOperationResult expectedResult = new AiChatCodeOperationResult(
            true,
            Collections.<String>emptyList(),
            null,
            "2",
            null,
            null,
            null,
            null);
        when(validationSupport.validateFormula(nodeModel, "=1+1")).thenReturn(expectedResult);

        AiChatCodeOperationResult result = uut.doEvaluateFormula(new EvaluateFormulaRequest(nodeModel, "=1+1"));

        assertThat(result).isSameAs(expectedResult);
    }

    @Test
    public void unrestrictedUserRunPermissionsArePermissiveExceptForRecursiveAi() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_USER_RUN_PERMISSION_MODE),
            eq(AiScriptUserRunPermissionMode.AI_SPECIFIC_PERMISSIONS)))
            .thenReturn(AiScriptUserRunPermissionMode.UNRESTRICTED);
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController);

        ScriptingPermissions permissions = uut.userStartedPermissions();

        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_WRITE_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_NETWORK_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_EXEC_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION)).isFalse();
    }

    @Test
    public void aiSpecificUserRunPermissionsReuseConfiguredExternalPermissionsAndBlockRecursiveAi() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_USER_RUN_PERMISSION_MODE),
            eq(AiScriptUserRunPermissionMode.AI_SPECIFIC_PERMISSIONS)))
            .thenReturn(AiScriptUserRunPermissionMode.AI_SPECIFIC_PERMISSIONS);
        when(resourceController.getBooleanProperty(AiOwnedScriptHostService.AI_SCRIPT_WITHOUT_FILE_RESTRICTION, false))
            .thenReturn(true);
        when(resourceController.getBooleanProperty(AiOwnedScriptHostService.AI_SCRIPT_WITHOUT_WRITE_RESTRICTION, false))
            .thenReturn(false);
        when(resourceController.getBooleanProperty(AiOwnedScriptHostService.AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION, false))
            .thenReturn(true);
        when(resourceController.getBooleanProperty(AiOwnedScriptHostService.AI_SCRIPT_WITHOUT_EXEC_RESTRICTION, false))
            .thenReturn(false);
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController);

        ScriptingPermissions permissions = uut.userStartedPermissions();

        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_WRITE_RESTRICTION)).isFalse();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_NETWORK_RESTRICTION)).isTrue();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_EXEC_RESTRICTION)).isFalse();
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION)).isFalse();
    }

    private WriteCodeRequest writeRequest(String sourceText, String argumentsJsonText, CodeStateToken expectedStateToken) {
        return new WriteCodeRequest(ScriptHost.AI, new CodeStateContent(sourceText, argumentsJsonText), expectedStateToken);
    }

    private static class RecordingDialogFactory implements AiOwnedScriptHostService.DialogFactory {
        private final RecordingDialog dialog = new RecordingDialog();

        @Override
        public AiOwnedScriptHostService.DialogHandle create(AiOwnedScriptHostService.CodeStateProvider codeStateProvider,
                                                            AiOwnedScriptHostService.DialogCallbacks callbacks) {
            return dialog;
        }
    }

    private static class LoadingDialogFactory implements AiOwnedScriptHostService.DialogFactory {
        private LoadingDialog dialog;

        @Override
        public AiOwnedScriptHostService.DialogHandle create(AiOwnedScriptHostService.CodeStateProvider codeStateProvider,
                                                            AiOwnedScriptHostService.DialogCallbacks callbacks) {
            dialog = new LoadingDialog(codeStateProvider);
            return dialog;
        }
    }

    private static class RecordingDialog implements AiOwnedScriptHostService.DialogHandle {
        private boolean codeShown;
        private int showAndFocusCalls;

        @Override
        public void showCode() {
            codeShown = true;
        }

        @Override
        public void showAndFocus() {
            showAndFocusCalls++;
        }

        @Override
        public CodeStateContent currentContent() {
            return new CodeStateContent("println 1", null);
        }

        @Override
        public boolean hasCode() {
            return codeShown;
        }

        @Override
        public void hideDialog() {
        }
    }

    private static class LoadingDialog implements AiOwnedScriptHostService.DialogHandle {
        private final AiOwnedScriptHostService.CodeStateProvider codeStateProvider;
        private boolean codeShown;
        private int showAndFocusCalls;
        private CodeStateContent currentContent = new CodeStateContent("", null);

        private LoadingDialog(AiOwnedScriptHostService.CodeStateProvider codeStateProvider) {
            this.codeStateProvider = codeStateProvider;
        }

        @Override
        public void showCode() {
            codeShown = true;
            ReadCodeResponse state = codeStateProvider.readCodeState();
            currentContent = state == null || state.getContent() == null ? new CodeStateContent("", null) : state.getContent();
        }

        @Override
        public void showAndFocus() {
            showAndFocusCalls++;
        }

        @Override
        public CodeStateContent currentContent() {
            return currentContent;
        }

        @Override
        public boolean hasCode() {
            return codeShown;
        }

        @Override
        public void hideDialog() {
        }
    }
}

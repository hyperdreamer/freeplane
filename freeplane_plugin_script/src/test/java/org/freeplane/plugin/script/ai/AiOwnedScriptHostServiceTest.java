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
import org.freeplane.features.ai.code.CodeLifecycleStatus;
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

        WriteCodeResponse first = uut.doWriteCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null));
        WriteCodeResponse second = uut.doWriteCode(new WriteCodeRequest(ScriptHost.AI, "println 2", first.getFingerprint()));
        ReadCodeResponse current = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI, null));

        assertThat(first.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(second.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(current.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(current.getCodeText()).isEqualTo("println 2");
        assertThat(current.getFingerprint()).isEqualTo(second.getFingerprint());
    }

    @Test
    public void writeCodeRejectsMismatchedExpectedFingerprint() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);
        WriteCodeResponse written = uut.doWriteCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null));

        assertThatThrownBy(() -> uut.doWriteCode(new WriteCodeRequest(
            ScriptHost.AI,
            "println 2",
            "wrong")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Expected fingerprint does not match the current code.");
    }

    @Test
    public void shownUserRunPolicyReturnsWaitingStateAndShowsDialog() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        RecordingDialogFactory dialogFactory = new RecordingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getFingerprint()));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.WAITING_FOR_USER_RUN);
        assertThat(state.getStatus()).isEqualTo(CodeLifecycleStatus.WAITING_FOR_USER_RUN);
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
        WriteCodeResponse written = uut.doWriteCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getFingerprint()));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.WAITING_FOR_USER_RUN);
        assertThat(response.getFingerprint()).isEqualTo(written.getFingerprint());
        assertThat(state.getCodeText()).isEqualTo("println 1");
        assertThat(state.getFingerprint()).isEqualTo(written.getFingerprint());
        assertThat(dialogFactory.dialog.currentText).isEqualTo("println 1");
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
        public String currentCodeText() {
            return "println 1";
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
        private String currentText = "";

        private LoadingDialog(AiOwnedScriptHostService.CodeStateProvider codeStateProvider) {
            this.codeStateProvider = codeStateProvider;
        }

        @Override
        public void showCode() {
            codeShown = true;
            ReadCodeResponse state = codeStateProvider.readCodeState();
            currentText = state == null || state.getCodeText() == null ? "" : state.getCodeText();
        }

        @Override
        public void showAndFocus() {
        }

        @Override
        public String currentCodeText() {
            return currentText;
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

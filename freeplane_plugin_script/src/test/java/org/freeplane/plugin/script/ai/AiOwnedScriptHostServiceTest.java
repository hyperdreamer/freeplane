package org.freeplane.plugin.script.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.junit.Test;

public class AiOwnedScriptHostServiceTest {
    @Test
    public void defaultConstructorDoesNotRequireCurrentController() {
        assertThatCode(() -> new AiOwnedScriptHostService()).doesNotThrowAnyException();
    }

    @Test
    public void writeCodeReplacesPreviousScriptAndArchivesReplacedState() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

        WriteCodeResponse first = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 1", null));
        WriteCodeResponse second = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 2", null));
        ReadCodeResponse current = uut.readCode(new ReadCodeRequest(null, ScriptHost.AI, null));
        ReadCodeResponse replaced = uut.readCode(new ReadCodeRequest(first.getCodeId(), null, null));

        assertThat(first.getCodeId()).isEqualTo("ai-script-1");
        assertThat(second.getCodeId()).isEqualTo("ai-script-2");
        assertThat(current.getCodeId()).isEqualTo(second.getCodeId());
        assertThat(current.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(current.getCodeText()).isEqualTo("println 2");
        assertThat(replaced.getStatus()).isEqualTo(CodeLifecycleStatus.REPLACED);
        assertThat(replaced.getReplacementCodeId()).isEqualTo(second.getCodeId());
        assertThat(replaced.getCodeText()).isNull();
    }

    @Test
    public void writeCodeRejectsMismatchedExpectedFingerprint() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);
        WriteCodeResponse written = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 1", null));

        assertThatThrownBy(() -> uut.writeCode(new WriteCodeRequest(
            written.getCodeId(),
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
        WriteCodeResponse written = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 1", null));

        RunScriptResponse response = uut.runScript(new RunScriptRequest(written.getCodeId(), null, null));
        ReadCodeResponse state = uut.readCode(new ReadCodeRequest(written.getCodeId(), null, null));

        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.WAITING_FOR_USER_RUN);
        assertThat(state.getStatus()).isEqualTo(CodeLifecycleStatus.WAITING_FOR_USER_RUN);
        assertThat(dialogFactory.dialog.shownCodeId).isEqualTo(written.getCodeId());
        assertThat(dialogFactory.dialog.showAndFocusCalls).isEqualTo(1);
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

    private static class RecordingDialog implements AiOwnedScriptHostService.DialogHandle {
        private String shownCodeId;
        private int showAndFocusCalls;

        @Override
        public void showCode(String codeId) {
            shownCodeId = codeId;
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
        public boolean showsCode(String codeId) {
            return shownCodeId != null && shownCodeId.equals(codeId);
        }

        @Override
        public void hideDialog() {
        }
    }
}

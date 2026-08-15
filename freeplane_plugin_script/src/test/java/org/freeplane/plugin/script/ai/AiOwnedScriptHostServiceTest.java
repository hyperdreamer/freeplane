package org.freeplane.plugin.script.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteAndRunCodeRequest;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.script.FormulaValidationSupport;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.junit.Test;
import org.mockito.MockedStatic;

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
    public void writeAndRunCodeCreatesAiOwnedStateAndRunsIt() {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockStatic(Controller.class)) {
            ResourceController resourceController = mock(ResourceController.class);
            when(resourceController.getEnumProperty(
                eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
                eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.HIDDEN_AI_RUN);
            when(resourceController.getIntProperty("compiled_script_cache_size", 200)).thenReturn(8);
            Controller currentController = mock(Controller.class);
            ModeController modeController = mock(ModeController.class);
            MapController mapController = mock(MapController.class);
            NodeModel selectedNode = new NodeModel("selected", new MapModel((source, targetMap, withChildren) -> null, null, null));
            when(currentController.getResourceController()).thenReturn(resourceController);
            when(modeController.getMapController()).thenReturn(mapController);
            when(mapController.getSelectedNode()).thenReturn(selectedNode);
            controller.when(Controller::getCurrentController).thenReturn(currentController);
            controller.when(Controller::getCurrentModeController).thenReturn(modeController);

            AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController);

            RunCodeResponse response = uut.doWriteAndRunCode(new WriteAndRunCodeRequest(
                new CodeStateContent("println 'hello'\nreturn 7", null)));
            ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

            assertThat(response.getCodeState()).isEqualTo(CodeState.RUN_SUCCEEDED);
            assertThat(response.getStdout()).contains("hello");
            assertThat(response.getStructuredResult()).isEqualTo(7);
            assertThat(state.getContent().getSourceText()).isEqualTo("println 'hello'\nreturn 7");
            assertThat(state.getStateToken()).isEqualTo(response.getStateToken());
        }
    }

    @Test
    public void writeAndRunCodeReplacesExistingAiOwnedStateWithoutPriorRead() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, new LoadingDialogFactory());

        uut.doWriteAndRunCode(new WriteAndRunCodeRequest(new CodeStateContent("println 1", null)));
        RunCodeResponse response = uut.doWriteAndRunCode(new WriteAndRunCodeRequest(
            new CodeStateContent("println 2", "{}")));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(state.getContent()).isEqualTo(new CodeStateContent("println 2", "{}"));
        assertThat(state.getStateToken()).isEqualTo(response.getStateToken());
    }

    @Test
    public void writeAndRunCodeReturnsGroovyDiagnosticLocations() {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            ResourceController resourceController = mock(ResourceController.class);
            when(resourceController.getEnumProperty(
                eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
                eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.HIDDEN_AI_RUN);
            when(resourceController.getIntProperty("compiled_script_cache_size", 200)).thenReturn(8);
            AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController);

            RunCodeResponse response = uut.doWriteAndRunCode(new WriteAndRunCodeRequest(
                new CodeStateContent("import a.A\nimport b.B\nprintln 'x'\n", null)));

            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    tuple(1, 1),
                    tuple(2, 1));
        }
    }

    @Test
    public void writeAndRunCodeShownUserRunReusesDialogAndPersistsUserEdits() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.SHOWN_USER_RUN);
        LoadingDialogFactory dialogFactory = new LoadingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);

        RunCodeResponse waiting = uut.doWriteAndRunCode(new WriteAndRunCodeRequest(
            new CodeStateContent("println 1", null)));
        dialogFactory.dialog.currentContent = new CodeStateContent("println 2", null);
        uut.dialogCancelled();
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(waiting.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(dialogFactory.dialog.showAndFocusCalls).isEqualTo(1);
        assertThat(state.getCodeState()).isEqualTo(CodeState.USER_RUN_CANCELLED);
        assertThat(state.getContent().getSourceText()).isEqualTo("println 2");
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
    public void writeCodeReturnsTokenMatchingUpdatedDialogContent() {
        LoadingDialogFactory dialogFactory = new LoadingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null, dialogFactory);
        WriteCodeResponse first = uut.doWriteCode(writeRequest("println 1", null, null));
        uut.showCurrentCode();
        assertThat(dialogFactory.dialog.currentContent.getSourceText()).isEqualTo("println 1");

        WriteCodeResponse second = uut.doWriteCode(writeRequest("println 2", null, first.getStateToken()));
        ReadCodeResponse state = uut.doReadCode(new ReadCodeRequest(ScriptHost.AI));

        assertThat(dialogFactory.dialog.currentContent.getSourceText()).isEqualTo("println 2");
        assertThat(state.getContent().getSourceText()).isEqualTo("println 2");
        assertThat(state.getStateToken()).isEqualTo(second.getStateToken());
        assertThatCode(() -> uut.doRunCode(new RunCodeRequest(ScriptHost.AI, second.getStateToken())))
            .doesNotThrowAnyException();
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
    public void hiddenAiRunPolicyDoesNotShowDialogBeforeAttemptingExecution() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getEnumProperty(
            eq(AiOwnedScriptHostService.AI_SCRIPT_EXECUTION_POLICY),
            eq(AiScriptExecutionPolicy.SHOWN_USER_RUN))).thenReturn(AiScriptExecutionPolicy.HIDDEN_AI_RUN);
        LoadingDialogFactory dialogFactory = new LoadingDialogFactory();
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(resourceController, dialogFactory);
        WriteCodeResponse written = uut.doWriteCode(writeRequest("if (", null, null));

        RunCodeResponse response = uut.doRunCode(new RunCodeRequest(ScriptHost.AI, written.getStateToken()));

        assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
        assertThat(dialogFactory.dialog).isNull();
    }

    @Test
    public void compileFailsForInvalidInputJsonButWriteAllowsIt() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

        WriteCodeResponse written = uut.doWriteCode(writeRequest("return args", "{", null));
        CompileCodeResponse compileResponse = uut.doCompileCode(new CompileCodeRequest(
            ScriptHost.AI,
            written.getStateToken()));

        assertThat(compileResponse.getCodeState()).isEqualTo(CodeState.INVALID_ARGUMENTS_JSON);
        assertThat(compileResponse.getDiagnostics()).hasSize(1);
        assertThat(compileResponse.getDiagnostics().get(0).getField()).isEqualTo(CodeStateField.ARGUMENTS_JSON);
    }

    @Test
    public void compileCodeReturnsGroovyDiagnosticLocations() {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

            WriteCodeResponse written = uut.doWriteCode(writeRequest("import a.A\nimport b.B\nprintln 'x'\n", null, null));
            CompileCodeResponse compileResponse = uut.doCompileCode(new CompileCodeRequest(
                ScriptHost.AI,
                written.getStateToken()));

            assertThat(compileResponse.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(compileResponse.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    tuple(1, 1),
                    tuple(2, 1));
            assertThat(compileResponse.getErrorMessage()).isEqualTo("Groovy compilation failed with 2 diagnostics.");
        }
    }

    @Test
    public void runFromDialogReturnsGroovyDiagnosticLocationsForCompileFailure() {
        ensureScriptClasspath();
        try (MockedStatic<Controller> controller = mockCurrentController()) {
            AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);
            uut.doWriteCode(writeRequest("println 1", null, null));

            RunCodeResponse response = uut.runFromDialog(new CodeStateContent("import a.A\nimport b.B\nprintln 'x'\n", null));

            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getRunInitiator()).isEqualTo(ScriptRunInitiator.USER);
            assertThat(response.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    tuple(1, 1),
                    tuple(2, 1));
            assertThat(response.getErrorMessage()).isEqualTo("Groovy compilation failed with 2 diagnostics.");
        }
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

    private MockedStatic<Controller> mockCurrentController() {
        MockedStatic<Controller> controller = mockStatic(Controller.class);
        Controller currentController = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getIntProperty("compiled_script_cache_size", 200)).thenReturn(8);
        controller.when(Controller::getCurrentController).thenReturn(currentController);
        return controller;
    }

    private void ensureScriptClasspath() {
        try {
            Method getClasspath = Class.forName("org.freeplane.plugin.script.ScriptResources")
                .getDeclaredMethod("getClasspath");
            getClasspath.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> classpath = (List<String>) getClasspath.invoke(null);
            if (classpath != null) {
                return;
            }
            Method setClasspath = Class.forName("org.freeplane.plugin.script.ScriptResources")
                .getDeclaredMethod("setClasspath", List.class);
            setClasspath.setAccessible(true);
            setClasspath.invoke(null, Collections.<String>emptyList());
        }
        catch (Exception error) {
            throw new IllegalStateException(error);
        }
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

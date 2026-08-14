package org.freeplane.plugin.script.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FilterScriptConditionValidationSupportTest {

    @Test
    public void compileReportsGroovyDiagnosticsWithLocations() throws Exception {
        ensureScriptClasspath();
        TextController textController = mock(TextController.class);
        FilterScriptConditionValidationSupport uut = new FilterScriptConditionValidationSupport(
            textController,
            (node, script, outStream, errorHandler) -> Boolean.TRUE);

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            CompileCodeResponse response = uut.compile("import a.A\nimport b.B\ntrue");

            assertThat(response.getCodeState()).isEqualTo(CodeState.INVALID_SCRIPT);
            assertThat(response.getDiagnostics())
                .extracting(diagnostic -> diagnostic.getLine(), diagnostic -> diagnostic.getColumn())
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, 1),
                    org.assertj.core.groups.Tuple.tuple(2, 1));
        }
    }

    @Test
    public void validateAcceptsBooleanAndNumberResults() throws Exception {
        ensureScriptClasspath();
        TextController textController = mockTextController();
        NodeModel node = mock(NodeModel.class);

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            FilterScriptConditionValidationSupport booleanSupport = new FilterScriptConditionValidationSupport(
                textController,
                (validatedNode, script, outStream, errorHandler) -> Boolean.TRUE);
            FilterScriptConditionValidationSupport numberSupport = new FilterScriptConditionValidationSupport(
                textController,
                (validatedNode, script, outStream, errorHandler) -> Integer.valueOf(0));

            AiChatCodeOperationResult booleanResult = booleanSupport.validate(node, "true");
            AiChatCodeOperationResult numberResult = numberSupport.validate(node, "0");

            assertThat(booleanResult.isSuccessful()).isTrue();
            assertThat(booleanResult.getResult()).isEqualTo("true");
            assertThat(numberResult.isSuccessful()).isTrue();
            assertThat(numberResult.getResult()).isEqualTo("0");
            assertThat(FilterScriptConditionValidationSupport.conditionResultAsBoolean(Boolean.TRUE)).isTrue();
            assertThat(FilterScriptConditionValidationSupport.conditionResultAsBoolean(Integer.valueOf(0))).isFalse();
        }
    }

    @Test
    public void validateRejectsOtherResultTypes() throws Exception {
        ensureScriptClasspath();
        TextController textController = mockTextController();
        FilterScriptConditionValidationSupport uut = new FilterScriptConditionValidationSupport(
            textController,
            (node, script, outStream, errorHandler) -> "not a condition result");

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            AiChatCodeOperationResult result = uut.validate(mock(NodeModel.class), "true");

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getErrorMessage()).contains("Boolean or Number");
            assertThat(result.getCompilerDiagnostics()).hasSize(1);
            assertThat(result.getCompilerDiagnostics().get(0)).contains("Boolean or Number");
        }
    }

    private TextController mockTextController() {
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(eq(true), any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        return textController;
    }

    private MockedStatic<Controller> mockCurrentController() {
        MockedStatic<Controller> controller = mockStatic(Controller.class);
        Controller currentController = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getBooleanProperty(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION))
            .thenReturn(false);
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

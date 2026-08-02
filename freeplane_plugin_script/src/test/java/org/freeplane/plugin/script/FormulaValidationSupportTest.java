package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FormulaValidationSupportTest {

    @Test
    public void validateFormulaMapsSuccessfulValidationResult() throws Exception {
        ensureScriptClasspath();
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        FormulaValidationSupport uut = new FormulaValidationSupport(
            textController,
            (node, formulaText, outStream, errorHandler) -> 42);

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            org.freeplane.features.ai.code.AiChatCodeOperationResult result = uut.validateFormula(
                mock(NodeModel.class),
                "=21*2");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getOperationType()).isEqualTo("SUBMIT_VALIDATION");
            assertThat(result.getTrigger()).isEqualTo("USER");
            assertThat(result.getResult()).isEqualTo("42");
        }
    }

    @Test
    public void validateFormulaMapsFailureAndCapturedLineNumber() throws Exception {
        ensureScriptClasspath();
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        FormulaValidationSupport uut = new FormulaValidationSupport(
            textController,
            (node, formulaText, outStream, errorHandler) -> {
                errorHandler.gotoLine(7);
                throw new RuntimeException("Broken formula");
            });

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            org.freeplane.features.ai.code.AiChatCodeOperationResult result = uut.validateFormula(
                mock(NodeModel.class),
                "=broken"
            );

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getLineNumber()).isEqualTo(7);
            assertThat(result.getErrorMessage()).isEqualTo("Broken formula");
            assertThat(result.getCompilerDiagnostics()).contains("Broken formula");
        }
    }

    @Test
    public void validateFormulaReturnsCompilerDiagnosticsSummaryWithoutExecutingValidator() throws Exception {
        ensureScriptClasspath();
        TextController textController = mock(TextController.class);
        AtomicBoolean validatorCalled = new AtomicBoolean(false);
        FormulaValidationSupport uut = new FormulaValidationSupport(
            textController,
            (node, formulaText, outStream, errorHandler) -> {
                validatorCalled.set(true);
                return 1;
            });

        try (MockedStatic<Controller> controller = mockCurrentController()) {
            org.freeplane.features.ai.code.AiChatCodeOperationResult result = uut.validateFormula(
                mock(NodeModel.class),
                "=import a.A\nimport b.B\n1"
            );

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getLineNumber()).isEqualTo(1);
            assertThat(result.getErrorMessage()).isEqualTo("Groovy compilation failed with 2 diagnostics.");
            assertThat(result.getCompilerDiagnostics()).hasSize(2);
            assertThat(result.getCompilerDiagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("@ line 1, column 2"))
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("@ line 2, column 1"));
            assertThat(validatorCalled.get()).isFalse();
        }
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
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getBooleanProperty(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION))
            .thenReturn(false);
        controller.when(Controller::getCurrentController).thenReturn(currentController);
        return controller;
    }
}

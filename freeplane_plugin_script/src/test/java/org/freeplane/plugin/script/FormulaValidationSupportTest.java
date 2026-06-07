package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

public class FormulaValidationSupportTest {

    @Test
    public void validateFormulaMapsSuccessfulValidationResult() {
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        FormulaValidationSupport uut = new FormulaValidationSupport(
            textController,
            (node, formulaText, outStream, errorHandler) -> 42);

        org.freeplane.features.ai.code.AiChatCodeOperationResult result = uut.validateFormula(
            mock(NodeModel.class),
            "=21*2");

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getOperationType()).isEqualTo("SUBMIT_VALIDATION");
        assertThat(result.getTrigger()).isEqualTo("USER");
        assertThat(result.getResult()).isEqualTo("42");
    }

    @Test
    public void validateFormulaMapsFailureAndCapturedLineNumber() {
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        FormulaValidationSupport uut = new FormulaValidationSupport(
            textController,
            (node, formulaText, outStream, errorHandler) -> {
                errorHandler.gotoLine(7);
                throw new RuntimeException("Broken formula");
            });

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

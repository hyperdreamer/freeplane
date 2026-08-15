package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

public class GroovyScriptValidationTest {
    @Test
    public void validateCapturesOutputLineAndPolicyResult() {
        TextController textController = mock(TextController.class);
        when(textController.withNodeNumbering(eq(true), any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        NodeModel node = mock(NodeModel.class);
        GroovyScriptValidation.ResultPolicy resultPolicy = new GroovyScriptValidation.ResultPolicy() {
            @Override
            public boolean accepts(Object result) {
                return result instanceof Integer;
            }

            @Override
            public String resultText(Object result) {
                return String.valueOf(result);
            }

            @Override
            public String invalidResultMessage(Object result) {
                return "Expected an integer.";
            }
        };

        GroovyScriptValidation validation = new GroovyScriptValidation(
            textController,
            (validatedNode, sourceText, outStream, errorHandler) -> {
                outStream.print("output");
                return Integer.valueOf(42);
            },
            resultPolicy,
            false,
            GroovyScriptValidation::sha256Fingerprint);

        AiChatCodeOperationResult result = validation.validate(node, "source", null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getResult()).isEqualTo("42");
        assertThat(result.getStandardOutput()).isEqualTo("output");
        assertThat(result.getLineNumber()).isNull();
    }

    @Test
    public void validateUsesFailurePolicyForRejectedResult() {
        GroovyScriptValidation.ResultPolicy resultPolicy = new GroovyScriptValidation.ResultPolicy() {
            @Override
            public boolean accepts(Object result) {
                return false;
            }

            @Override
            public String resultText(Object result) {
                return null;
            }

            @Override
            public String invalidResultMessage(Object result) {
                return "Rejected result.";
            }
        };

        GroovyScriptValidation validation = new GroovyScriptValidation(
            null,
            (node, sourceText, outStream, errorHandler) -> {
                errorHandler.gotoLine(4);
                return "wrong";
            },
            resultPolicy,
            false,
            GroovyScriptValidation::sha256Fingerprint);

        AiChatCodeOperationResult result = validation.validate(mock(NodeModel.class), "source", null);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Rejected result.");
        assertThat(result.getCompilerDiagnostics()).containsExactly("Rejected result.");
        assertThat(result.getLineNumber()).isEqualTo(4);
    }
}

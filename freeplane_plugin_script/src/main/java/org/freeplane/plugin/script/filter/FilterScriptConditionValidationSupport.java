package org.freeplane.plugin.script.filter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.GroovyCompilerDiagnosticsMapper;
import org.freeplane.plugin.script.FormulaThreadLocalStacks;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.NodeScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptRunner;
import org.freeplane.plugin.script.ScriptingEngine;
import org.freeplane.plugin.script.ScriptingPermissions;

public class FilterScriptConditionValidationSupport {
    public static final String FORMULA_CONDITION_CONTENT_TYPE = "text/x-freeplane-formula-condition-groovy";

    interface ScriptValidator {
        Object validate(NodeModel node, String script, PrintStream outStream, IFreeplaneScriptErrorHandler errorHandler);
    }

    private final TextController textController;
    private final ScriptValidator scriptValidator;

    public FilterScriptConditionValidationSupport() {
        this(TextController.getController(), new ScriptValidator() {
            @Override
            public Object validate(NodeModel node, String script, PrintStream outStream,
                                   IFreeplaneScriptErrorHandler errorHandler) {
                return FormulaUtils.validateScript(
                    node,
                    script,
                    outStream,
                    errorHandler);
            }
        });
    }

    FilterScriptConditionValidationSupport(TextController textController, ScriptValidator scriptValidator) {
        this.textController = textController;
        this.scriptValidator = scriptValidator;
    }

    public CompileCodeResponse compile(String scriptText) {
        String sourceText = normalizeSourceText(scriptText);
        CodeStateContent content = new CodeStateContent(sourceText, null);
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            sourceText,
            ScriptingPermissions.getFormulaPermissions());
        return new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            FORMULA_CONDITION_CONTENT_TYPE,
            compileResult.isSuccessful() ? CodeState.RUNNABLE : CodeState.INVALID_SCRIPT,
            stateToken,
            GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(compileResult.getCompilerDiagnostics()),
            compileResult.getErrorMessage());
    }

    public AiChatCodeOperationResult validate(NodeModel node, String scriptText) {
        String sourceText = normalizeSourceText(scriptText);
        String sourceFingerprint = CodeStateToken.fingerprint(sourceText);
        CompileCodeResponse compileResponse = compile(sourceText);
        if (compileResponse.getCodeState() != CodeState.RUNNABLE) {
            return new AiChatCodeOperationResult(
                "SUBMIT_VALIDATION",
                "USER",
                false,
                compilerDiagnosticMessages(compileResponse),
                null,
                null,
                "validation",
                compileResponse.getErrorMessage(),
                firstDiagnosticLine(compileResponse),
                sourceFingerprint);
        }
        if (node == null) {
            return failure(
                Collections.singletonList("A selected node is required to validate a filter condition."),
                null,
                "A selected node is required to validate a filter condition.",
                null,
                sourceFingerprint);
        }

        final int[] lineNumber = new int[] { -1 };
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (PrintStream outStream = new PrintStream(outputBuffer, false, "UTF-8")) {
            Supplier<Object> validation = () -> scriptValidator.validate(
                node,
                sourceText,
                outStream,
                new IFreeplaneScriptErrorHandler() {
                    @Override
                    public void gotoLine(int pLineNumber) {
                        lineNumber[0] = pLineNumber;
                    }
                });
            Object result = textController == null
                ? validation.get()
                : textController.withNodeNumbering(true, validation);
            String standardOutput = new String(outputBuffer.toByteArray(), "UTF-8");
            if (isConditionResult(result)) {
                return new AiChatCodeOperationResult(
                    "SUBMIT_VALIDATION",
                    "USER",
                    true,
                    Collections.<String>emptyList(),
                    standardOutput.isEmpty() ? null : standardOutput,
                    String.valueOf(result),
                    null,
                    null,
                    null,
                    sourceFingerprint);
            }
            String errorMessage = invalidResultMessage(result);
            return failure(
                Collections.singletonList(errorMessage),
                standardOutput.isEmpty() ? null : standardOutput,
                errorMessage,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                sourceFingerprint);
        } catch (UnsupportedEncodingException error) {
            throw new IllegalStateException("UTF-8 is not available.", error);
        } catch (Exception error) {
            String standardOutput;
            try {
                standardOutput = new String(outputBuffer.toByteArray(), "UTF-8").trim();
            } catch (UnsupportedEncodingException encodingError) {
                throw new IllegalStateException("UTF-8 is not available.", encodingError);
            }
            String errorMessage = error.getMessage();
            return failure(
                standardOutput.isEmpty() && errorMessage != null
                    ? Collections.singletonList(errorMessage)
                    : standardOutput.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(standardOutput),
                standardOutput.isEmpty() ? null : standardOutput,
                errorMessage,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                sourceFingerprint);
        }
    }

    static ConditionExecutionResult executeCondition(NodeModel node, String source, ScriptRunner scriptRunner) {
        ScriptContext scriptContext = new ScriptContext(new NodeScript(node, source));
        if (!FormulaThreadLocalStacks.INSTANCE.push(scriptContext)) {
            return ConditionExecutionResult.cycleDetected();
        }
        scriptRunner.setScriptContext(scriptContext);
        try {
            return ConditionExecutionResult.value(
                FormulaUtils.executeScript(scriptContext, () -> scriptRunner.execute(node)));
        } finally {
            FormulaThreadLocalStacks.INSTANCE.pop();
            scriptRunner.setScriptContext(null);
        }
    }

    static final class ConditionExecutionResult {
        private final boolean cycleDetected;
        private final Object value;

        private ConditionExecutionResult(boolean cycleDetected, Object value) {
            this.cycleDetected = cycleDetected;
            this.value = value;
        }

        static ConditionExecutionResult cycleDetected() {
            return new ConditionExecutionResult(true, null);
        }

        static ConditionExecutionResult value(Object value) {
            return new ConditionExecutionResult(false, value);
        }

        boolean isCycleDetected() {
            return cycleDetected;
        }

        Object getValue() {
            return value;
        }
    }

    public static boolean isConditionResult(Object result) {
        return result instanceof Boolean || result instanceof Number;
    }

    public static boolean conditionResultAsBoolean(Object result) {
        if (result instanceof Boolean) {
            return ((Boolean) result).booleanValue();
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        throw new IllegalArgumentException(invalidResultMessage(result));
    }

    public static String invalidResultMessage(Object result) {
        return "Filter condition must return Boolean or Number, but returned " + String.valueOf(result) + ".";
    }

    private AiChatCodeOperationResult failure(List<String> diagnostics,
                                              String standardOutput,
                                              String errorMessage,
                                              Integer lineNumber,
                                              String sourceFingerprint) {
        return new AiChatCodeOperationResult(
            "SUBMIT_VALIDATION",
            "USER",
            false,
            diagnostics,
            standardOutput,
            null,
            "validation",
            errorMessage,
            lineNumber,
            sourceFingerprint);
    }

    private List<String> compilerDiagnosticMessages(CompileCodeResponse compileResponse) {
        if (compileResponse == null || compileResponse.getDiagnostics() == null
            || compileResponse.getDiagnostics().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new ArrayList<String>();
        for (CodeStateDiagnostic diagnostic : compileResponse.getDiagnostics()) {
            if (diagnostic != null && diagnostic.getMessage() != null && !diagnostic.getMessage().trim().isEmpty()) {
                messages.add(diagnostic.getMessage());
            }
        }
        return messages.isEmpty() ? Collections.<String>emptyList() : Collections.unmodifiableList(messages);
    }

    private Integer firstDiagnosticLine(CompileCodeResponse compileResponse) {
        if (compileResponse == null || compileResponse.getDiagnostics() == null) {
            return null;
        }
        for (CodeStateDiagnostic diagnostic : compileResponse.getDiagnostics()) {
            if (diagnostic != null && diagnostic.getLine() != null) {
                return diagnostic.getLine();
            }
        }
        return null;
    }

    private String normalizeSourceText(String scriptText) {
        return scriptText == null ? "" : scriptText;
    }
}

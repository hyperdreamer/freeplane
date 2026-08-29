package org.freeplane.plugin.script;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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

public class GroovyScriptValidation {
    public interface ScriptValidator {
        Object validate(NodeModel node, String sourceText, PrintStream outStream,
                        IFreeplaneScriptErrorHandler errorHandler);
    }

    public interface ResultPolicy {
        boolean accepts(Object result);

        String resultText(Object result);

        String invalidResultMessage(Object result);
    }

    private final TextController textController;
    private final ScriptValidator scriptValidator;
    private final ResultPolicy resultPolicy;
    private final boolean nodeRequired;
    private final Function<String, String> sourceFingerprint;

    public GroovyScriptValidation(TextController textController,
                                  ScriptValidator scriptValidator,
                                  ResultPolicy resultPolicy,
                                  boolean nodeRequired,
                                  Function<String, String> sourceFingerprint) {
        this.textController = textController;
        this.scriptValidator = Objects.requireNonNull(scriptValidator, "scriptValidator");
        this.resultPolicy = Objects.requireNonNull(resultPolicy, "resultPolicy");
        this.nodeRequired = nodeRequired;
        this.sourceFingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
    }

    public CompileCodeResponse compile(String sourceText, String contentType) {
        String normalizedSourceText = normalizeSourceText(sourceText);
        CodeStateContent content = new CodeStateContent(normalizedSourceText, null);
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ScriptingEngine.GroovyCompileResult compileResult = compileResult(normalizedSourceText);
        return new CompileCodeResponse(
            ScriptHost.ATTACHED_EDITOR,
            Objects.requireNonNull(contentType, "contentType"),
            compileResult.isSuccessful() ? CodeState.RUNNABLE : CodeState.INVALID_SCRIPT,
            stateToken,
            GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(compileResult.getCompilerDiagnostics()),
            compileResult.getErrorMessage());
    }

    public AiChatCodeOperationResult validate(NodeModel node,
                                               String sourceText,
                                               String compilationSource) {
        String normalizedSourceText = normalizeSourceText(sourceText);
        String fingerprint = sourceFingerprint.apply(normalizedSourceText);
        if (compilationSource != null) {
            ScriptingEngine.GroovyCompileResult compileResult = compileResult(compilationSource);
            if (!compileResult.isSuccessful()) {
                return failure(
                    compilerDiagnosticMessages(compileResult),
                    null,
                    compileResult.getErrorMessage(),
                    firstDiagnosticLine(compileResult),
                    fingerprint);
            }
        }
        if (nodeRequired && node == null) {
            String errorMessage = "A selected node is required to validate a filter condition.";
            return failure(
                Collections.singletonList(errorMessage),
                null,
                errorMessage,
                null,
                fingerprint);
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
            String standardOutput = new String(outputBuffer.toByteArray(), StandardCharsets.UTF_8);
            if (resultPolicy.accepts(result)) {
                return new AiChatCodeOperationResult(
                    "SUBMIT_VALIDATION",
                    "USER",
                    true,
                    Collections.<String>emptyList(),
                    standardOutput.isEmpty() ? null : standardOutput,
                    resultPolicy.resultText(result),
                    null,
                    null,
                    null,
                    fingerprint);
            }
            String errorMessage = resultPolicy.invalidResultMessage(result);
            return failure(
                Collections.singletonList(errorMessage),
                standardOutput.isEmpty() ? null : standardOutput,
                errorMessage,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                fingerprint);
        } catch (Exception error) {
            String standardOutput = new String(outputBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
            String errorMessage = error.getMessage();
            return failure(
                standardOutput.isEmpty() && errorMessage != null
                    ? Collections.singletonList(errorMessage)
                    : standardOutput.isEmpty()
                        ? Collections.<String>emptyList()
                        : Collections.singletonList(standardOutput),
                standardOutput.isEmpty() ? null : standardOutput,
                errorMessage,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                fingerprint);
        }
    }

    public static String sha256Fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }

    private ScriptingEngine.GroovyCompileResult compileResult(String sourceText) {
        return ScriptingEngine.compileGroovyScriptForDiagnostics(
            sourceText,
            ScriptingPermissions.getFormulaPermissions());
    }

    private String normalizeSourceText(String sourceText) {
        return sourceText == null ? "" : sourceText;
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

    private List<String> compilerDiagnosticMessages(ScriptingEngine.GroovyCompileResult compileResult) {
        if (compileResult == null || compileResult.getCompilerDiagnostics().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new ArrayList<String>();
        for (ScriptingEngine.GroovyCompilerDiagnostic diagnostic : compileResult.getCompilerDiagnostics()) {
            if (diagnostic != null && diagnostic.getMessage() != null && !diagnostic.getMessage().trim().isEmpty()) {
                messages.add(diagnostic.getMessage());
            }
        }
        return messages.isEmpty() ? Collections.<String>emptyList() : Collections.unmodifiableList(messages);
    }

    private Integer firstDiagnosticLine(ScriptingEngine.GroovyCompileResult compileResult) {
        if (compileResult == null) {
            return null;
        }
        for (ScriptingEngine.GroovyCompilerDiagnostic diagnostic : compileResult.getCompilerDiagnostics()) {
            if (diagnostic != null && diagnostic.getLine() != null) {
                return diagnostic.getLine();
            }
        }
        return null;
    }
}

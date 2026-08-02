package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnosticTextFormatter;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeResponse;

public class AutomaticCodeStatusMessage extends UserMessage {
    private static final String PREFIX = "Automatic app-authored code-status message:";

    public AutomaticCodeStatusMessage(String text) {
        super(text == null ? PREFIX : text);
    }

    public static AutomaticCodeStatusMessage forRunResponse(RunCodeResponse response) {
        return new AutomaticCodeStatusMessage(formatRunResponse(response));
    }

    public static AutomaticCodeStatusMessage forCodeState(ReadCodeResponse response) {
        return new AutomaticCodeStatusMessage(formatCodeState(response));
    }

    public static boolean isAutomaticCodeStatusText(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    static String formatRunResponse(RunCodeResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append(PREFIX);
        if (response == null) {
            return builder.toString();
        }
        builder.append('\n');
        appendCommonFields(builder,
            response.getHost(),
            response.getContentType(),
            response.getCodeState(),
            response.getRunInitiator(),
            response.getStateToken() == null ? null : response.getStateToken().getCodeFingerprint(),
            response.getStateToken() == null ? null : response.getStateToken().getArgumentsFingerprint(),
            response.getDiagnostics(),
            response.getErrorMessage(),
            response.getStdout(),
            response.getStructuredResult());
        return builder.toString();
    }

    static String formatCodeState(ReadCodeResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append(PREFIX);
        if (response == null) {
            return builder.toString();
        }
        builder.append('\n');
        appendCommonFields(builder,
            response.getHost(),
            response.getContentType(),
            response.getCodeState(),
            response.getRunInitiator(),
            response.getStateToken() == null ? null : response.getStateToken().getCodeFingerprint(),
            response.getStateToken() == null ? null : response.getStateToken().getArgumentsFingerprint(),
            response.getDiagnostics(),
            response.getErrorMessage(),
            response.getStdout(),
            response.getStructuredResult());
        return builder.toString();
    }

    private static void appendCommonFields(StringBuilder builder,
                                           Object host,
                                           Object contentType,
                                           Object codeState,
                                           Object runInitiator,
                                           Object codeFingerprint,
                                           Object argumentsFingerprint,
                                           List<CodeStateDiagnostic> diagnostics,
                                           String errorMessage,
                                           String stdout,
                                           Object structuredResult) {
        append(builder, "host", host);
        append(builder, "contentType", contentType);
        append(builder, "codeState", codeState);
        append(builder, "runInitiator", runInitiator);
        append(builder, "codeFingerprint", codeFingerprint);
        append(builder, "argumentsFingerprint", argumentsFingerprint);
        appendDiagnostics(builder, diagnostics);
        if (shouldAppendErrorMessage(diagnostics, errorMessage)) {
            append(builder, "errorMessage", errorMessage);
        }
        appendBlock(builder, "stdout", stdout);
        append(builder, "structuredResult", structuredResult);
    }

    private static void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        builder.append(key).append('=').append(value).append('\n');
    }

    private static void appendDiagnostics(StringBuilder builder, List<CodeStateDiagnostic> diagnostics) {
        String formattedDiagnostics = CodeStateDiagnosticTextFormatter.format(diagnostics);
        if (formattedDiagnostics == null) {
            return;
        }
        builder.append("diagnostics:").append('\n');
        builder.append(formattedDiagnostics).append('\n');
    }

    private static boolean shouldAppendErrorMessage(List<CodeStateDiagnostic> diagnostics, String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        if (diagnostics == null || diagnostics.isEmpty()) {
            return true;
        }
        for (CodeStateDiagnostic diagnostic : diagnostics) {
            if (diagnostic == null || diagnostic.getMessage() == null || diagnostic.getMessage().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void appendBlock(StringBuilder builder, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        builder.append(key).append('=').append('\n');
        builder.append(value).append('\n');
    }
}

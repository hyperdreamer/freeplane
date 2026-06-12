package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.RunCodeResponse;

public class AutomaticCodeStatusMessage extends UserMessage {
    private static final String PREFIX = "Automatic app-authored code-status message:";

    public AutomaticCodeStatusMessage(String text) {
        super(text == null ? PREFIX : text);
    }

    public static AutomaticCodeStatusMessage forRunResponse(RunCodeResponse response) {
        return new AutomaticCodeStatusMessage(formatRunResponse(response));
    }

    static String formatRunResponse(RunCodeResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append(PREFIX);
        if (response == null) {
            return builder.toString();
        }
        builder.append('\n');
        append(builder, "host", response.getHost());
        append(builder, "contentType", response.getContentType());
        append(builder, "codeState", response.getCodeState());
        append(builder, "runInitiator", response.getRunInitiator());
        append(builder, "codeFingerprint", response.getStateToken() == null ? null : response.getStateToken().getCodeFingerprint());
        append(builder, "argumentsFingerprint", response.getStateToken() == null ? null : response.getStateToken().getArgumentsFingerprint());
        appendDiagnostics(builder, response.getDiagnostics());
        append(builder, "errorMessage", response.getErrorMessage());
        appendBlock(builder, "stdout", response.getStdout());
        append(builder, "structuredResult", response.getStructuredResult());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        builder.append(key).append('=').append(value).append('\n');
    }

    private static void appendDiagnostics(StringBuilder builder, List<CodeStateDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        builder.append("diagnostics:").append('\n');
        for (CodeStateDiagnostic diagnostic : diagnostics) {
            builder.append("- ").append(diagnostic.getField()).append(": ").append(diagnostic.getMessage());
            if (diagnostic.getLine() != null) {
                builder.append(" (line ").append(diagnostic.getLine());
                if (diagnostic.getColumn() != null) {
                    builder.append(", column ").append(diagnostic.getColumn());
                }
                builder.append(')');
            }
            builder.append('\n');
        }
    }

    private static void appendBlock(StringBuilder builder, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        builder.append(key).append('=').append('\n');
        builder.append(value).append('\n');
    }
}

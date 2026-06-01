package org.freeplane.plugin.ai.chat;

import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.freeplane.features.ai.code.RunScriptResponse;

public class AutomaticCodeStatusMessage extends UserMessage {
    private static final String PREFIX = "Automatic app-authored code-status message:";

    public AutomaticCodeStatusMessage(String text) {
        super(text == null ? PREFIX : text);
    }

    public static AutomaticCodeStatusMessage forRunResponse(RunScriptResponse response) {
        return new AutomaticCodeStatusMessage(formatRunResponse(response));
    }

    static String formatRunResponse(RunScriptResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append(PREFIX);
        if (response == null) {
            return builder.toString();
        }
        builder.append('\n');
        append(builder, "codeId", response.getCodeId());
        append(builder, "host", response.getHost());
        append(builder, "contentType", response.getContentType());
        append(builder, "status", response.getStatus());
        append(builder, "runInitiator", response.getRunInitiator());
        append(builder, "fingerprint", response.getFingerprint());
        appendList(builder, "compilerDiagnostics", response.getCompilerDiagnostics());
        append(builder, "errorMessage", response.getErrorMessage());
        append(builder, "lineNumber", response.getLineNumber());
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

    private static void appendList(StringBuilder builder, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(key).append(':').append('\n');
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
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

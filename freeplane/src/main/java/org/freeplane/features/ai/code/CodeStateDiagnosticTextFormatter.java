package org.freeplane.features.ai.code;

import java.util.List;

public class CodeStateDiagnosticTextFormatter {
    private CodeStateDiagnosticTextFormatter() {
    }

    public static String format(List<CodeStateDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (CodeStateDiagnostic diagnostic : diagnostics) {
            String formattedDiagnostic = formatDiagnostic(diagnostic);
            if (formattedDiagnostic == null || formattedDiagnostic.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(formattedDiagnostic);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static String formatDiagnostic(CodeStateDiagnostic diagnostic) {
        if (diagnostic == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("- ");
        boolean hasHeader = false;
        if (diagnostic.getField() != null) {
            builder.append(diagnostic.getField());
            hasHeader = true;
        }
        if (diagnostic.getLine() != null) {
            if (hasHeader) {
                builder.append(' ');
            }
            builder.append("(line ").append(diagnostic.getLine());
            if (diagnostic.getColumn() != null) {
                builder.append(", column ").append(diagnostic.getColumn());
            }
            builder.append(')');
            hasHeader = true;
        }
        String message = trimToNull(diagnostic.getMessage());
        if (message != null) {
            if (hasHeader) {
                builder.append(": ");
            }
            builder.append(message);
        }
        else if (!hasHeader) {
            return null;
        }
        return builder.toString();
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

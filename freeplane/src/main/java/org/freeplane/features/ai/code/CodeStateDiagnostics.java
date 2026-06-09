package org.freeplane.features.ai.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeStateDiagnostics {
    private CodeStateDiagnostics() {
    }

    public static List<CodeStateDiagnostic> sourceDiagnostics(List<String> messages, Integer line) {
        return diagnostics(CodeStateField.SOURCE_TEXT, messages, line, null);
    }

    public static List<CodeStateDiagnostic> diagnostics(CodeStateField field,
                                                        List<String> messages,
                                                        Integer line,
                                                        Integer column) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        List<CodeStateDiagnostic> diagnostics = new ArrayList<CodeStateDiagnostic>();
        boolean first = true;
        for (String message : messages) {
            diagnostics.add(new CodeStateDiagnostic(field, message, first ? line : null, first ? column : null));
            first = false;
        }
        return Collections.unmodifiableList(diagnostics);
    }

    public static List<CodeStateDiagnostic> singleton(CodeStateField field,
                                                      String message,
                                                      Integer line,
                                                      Integer column) {
        if (message == null) {
            return null;
        }
        return Collections.singletonList(new CodeStateDiagnostic(field, message, line, column));
    }

    public static String primaryMessage(List<CodeStateDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return null;
        }
        CodeStateDiagnostic diagnostic = diagnostics.get(0);
        return diagnostic == null ? null : diagnostic.getMessage();
    }
}

package org.freeplane.plugin.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;

public class GroovyCompilerDiagnosticsMapper {
    private GroovyCompilerDiagnosticsMapper() {
    }

    public static List<CodeStateDiagnostic> toSourceDiagnostics(
        List<ScriptingEngine.GroovyCompilerDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return null;
        }
        List<CodeStateDiagnostic> mappedDiagnostics = new ArrayList<CodeStateDiagnostic>();
        for (ScriptingEngine.GroovyCompilerDiagnostic diagnostic : diagnostics) {
            if (diagnostic == null) {
                continue;
            }
            mappedDiagnostics.add(new CodeStateDiagnostic(
                CodeStateField.SOURCE_TEXT,
                diagnostic.getMessage(),
                diagnostic.getLine(),
                diagnostic.getColumn()));
        }
        return mappedDiagnostics.isEmpty()
            ? null
            : Collections.unmodifiableList(mappedDiagnostics);
    }
}

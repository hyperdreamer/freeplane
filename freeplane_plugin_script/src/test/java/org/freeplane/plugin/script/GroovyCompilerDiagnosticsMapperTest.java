package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.junit.Test;

public class GroovyCompilerDiagnosticsMapperTest {
    @Test
    public void toSourceDiagnosticsPreservesNullLocations() {
        List<CodeStateDiagnostic> diagnostics = GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(
            Collections.singletonList(new ScriptingEngine.GroovyCompilerDiagnostic("Broken", null, null)));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).getLine()).isNull();
        assertThat(diagnostics.get(0).getColumn()).isNull();
    }
}

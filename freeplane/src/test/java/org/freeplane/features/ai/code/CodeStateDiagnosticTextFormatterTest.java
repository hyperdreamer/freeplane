package org.freeplane.features.ai.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Test;

public class CodeStateDiagnosticTextFormatterTest {
    @Test
    public void formatIncludesLineAndColumnWhenPresent() {
        String formatted = CodeStateDiagnosticTextFormatter.format(Collections.singletonList(
            new CodeStateDiagnostic(CodeStateField.SOURCE_TEXT, "Broken", 4, 9)));

        assertThat(formatted).isEqualTo("- SOURCE_TEXT (line 4, column 9): Broken");
    }

    @Test
    public void formatOmitsMissingLocations() {
        String formatted = CodeStateDiagnosticTextFormatter.format(Collections.singletonList(
            new CodeStateDiagnostic(CodeStateField.SOURCE_TEXT, "Broken", null, null)));

        assertThat(formatted).isEqualTo("- SOURCE_TEXT: Broken");
    }
}

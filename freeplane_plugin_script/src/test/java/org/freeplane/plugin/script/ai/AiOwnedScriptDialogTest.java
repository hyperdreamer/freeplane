package org.freeplane.plugin.script.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.junit.Test;

public class AiOwnedScriptDialogTest {
    @Test
    public void resultTextPreservesStdoutAlongsideFailureDiagnostics() {
        String result = AiOwnedScriptDialog.resultText(response(
            CodeState.RUN_FAILED,
            Collections.singletonList(new CodeStateDiagnostic(
                CodeStateField.SOURCE_TEXT,
                "Failure detail",
                3,
                4)),
            "Run failed.",
            "Printed before failure",
            null));

        assertThat(result).contains("Printed before failure");
        assertThat(result).contains("- SOURCE_TEXT (line 3, column 4): Failure detail");
        assertThat(result).contains("Run failed.");
    }

    @Test
    public void resultTextPreservesStdoutAlongsideStructuredResult() {
        String result = AiOwnedScriptDialog.resultText(response(
            CodeState.RUN_SUCCEEDED,
            null,
            null,
            "Printed output",
            "Returned value"));

        assertThat(result).isEqualTo("Printed output\n\nReturned value");
    }

    @Test
    public void resultTextOmitsDuplicateErrorMessageWhenDiagnosticsContainIt() {
        String result = AiOwnedScriptDialog.resultText(response(
            CodeState.RUN_FAILED,
            Collections.singletonList(new CodeStateDiagnostic(
                CodeStateField.SOURCE_TEXT,
                "Failure detail",
                null,
                null)),
            "Failure detail",
            null,
            null));

        assertThat(result).isEqualTo("- SOURCE_TEXT: Failure detail");
    }

    private ReadCodeResponse response(CodeState codeState,
                                      List<CodeStateDiagnostic> diagnostics,
                                      String errorMessage,
                                      String stdout,
                                      Object structuredResult) {
        return new ReadCodeResponse(
            ScriptHost.AI,
            AiOwnedScriptHostService.AI_SCRIPT_CONTENT_TYPE,
            codeState,
            ScriptRunInitiator.USER,
            null,
            new CodeStateContent("println 'test'", null),
            diagnostics,
            errorMessage,
            stdout,
            structuredResult);
    }
}

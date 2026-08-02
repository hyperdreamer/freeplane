package org.freeplane.plugin.ai.chat.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.junit.Test;

public class AutomaticCodeStatusMessageTest {
    @Test
    public void formatRunResponseDoesNotRepeatCompilerDetailWhenDiagnosticsExist() {
        RunCodeResponse response = new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.INVALID_SCRIPT,
            ScriptRunInitiator.USER,
            new CodeStateToken("code", "args"),
            Collections.singletonList(new CodeStateDiagnostic(CodeStateField.SOURCE_TEXT, "Broken", 4, 9)),
            "Groovy compilation failed with 1 diagnostic.",
            null,
            null);

        String text = AutomaticCodeStatusMessage.formatRunResponse(response);

        assertThat(text).contains("diagnostics:\n- SOURCE_TEXT (line 4, column 9): Broken");
        assertThat(text).doesNotContain("errorMessage=");
    }
}

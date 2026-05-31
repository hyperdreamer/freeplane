package org.freeplane.plugin.script.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.junit.Test;

public class AiOwnedScriptHostServiceTest {
    @Test
    public void defaultConstructorDoesNotRequireCurrentController() {
        assertThatCode(() -> new AiOwnedScriptHostService()).doesNotThrowAnyException();
    }

    @Test
    public void writeCodeReplacesPreviousScriptAndArchivesReplacedState() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);

        WriteCodeResponse first = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 1", null));
        WriteCodeResponse second = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 2", null));
        ReadCodeResponse current = uut.readCode(new ReadCodeRequest(null, ScriptHost.AI, null));
        ReadCodeResponse replaced = uut.readCode(new ReadCodeRequest(first.getCodeId(), null, null));

        assertThat(first.getCodeId()).isEqualTo("ai-script-1");
        assertThat(second.getCodeId()).isEqualTo("ai-script-2");
        assertThat(current.getCodeId()).isEqualTo(second.getCodeId());
        assertThat(current.getStatus()).isEqualTo(CodeLifecycleStatus.READY);
        assertThat(current.getCodeText()).isEqualTo("println 2");
        assertThat(replaced.getStatus()).isEqualTo(CodeLifecycleStatus.REPLACED);
        assertThat(replaced.getReplacementCodeId()).isEqualTo(second.getCodeId());
        assertThat(replaced.getCodeText()).isNull();
    }

    @Test
    public void writeCodeRejectsMismatchedExpectedFingerprint() {
        AiOwnedScriptHostService uut = new AiOwnedScriptHostService(null);
        WriteCodeResponse written = uut.writeCode(new WriteCodeRequest(null, ScriptHost.AI, "println 1", null));

        assertThatThrownBy(() -> uut.writeCode(new WriteCodeRequest(
            written.getCodeId(),
            ScriptHost.AI,
            "println 2",
            "wrong")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Expected fingerprint does not match the current code.");
    }
}

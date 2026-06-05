package org.freeplane.plugin.ai.tools.code;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AiCodeOperationAuthorizerTest {
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";

    @Test
    public void attachedEditorOverrideKeepsReadWriteAndCompileAuthorizedAtReading() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeLifecycleStatus.READY);
        AiCodeOperationAuthorizer uut = new AiCodeOperationAuthorizer(
            ToolCaller.CHAT,
            () -> ToolAvailabilityLevel.READING,
            () -> ToolAvailabilityLevel.READING,
            codeHostService);

        Set<String> authorizedToolNames = uut.authorizedToolNames();

        assertThat(authorizedToolNames).containsExactly("readCode", "writeCode", "compileCode");
        uut.assertAuthorized("readCode", null, ScriptHost.ATTACHED_EDITOR);
        uut.assertAuthorized("writeCode", null, ScriptHost.ATTACHED_EDITOR);
        uut.assertAuthorized("compileCode", null, ScriptHost.ATTACHED_EDITOR);
    }

    @Test
    public void scriptExecutionAvailabilityAddsRunScriptForScriptContent() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeLifecycleStatus.READY);
        AiCodeOperationAuthorizer uut = new AiCodeOperationAuthorizer(
            ToolCaller.CHAT,
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION,
            () -> null,
            codeHostService);

        assertThat(uut.authorizedToolNames()).contains("runScript");
        uut.assertAuthorized("runScript", null, ScriptHost.ATTACHED_EDITOR);
    }

    @Test
    public void runScriptRejectsNonScriptContent() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, FORMULA_CONTENT_TYPE, CodeLifecycleStatus.READY);
        AiCodeOperationAuthorizer uut = new AiCodeOperationAuthorizer(
            ToolCaller.CHAT,
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION,
            () -> null,
            codeHostService);

        assertThatThrownBy(() -> uut.assertAuthorized("runScript", null, ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only script content is runnable.");
    }

    @Test
    public void disabledAvailabilityWithoutOverrideRejectsAttachedEditorRead() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeLifecycleStatus.READY);
        AiCodeOperationAuthorizer uut = new AiCodeOperationAuthorizer(
            ToolCaller.CHAT,
            () -> ToolAvailabilityLevel.DISABLED,
            () -> null,
            codeHostService);

        assertThat(uut.authorizedToolNames()).isEmpty();
        assertThatThrownBy(() -> uut.assertAuthorized("readCode", null, ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested code host is not readable at the current availability level.");
    }

    private static class FakeCodeHostService implements AiCodeHostService {
        private final Map<ScriptHost, ReadCodeResponse> states = new EnumMap<ScriptHost, ReadCodeResponse>(ScriptHost.class);

        private FakeCodeHostService withState(ScriptHost host, String contentType, CodeLifecycleStatus status) {
            states.put(host, new ReadCodeResponse(
                host == ScriptHost.AI ? "ai-script-1" : "attached-editor-1",
                host,
                contentType,
                status,
                null,
                "fingerprint",
                "code",
                null,
                null,
                null,
                null,
                null,
                null));
            return this;
        }

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            ScriptHost host = request == null ? null : request.getHost();
            if (host == null && request != null && request.getCodeId() != null) {
                host = request.getCodeId().startsWith("ai-script-") ? ScriptHost.AI : ScriptHost.ATTACHED_EDITOR;
            }
            ReadCodeResponse state = states.get(host);
            return state != null
                ? state
                : new ReadCodeResponse(
                    null,
                    host,
                    null,
                    CodeLifecycleStatus.NO_CODE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        @Override
        public WriteCodeResponse writeCode(WriteCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunScriptResponse runScript(RunScriptRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRunListener(AiCodeRunListener listener) {
        }

        @Override
        public void removeRunListener(AiCodeRunListener listener) {
        }
    }
}

package org.freeplane.plugin.ai.tools.code;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
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
    public void attachedScriptEditorOverrideKeepsReadWriteAndCompileAuthorizedAtReading() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.READING,
            () -> ToolAvailabilityLevel.READING,
            false,
            codeHostService);

        Set<String> authorizedToolNames = uut.authorizedToolNames();

        assertThat(authorizedToolNames).containsExactly("readCode", "writeCode", "compileCode");
        uut.assertAuthorized("readCode", ScriptHost.ATTACHED_EDITOR);
        uut.assertAuthorized("writeCode", ScriptHost.ATTACHED_EDITOR);
        uut.assertAuthorized("compileCode", ScriptHost.ATTACHED_EDITOR);
    }

    @Test
    public void attachedFormulaWriteAndCompileRequireFormulaEditingPermission() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, FORMULA_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.EDITING,
            () -> null,
            false,
            codeHostService);

        assertThat(uut.authorizedToolNames()).containsExactly("readCode");
        assertThatThrownBy(() -> uut.assertAuthorized("writeCode", ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested code host is not writable at the current availability level.");
        assertThatThrownBy(() -> uut.assertAuthorized("compileCode", ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested code host is not writable at the current availability level.");
    }

    @Test
    public void scriptExecutionAvailabilityAddsRunCodeForScriptContent() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION,
            () -> null,
            false,
            codeHostService);

        assertThat(uut.authorizedToolNames()).contains("runCode");
        uut.assertAuthorized("runCode", ScriptHost.ATTACHED_EDITOR);
    }

    @Test
    public void scriptExecutionAvailabilityExposesAllCodeToolsWithoutCurrentCode() {
        FakeCodeHostService codeHostService = new FakeCodeHostService();
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION,
            () -> null,
            false,
            codeHostService);

        assertThat(uut.authorizedToolNames()).containsExactly("readCode", "writeCode", "compileCode", "runCode");
    }

    @Test
    public void editingAvailabilityAllowsAttachedFormulaWriteAndCompileWhenFormulaEditingIsEnabled() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, FORMULA_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.EDITING,
            () -> null,
            true,
            codeHostService);

        assertThat(uut.authorizedToolNames()).contains("readCode", "writeCode", "compileCode");
        uut.assertAuthorized("writeCode", ScriptHost.ATTACHED_EDITOR);
        uut.assertAuthorized("compileCode", ScriptHost.ATTACHED_EDITOR);
    }

    @Test
    public void runCodeRejectsNonScriptContent() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, FORMULA_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.SCRIPT_EXECUTION,
            () -> null,
            false,
            codeHostService);

        assertThatThrownBy(() -> uut.assertAuthorized("runCode", ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only script content is runnable.");
    }

    @Test
    public void disabledAvailabilityWithoutOverrideRejectsAttachedEditorRead() {
        FakeCodeHostService codeHostService = new FakeCodeHostService()
            .withState(ScriptHost.ATTACHED_EDITOR, SCRIPT_CONTENT_TYPE, CodeState.EDITED);
        AiCodeOperationAuthorizer uut = authorizer(
            () -> ToolAvailabilityLevel.DISABLED,
            () -> null,
            false,
            codeHostService);

        assertThat(uut.authorizedToolNames()).isEmpty();
        assertThatThrownBy(() -> uut.assertAuthorized("readCode", ScriptHost.ATTACHED_EDITOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested code host is not readable at the current availability level.");
    }

    private AiCodeOperationAuthorizer authorizer(java.util.function.Supplier<ToolAvailabilityLevel> globalAvailability,
                                                 java.util.function.Supplier<ToolAvailabilityLevel> sessionOverride,
                                                 boolean formulaEditingEnabled,
                                                 FakeCodeHostService codeHostService) {
        return new AiCodeOperationAuthorizer(
            ToolCaller.CHAT,
            globalAvailability,
            sessionOverride,
            () -> Boolean.valueOf(formulaEditingEnabled),
            codeHostService);
    }

    private static class FakeCodeHostService implements AiCodeHostService {
        private final Map<ScriptHost, ReadCodeResponse> states = new EnumMap<ScriptHost, ReadCodeResponse>(ScriptHost.class);

        private FakeCodeHostService withState(ScriptHost host, String contentType, CodeState status) {
            states.put(host, new ReadCodeResponse(
                host,
                contentType,
                status,
                null,
                new CodeStateToken("code", "fingerprint"),
                new CodeStateContent("code", null),
                null,
                null,
                null,
                null));
            return this;
        }

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            ScriptHost host = request == null ? null : request.getHost();
            ReadCodeResponse state = states.get(host);
            return state != null
                ? state
                : new ReadCodeResponse(
                    host,
                    null,
                    CodeState.NO_CODE,
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
        public RunCodeResponse runCode(RunCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
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

package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
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
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModelContextProtocolToolDispatcherTest {

    @Test
    public void registryAndDispatcherUseSameOrderedToolObjectList() {
        List<Object> toolObjects = Arrays.<Object>asList(new FirstToolSet(), new SecondToolSet());
        ModelContextProtocolToolRegistry registry = new ModelContextProtocolToolRegistry(toolObjects, new ObjectMapper());
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(toolObjects, new ObjectMapper());

        List<ModelContextProtocolTool> tools = registry.listTools();
        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("secondTool", null)));
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        assertThat(tools).extracting(ModelContextProtocolTool::getName).containsExactly("firstTool", "secondTool");
        assertThat(result.get().resultText()).isEqualTo("second");
    }

    @Test
    public void dispatchBindsWriteCodeRequestFieldsForAiCodeTools() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCodeHostService codeHostService = new RecordingCodeHostService();
        AiCodeToolSet toolSet = new AiCodeToolSet(codeHostService, null, null, null);
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        final com.fasterxml.jackson.databind.JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"AI\",\"content\":{\"sourceText\":\"println 1\",\"argumentsJsonText\":\"{}\"},\"expectedStateToken\":{\"codeFingerprint\":\"code-fp\",\"argumentsFingerprint\":\"args-fp\"}}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("writeCode", argumentsNode)));

        assertThat(codeHostService.lastWriteRequest).isNotNull();
        assertThat(codeHostService.lastWriteRequest.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(codeHostService.lastWriteRequest.getContent().getSourceText()).isEqualTo("println 1");
        assertThat(codeHostService.lastWriteRequest.getExpectedStateToken().getArgumentsFingerprint()).isEqualTo("args-fp");
        assertThat(result.get().resultText()).contains("EDITED");
    }

    @Test
    public void dispatchBindsCompileCodeRequestFieldsForAiCodeTools() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCodeHostService codeHostService = new RecordingCodeHostService();
        AiCodeToolSet toolSet = new AiCodeToolSet(codeHostService, null, null, null);
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        final com.fasterxml.jackson.databind.JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"AI\",\"expectedStateToken\":{\"codeFingerprint\":\"code-fp\",\"argumentsFingerprint\":\"args-fp\"}}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("compileCode", argumentsNode)));

        assertThat(codeHostService.lastCompileRequest).isNotNull();
        assertThat(codeHostService.lastCompileRequest.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(codeHostService.lastCompileRequest.getExpectedStateToken().getArgumentsFingerprint()).isEqualTo("args-fp");
        assertThat(result.get().resultText()).contains("RUNNABLE");
    }

    @Test
    public void dispatchBindsRunCodeRequestFieldsForAiCodeTools() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCodeHostService codeHostService = new RecordingCodeHostService();
        AiCodeToolSet toolSet = new AiCodeToolSet(codeHostService, null, null, null);
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        final com.fasterxml.jackson.databind.JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"AI\",\"expectedStateToken\":{\"codeFingerprint\":\"code-fp\",\"argumentsFingerprint\":\"args-fp\"}}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("runCode", argumentsNode)));

        assertThat(codeHostService.lastRunRequest).isNotNull();
        assertThat(codeHostService.lastRunRequest.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(codeHostService.lastRunRequest.getExpectedStateToken().getArgumentsFingerprint()).isEqualTo("args-fp");
        assertThat(result.get().resultText()).contains("RUN_SUCCEEDED");
    }

    @Test
    public void dispatchBindsReadCodeRequestFieldsForAiCodeTools() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCodeHostService codeHostService = new RecordingCodeHostService();
        AiCodeToolSet toolSet = new AiCodeToolSet(codeHostService, null, null, null);
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        final com.fasterxml.jackson.databind.JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"ATTACHED_EDITOR\"}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("readCode", argumentsNode)));

        assertThat(codeHostService.lastReadRequest).isNotNull();
        assertThat(codeHostService.lastReadRequest.getHost()).isEqualTo(ScriptHost.ATTACHED_EDITOR);
        assertThat(result.get().resultText()).contains("RUNNABLE");
    }

    @Test
    public void dispatcherConsultsAuthorizerBeforeExecutingTool() {
        AtomicBoolean authorized = new AtomicBoolean(false);
        List<Object> toolObjects = Arrays.<Object>asList(new AuthorizationAwareToolSet(authorized));
        ModelContextProtocolToolCallAuthorizer authorizer = mock(ModelContextProtocolToolCallAuthorizer.class);
        doAnswer(invocation -> {
            authorized.set(true);
            return null;
        }).when(authorizer).assertAuthorized(eq("secondTool"), eq(null));
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            toolObjects,
            new ObjectMapper(),
            authorizer);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("secondTool", null)));
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        verify(authorizer).assertAuthorized(eq("secondTool"), eq(null));
        assertThat(result.get().resultText()).isEqualTo("authorized");
    }

    private static class RecordingCodeHostService implements AiCodeHostService {
        private ReadCodeRequest lastReadRequest;
        private WriteCodeRequest lastWriteRequest;
        private CompileCodeRequest lastCompileRequest;
        private RunCodeRequest lastRunRequest;

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            lastReadRequest = request;
            return new ReadCodeResponse(
                ScriptHost.ATTACHED_EDITOR,
                "text/plain",
                CodeState.RUNNABLE,
                null,
                token("read-fp"),
                new CodeStateContent("code", null),
                null,
                null,
                null,
                null);
        }

        @Override
        public WriteCodeResponse writeCode(WriteCodeRequest request) {
            lastWriteRequest = request;
            return new WriteCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.EDITED,
                token("fp"));
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            lastCompileRequest = request;
            return new CompileCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.RUNNABLE,
                token("fp"),
                null,
                null);
        }

        @Override
        public RunCodeResponse runCode(RunCodeRequest request) {
            lastRunRequest = request;
            return new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.RUN_SUCCEEDED,
                ScriptRunInitiator.AI,
                token("fp"),
                null,
                null,
                null,
                null);
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

    private static CodeStateToken token(String argumentsFingerprint) {
        return argumentsFingerprint == null ? null : new CodeStateToken("code", argumentsFingerprint);
    }

    private static class FirstToolSet {
        @Tool("first")
        public String firstTool() {
            return "first";
        }
    }

    private static class SecondToolSet {
        @Tool("second")
        public String secondTool() {
            return "second";
        }
    }

    private static class AuthorizationAwareToolSet {
        private final AtomicBoolean authorized;

        private AuthorizationAwareToolSet(AtomicBoolean authorized) {
            this.authorized = authorized;
        }

        @Tool("second")
        public String secondTool() {
            return authorized.get() ? "authorized" : "not authorized";
        }
    }
}

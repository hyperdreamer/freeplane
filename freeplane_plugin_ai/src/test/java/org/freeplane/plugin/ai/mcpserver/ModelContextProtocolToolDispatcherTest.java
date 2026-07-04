package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
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
import org.freeplane.plugin.ai.tools.read.ContextSection;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.search.SearchCaseSensitivity;
import org.freeplane.plugin.ai.tools.search.SearchMatchingMode;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
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
        final JsonNode argumentsNode = objectMapper.readTree(
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
        final JsonNode argumentsNode = objectMapper.readTree(
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
        final JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"AI\",\"expectedStateToken\":{\"codeFingerprint\":\"code-fp\",\"argumentsFingerprint\":\"args-fp\"}}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("runCode", argumentsNode)));

        assertThat(codeHostService.lastRunRequest).isNotNull();
        assertThat(codeHostService.lastRunRequest.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(codeHostService.lastRunRequest.getExpectedStateToken().getArgumentsFingerprint()).isEqualTo("args-fp");
        assertThat(result.get().resultText()).contains("RUN_SUCCEEDED");
    }

    @Test
    public void dispatchCompletesWaitingAiRunCodeWithTerminalResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WaitingAiCodeHostService delegate = new WaitingAiCodeHostService();
        CompletingAiCodeHostService codeHostService = new CompletingAiCodeHostService(delegate);
        List<ToolCallSummary> summaries = new ArrayList<ToolCallSummary>();
        AiCodeToolSet toolSet = new AiCodeToolSet(codeHostService, null, null, null);
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper,
            null,
            codeHostService,
            summaries::add);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        final JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"AI\",\"expectedStateToken\":{\"codeFingerprint\":\"code-fp\",\"argumentsFingerprint\":\"args-fp\"}}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("runCode", argumentsNode)));

        assertThat(delegate.lastRunRequest).isNotNull();
        assertThat(codeHostService.awaited).isTrue();
        assertThat(result.get().result()).isInstanceOf(RunCodeResponse.class);
        RunCodeResponse response = (RunCodeResponse) result.get().result();
        assertThat(response.getCodeState()).isEqualTo(CodeState.USER_RUN_CANCELLED);
        assertThat(result.get().resultText()).contains("USER_RUN_CANCELLED");
        assertThat(summaries).extracting(ToolCallSummary::getSummaryText)
            .containsExactly("runCode: codeState=USER_RUN_CANCELLED, host=AI");
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
        final JsonNode argumentsNode = objectMapper.readTree(
            "{\"request\":{\"host\":\"ATTACHED_EDITOR\"}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("readCode", argumentsNode)));

        assertThat(codeHostService.lastReadRequest).isNotNull();
        assertThat(codeHostService.lastReadRequest.getHost()).isEqualTo(ScriptHost.ATTACHED_EDITOR);
        assertThat(result.get().resultText()).contains("RUNNABLE");
    }

    @Test
    public void dispatchBindsCurrentToolRequestFieldNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingContextGatheringToolSet toolSet = new RecordingContextGatheringToolSet();
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            Arrays.<Object>asList(toolSet),
            objectMapper);
        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();

        final JsonNode readArgumentsNode = objectMapper.readTree(
            "{\"request\":{\"mapIdentifier\":\"map-identifier\",\"contextSections\":[\"QUALIFIERS\"],"
                + "\"fullContentDepth\":2,\"additionalSummaryDepth\":3,\"maxCharacters\":1000}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("readNodesWithDescendants", readArgumentsNode)));

        assertThat(toolSet.lastReadRequest).isNotNull();
        assertThat(toolSet.lastReadRequest.getFullContentDepth()).isEqualTo(2);
        assertThat(toolSet.lastReadRequest.getAdditionalSummaryDepth()).isEqualTo(3);
        assertThat(toolSet.lastReadRequest.getMaxCharacters()).isEqualTo(1000);
        assertThat(toolSet.lastReadRequest.hasFullContentDepth()).isTrue();
        assertThat(toolSet.lastReadRequest.hasAdditionalSummaryDepth()).isTrue();
        assertThat(toolSet.lastReadRequest.getContextSections()).containsExactly(ContextSection.QUALIFIERS);
        assertThat(result.get().resultText()).isEqualTo("read");

        final JsonNode searchArgumentsNode = objectMapper.readTree(
            "{\"request\":{\"mapIdentifier\":\"map-identifier\",\"queryText\":\"alpha\","
                + "\"matchingMode\":\"EQUALS\",\"caseSensitivity\":\"CASE_SENSITIVE\","
                + "\"offset\":4,\"limit\":5,\"maxCharacters\":2000}}");
        SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("searchNodes", searchArgumentsNode)));

        assertThat(toolSet.lastSearchRequest).isNotNull();
        assertThat(toolSet.lastSearchRequest.getMatchingMode()).isEqualTo(SearchMatchingMode.EQUALS);
        assertThat(toolSet.lastSearchRequest.getCaseSensitivity()).isEqualTo(SearchCaseSensitivity.CASE_SENSITIVE);
        assertThat(toolSet.lastSearchRequest.getOffset()).isEqualTo(4);
        assertThat(toolSet.lastSearchRequest.getLimit()).isEqualTo(5);
        assertThat(toolSet.lastSearchRequest.getMaxCharacters()).isEqualTo(2000);
        assertThat(toolSet.lastSearchRequest.hasMatchingMode()).isTrue();
        assertThat(toolSet.lastSearchRequest.hasCaseSensitivity()).isTrue();
        assertThat(toolSet.lastSearchRequest.hasOffset()).isTrue();
        assertThat(toolSet.lastSearchRequest.hasLimit()).isTrue();
        assertThat(result.get().resultText()).isEqualTo("search");
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

    private static class CompletingAiCodeHostService extends ModelContextProtocolAiCodeHostService {
        private boolean awaited;

        private CompletingAiCodeHostService(AiCodeHostService delegate) {
            super(delegate, () -> {}, () -> Long.valueOf(1000L));
        }

        @Override
        RunCodeResponse awaitFinalRunResponse(RunCodeResponse waitingResponse) {
            awaited = true;
            return new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.USER_RUN_CANCELLED,
                ScriptRunInitiator.USER,
                waitingResponse.getStateToken(),
                null,
                null,
                null,
                null);
        }
    }

    private static class WaitingAiCodeHostService implements AiCodeHostService {
        private RunCodeRequest lastRunRequest;

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            throw new UnsupportedOperationException();
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
            lastRunRequest = request;
            return new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.WAITING_FOR_USER_RUN,
                ScriptRunInitiator.AI,
                token("args-fp"),
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

    private static class RecordingContextGatheringToolSet {
        private ReadNodesWithDescendantsRequest lastReadRequest;
        private SearchNodesRequest lastSearchRequest;

        @Tool("read")
        public String readNodesWithDescendants(ReadNodesWithDescendantsRequest request) {
            lastReadRequest = request;
            return "read";
        }

        @Tool("search")
        public String searchNodes(SearchNodesRequest request) {
            lastSearchRequest = request;
            return "search";
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

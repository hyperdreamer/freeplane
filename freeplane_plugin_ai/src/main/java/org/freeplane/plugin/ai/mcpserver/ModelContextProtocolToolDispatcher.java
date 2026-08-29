package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorFactory;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorRegistry;

public class ModelContextProtocolToolDispatcher {
    private final ObjectMapper objectMapper;
    private final Map<String, ToolExecutor> toolExecutorsByName;
    private final ModelContextProtocolToolCallAuthorizer toolCallAuthorizer;
    private final ModelContextProtocolAiCodeHostService aiCodeHostService;
    private final ToolCallSummaryHandler toolCallSummaryHandler;

    public ModelContextProtocolToolDispatcher(Object toolSet, ObjectMapper objectMapper) {
        this(Collections.singletonList(Objects.requireNonNull(toolSet, "toolSet")), objectMapper, null);
    }

    public ModelContextProtocolToolDispatcher(Collection<?> toolSets, ObjectMapper objectMapper) {
        this(toolSets, objectMapper, null);
    }

    public ModelContextProtocolToolDispatcher(Collection<?> toolSets,
                                              ObjectMapper objectMapper,
                                              ModelContextProtocolToolCallAuthorizer toolCallAuthorizer) {
        this(toolSets, objectMapper, toolCallAuthorizer, null, null);
    }

    ModelContextProtocolToolDispatcher(Collection<?> toolSets,
                                       ObjectMapper objectMapper,
                                       ModelContextProtocolToolCallAuthorizer toolCallAuthorizer,
                                       ModelContextProtocolAiCodeHostService aiCodeHostService) {
        this(toolSets, objectMapper, toolCallAuthorizer, aiCodeHostService, null);
    }

    ModelContextProtocolToolDispatcher(Collection<?> toolSets,
                                       ObjectMapper objectMapper,
                                       ModelContextProtocolToolCallAuthorizer toolCallAuthorizer,
                                       ModelContextProtocolAiCodeHostService aiCodeHostService,
                                       ToolCallSummaryHandler toolCallSummaryHandler) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.toolCallAuthorizer = toolCallAuthorizer;
        this.aiCodeHostService = aiCodeHostService;
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        ToolExecutorFactory toolExecutorFactory = new ToolExecutorFactory(false, false);
        ToolExecutorRegistry toolExecutorRegistry = toolExecutorFactory.createRegistry(toolSets);
        this.toolExecutorsByName = toolExecutorRegistry.getExecutorsByName();
    }

    public ToolExecutionResult dispatch(String toolName, JsonNode argumentsNode) {
        return executeTool(toolName, argumentsNode);
    }

    private ToolExecutionResult executeTool(String toolName, JsonNode argumentsNode) {
        ToolExecutor executor = toolExecutorsByName.get(toolName);
        if (executor == null) {
            LogUtils.info(buildToolCallLog(toolName, null, "Unknown tool name: " + toolName));
            throw new IllegalArgumentException("Unknown tool name: " + toolName);
        }
        String arguments = "{}";
        if (argumentsNode != null && !argumentsNode.isNull()) {
            try {
                arguments = objectMapper.writeValueAsString(argumentsNode);
            } catch (Exception error) {
                LogUtils.info(buildToolCallLog(toolName, null, "Invalid tool arguments."));
                throw new IllegalArgumentException("Invalid tool arguments.", error);
            }
        }
        if (toolCallAuthorizer != null) {
            toolCallAuthorizer.assertAuthorized(toolName, argumentsNode);
        }
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name(toolName)
            .arguments(arguments)
            .build();
        ToolExecutionResult result = executor.executeWithContext(request, InvocationContext.builder().build());
        result = completeDelayedAiExecutionResult(toolName, result);
        if (result != null && result.isError()) {
            LogUtils.info(buildToolCallLog(toolName, arguments, result.resultText()));
        }
        return result;
    }

    private ToolExecutionResult completeDelayedAiExecutionResult(String toolName, ToolExecutionResult result) {
        if (aiCodeHostService == null || !isDelayedAiExecutionTool(toolName) || result == null) {
            return result;
        }
        Object rawResult = result.result();
        if (!(rawResult instanceof RunCodeResponse)) {
            return result;
        }
        RunCodeResponse waitingResponse = (RunCodeResponse) rawResult;
        if (!isWaitingAiRunResponse(waitingResponse)) {
            return result;
        }
        RunCodeResponse finalResponse = aiCodeHostService.awaitFinalRunResponse(waitingResponse);
        publishDelayedAiExecutionSummary(toolName, finalResponse);
        if (finalResponse == waitingResponse || isWaitingAiRunResponse(finalResponse)) {
            return result;
        }
        return toolExecutionResult(finalResponse);
    }

    private boolean isDelayedAiExecutionTool(String toolName) {
        return "runCode".equals(toolName) || "writeAndRunCode".equals(toolName);
    }

    private boolean isWaitingAiRunResponse(RunCodeResponse response) {
        return response != null
            && response.getHost() == ScriptHost.AI
            && response.getCodeState() == CodeState.WAITING_FOR_USER_RUN;
    }

    private void publishDelayedAiExecutionSummary(String toolName, RunCodeResponse response) {
        if (toolCallSummaryHandler == null || response == null) {
            return;
        }
        String summaryText = AiCodeToolSet.runCodeSummaryText(toolName, response);
        toolCallSummaryHandler.handleToolCallSummary(new ToolCallSummary(
            toolName,
            summaryText,
            isFailureState(response.getCodeState()),
            ToolCaller.MCP));
    }

    private boolean isFailureState(CodeState codeState) {
        return codeState == CodeState.INVALID_SCRIPT
            || codeState == CodeState.INVALID_ARGUMENTS_JSON
            || codeState == CodeState.RUN_FAILED;
    }

    private ToolExecutionResult toolExecutionResult(Object result) {
        return ToolExecutionResult.builder()
            .result(result)
            .resultTextSupplier(() -> Json.toJson(result))
            .build();
    }

    private String buildToolCallLog(String toolName, String arguments, String errorMessage) {
        String safeToolName = toolName == null ? "unknown tool" : toolName;
        String safeArguments = arguments == null ? "" : arguments;
        String safeError = errorMessage == null ? "" : errorMessage;
        return "MCP tool error: tool=" + safeToolName + ", arguments=" + safeArguments + ", error=" + safeError;
    }

}

package org.freeplane.plugin.ai.tools.code;

import dev.langchain4j.agent.tool.Tool;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteAndRunCodeRequest;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AiCodeToolSet {
    private static final Set<String> TOOL_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "readCode",
        "writeCode",
        "compileCode",
        "runCode",
        "writeAndRunCode")));
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";
    private static final String FORMULA_CONDITION_CONTENT_TYPE = "text/x-freeplane-formula-condition-groovy";

    private final AiCodeHostService codeHostService;
    private final AiCodeOperationAuthorizer aiCodeOperationAuthorizer;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolCaller toolCaller;

    public AiCodeToolSet(AiCodeHostService codeHostService,
                         AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                         ToolCallSummaryHandler toolCallSummaryHandler,
                         ToolCaller toolCaller) {
        this.codeHostService = codeHostService;
        this.aiCodeOperationAuthorizer = aiCodeOperationAuthorizer;
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
    }

    public static Collection<String> toolNames() {
        return TOOL_NAMES;
    }

    @Tool("Read the current code state from host AI or ATTACHED_EDITOR. The response includes the content.")
    public ReadCodeResponse readCode(ReadCodeToolRequest request) {
        try {
            ReadCodeRequest codeRequest = toReadCodeRequest(request);
            assertAuthorized("readCode", codeRequest == null ? null : codeRequest.getHost());
            ReadCodeResponse response = codeHostService.readCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "readCode",
                "readCode: codeState=" + response.getCodeState() + ", host=" + response.getHost(),
                isFailureState(response.getCodeState()),
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "readCode",
                "readCode error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Store content in host AI or ATTACHED_EDITOR. For existing AI code, expectedStateToken must match readCode; omit it only for new AI code. ATTACHED_EDITOR writes only the draft. content contains sourceText and optional argumentsJsonText. Attached formula editing requires writeCode and compileCode availability plus AI formula editing.")
    public WriteCodeResponse writeCode(WriteCodeToolRequest request) {
        try {
            WriteCodeRequest codeRequest = toWriteCodeRequest(request);
            assertAuthorized("writeCode", codeRequest == null ? null : codeRequest.getHost());
            WriteCodeResponse response = codeHostService.writeCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "writeCode",
                "writeCode: codeState=" + response.getCodeState() + ", host=" + response.getHost(),
                false,
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "writeCode",
                "writeCode error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Compile current code in host AI or ATTACHED_EDITOR without running it. It accepts no source text. expectedStateToken must match readCode; for new AI code, call writeCode first. Attached formula compilation requires writeCode and compileCode availability plus AI formula editing.")
    public CompileCodeResponse compileCode(CompileCodeToolRequest request) {
        try {
            CompileCodeRequest codeRequest = toCompileCodeRequest(request);
            assertAuthorized("compileCode", codeRequest == null ? null : codeRequest.getHost());
            CompileCodeResponse response = codeHostService.compileCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "compileCode",
                "compileCode: codeState=" + response.getCodeState() + ", host=" + response.getHost(),
                isFailureState(response.getCodeState()),
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "compileCode",
                "compileCode error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Run current code in host AI or ATTACHED_EDITOR with the current Freeplane selection. It accepts no source text. expectedStateToken must match readCode; for new AI code, call writeCode first. Content and condition formulas are not runnable. Scripts may affect the UI; prefer return values or stdout and avoid UI or state-changing calls unless requested.")
    public RunCodeResponse runCode(RunCodeToolRequest request) {
        try {
            RunCodeRequest codeRequest = toRunCodeRequest(request);
            assertAuthorized("runCode", codeRequest == null ? null : codeRequest.getHost());
            RunCodeResponse response = codeHostService.runCode(codeRequest);
            if (!isDeferredMcpRunSummary(response)) {
                publishSummary(new ToolCallSummary(
                    "runCode",
                    runCodeSummaryText("runCode", response),
                    isFailureState(response.getCodeState()),
                    toolCaller));
            }
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "runCode",
                "runCode error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Store content in the AI-owned Groovy script host, then compile and run it against the current Freeplane selection. No prior readCode or expectedStateToken is required. content contains sourceText and optional argumentsJsonText. argumentsJsonText is parsed as JSON and exposed to the script as args. The usual approval dialog appears when required. Content and condition formulas are not runnable. Scripts may affect the UI; prefer return values or stdout and avoid UI or state-changing calls unless requested.")
    public RunCodeResponse writeAndRunCode(WriteAndRunCodeToolRequest request) {
        try {
            WriteAndRunCodeRequest codeRequest = toWriteAndRunCodeRequest(request);
            assertAuthorized("writeAndRunCode", ScriptHost.AI);
            RunCodeResponse response = codeHostService.writeAndRunCode(codeRequest);
            if (!isDeferredMcpRunSummary(response)) {
                publishSummary(new ToolCallSummary(
                    "writeAndRunCode",
                    runCodeSummaryText("writeAndRunCode", response),
                    isFailureState(response.getCodeState()),
                    toolCaller));
            }
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "writeAndRunCode",
                "writeAndRunCode error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    public static String runCodeSummaryText(String methodName, RunCodeResponse response) {
        return methodName + ": codeState=" + response.getCodeState() + ", host=" + response.getHost();
    }

    private boolean isDeferredMcpRunSummary(RunCodeResponse response) {
        return toolCaller == ToolCaller.MCP
            && response != null
            && response.getHost() == ScriptHost.AI
            && response.getCodeState() == CodeState.WAITING_FOR_USER_RUN;
    }

    public Set<String> authorizedToolNames() {
        if (aiCodeOperationAuthorizer == null) {
            return TOOL_NAMES;
        }
        return aiCodeOperationAuthorizer.authorizedToolNames();
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input) {
        boolean writeAuthorized = authorizedToolNames().contains("writeCode");
        boolean compileAuthorized = authorizedToolNames().contains("compileCode");
        boolean runAuthorized = authorizedToolNames().contains("runCode");
        boolean writeAndRunAuthorized = authorizedToolNames().contains("writeAndRunCode");
        ReadCodeResponse response;
        try {
            response = codeHostService.readCode(new ReadCodeRequest(ScriptHost.ATTACHED_EDITOR));
        } catch (RuntimeException error) {
            response = null;
        }
        if (response == null || response.getCodeState() == CodeState.NO_CODE) {
            return genericAiHostGuidance(writeAuthorized, compileAuthorized, runAuthorized, writeAndRunAuthorized);
        }
        if (FORMULA_CONTENT_TYPE.equals(response.getContentType())
            || FORMULA_CONDITION_CONTENT_TYPE.equals(response.getContentType())) {
            boolean conditionFormula = FORMULA_CONDITION_CONTENT_TYPE.equals(response.getContentType());
            String toolGuidance = writeAuthorized && compileAuthorized
                ? "Use readCode, writeCode, and compileCode. "
                : "Use readCode. Authoring requires writeCode, compileCode, and AI formula editing. ";
            String contentType = conditionFormula ? "condition formula" : "content formula";
            String resultRule = conditionFormula ? " Condition formulas return Boolean or Number." : "";
            return "An editor is attached to this chat. " + toolGuidance
                + "Target host ATTACHED_EDITOR. The attached content is a " + contentType + ". "
                + "Keep it argument-free and value-computing; runCode is not supported. "
                + "Avoid state-changing or UI calls. "
                + "writeCode edits the draft; compileCode needs readCode's stateToken."
                + resultRule
                + " Do not assume submit; repair requires user approval.";
        }
        if (SCRIPT_CONTENT_TYPE.equals(response.getContentType())) {
            return runAuthorized
                ? "An editor is attached to this chat. Use readCode, writeCode, compileCode, and runCode. "
                    + "Target host ATTACHED_EDITOR. The attached content is a script. "
                    + "writeCode changes only the current draft content. content contains sourceText and argumentsJsonText. compileCode and runCode act on the attached editor's current code state, do not accept source text directly, and require the current stateToken from readCode. "
                    + "Do not copy attached code into the AI-owned script host and run it there unless the user explicitly asks."
                : "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                    + "Target host ATTACHED_EDITOR. The attached content is a script. "
                    + "writeCode changes only the current draft content. content contains sourceText and argumentsJsonText. compileCode acts on the attached editor's current code state and requires the current stateToken from readCode. Do not assume execution support.";
        }
        return runAuthorized
            ? "An editor is attached to this chat. Use readCode, writeCode, compileCode, and runCode. "
                + "Target host ATTACHED_EDITOR. compileCode and runCode act on the attached editor's current code state, do not accept source text directly, and require the current stateToken from readCode."
            : "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                + "Target host ATTACHED_EDITOR. compileCode acts on the attached editor's current code state and requires the current stateToken from readCode.";
    }

    private String genericAiHostGuidance(boolean writeAuthorized,
                                         boolean compileAuthorized,
                                         boolean runAuthorized,
                                         boolean writeAndRunAuthorized) {
        if (!writeAuthorized || !compileAuthorized || !runAuthorized || !writeAndRunAuthorized) {
            return null;
        }
        return "AI-owned code tools are available in this chat. Use writeAndRunCode for a new script when one call should store and run it; it accepts content with sourceText and argumentsJsonText, targets host AI, and does not require a prior readCode or expectedStateToken. "
            + "For the explicit token-checked workflow, use readCode, writeCode, compileCode, and runCode. Those tools act on stored host state, and writeCode, compileCode, and runCode require the current stateToken from readCode when AI-owned code already exists. "
            + "compileCode and runCode do not accept source text directly.";
    }

    private void assertAuthorized(String operation, ScriptHost host) {
        if (aiCodeOperationAuthorizer != null) {
            aiCodeOperationAuthorizer.assertAuthorized(operation, host);
        }
    }

    private ReadCodeRequest toReadCodeRequest(ReadCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new ReadCodeRequest(request.getHost());
    }

    private WriteCodeRequest toWriteCodeRequest(WriteCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new WriteCodeRequest(
            request.getHost(),
            request.getContent() == null ? null : request.getContent().toCodeStateContent(),
            request.getExpectedStateToken());
    }

    private CompileCodeRequest toCompileCodeRequest(CompileCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new CompileCodeRequest(request.getHost(), request.getExpectedStateToken());
    }

    private RunCodeRequest toRunCodeRequest(RunCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new RunCodeRequest(request.getHost(), request.getExpectedStateToken());
    }

    private WriteAndRunCodeRequest toWriteAndRunCodeRequest(WriteAndRunCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new WriteAndRunCodeRequest(
            request.getContent() == null ? null : request.getContent().toCodeStateContent());
    }

    private boolean isFailureState(CodeState codeState) {
        return codeState == CodeState.INVALID_SCRIPT
            || codeState == CodeState.INVALID_ARGUMENTS_JSON
            || codeState == CodeState.RUN_FAILED;
    }

    private void publishSummary(ToolCallSummary summary) {
        if (summary == null) {
            return;
        }
        LogUtils.info(summary.getSummaryText());
        if (toolCallSummaryHandler != null) {
            toolCallSummaryHandler.handleToolCallSummary(summary);
        }
    }

    private String safeMessage(RuntimeException error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return RuntimeException.class.getSimpleName();
        }
        return error.getMessage().trim();
    }
}

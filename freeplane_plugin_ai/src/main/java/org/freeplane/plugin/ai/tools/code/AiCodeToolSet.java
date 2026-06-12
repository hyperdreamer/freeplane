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
        "runCode")));
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";

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

    @Tool("Read the current code state stored in the requested host. Pass host AI for AI-owned code or ATTACHED_EDITOR for an attached editor. The response always includes the current content.")
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

    @Tool("Create or replace the current code state in the requested host by storing the provided content there. Use host AI for AI-owned code or ATTACHED_EDITOR for an attached editor. content contains sourceText and argumentsJsonText. For new AI-owned code, call this first without expectedStateToken only when no current AI-owned code exists. Otherwise expectedStateToken must match the current full state. For the attached editor this updates only the current draft content. Attached formula editing is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled. Current attached formula editing capability: enabled when the attached editor content is a formula.")
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

    @Tool("Compile the current code state for the requested host without executing it. This compiles code already present in the target host; it does not accept source text directly. expectedStateToken is required and must match the current full state, so readCode first when needed. For new AI-owned code, call writeCode first. Attached formula compilation is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled. Current attached formula editing capability: enabled when the attached editor content is a formula.")
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

    @Tool("Run the current code state for the requested host using the current Freeplane selection. This runs code already present in the target host; it does not accept source text directly. expectedStateToken is required and must match the current full state, so readCode first when needed. For new AI-owned code, call writeCode first. Only script content is runnable. Running code may trigger UI side effects. Avoid scripts that show dialogs, notifications, or alter visible UI state unless explicitly requested by the user. Prefer return values or stdout over UI output.")
    public RunCodeResponse runCode(RunCodeToolRequest request) {
        try {
            RunCodeRequest codeRequest = toRunCodeRequest(request);
            assertAuthorized("runCode", codeRequest == null ? null : codeRequest.getHost());
            RunCodeResponse response = codeHostService.runCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "runCode",
                "runCode: codeState=" + response.getCodeState() + ", host=" + response.getHost(),
                isFailureState(response.getCodeState()),
                toolCaller));
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
        ReadCodeResponse response;
        try {
            response = codeHostService.readCode(new ReadCodeRequest(ScriptHost.ATTACHED_EDITOR));
        } catch (RuntimeException error) {
            response = null;
        }
        if (response == null || response.getCodeState() == CodeState.NO_CODE) {
            return genericAiHostGuidance(writeAuthorized, compileAuthorized, runAuthorized);
        }
        if (FORMULA_CONTENT_TYPE.equals(response.getContentType())) {
            String toolGuidance = writeAuthorized && compileAuthorized
                ? "Use readCode, writeCode, and compileCode. "
                : "Use readCode. Formula authoring is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled. ";
            return "An editor is attached to this chat. " + toolGuidance
                + "Target host ATTACHED_EDITOR. The attached content is a formula. "
                + "Keep it value-computing. Avoid state-changing Freeplane API calls and avoid obviously UI-driving calls. "
                + "Use the available Freeplane API documentation for API surface and semantics, but do not assume it explicitly marks which methods are UI-related. "
                + "writeCode changes only the current draft content. compileCode acts on the attached editor's current code state and requires the current stateToken from readCode. argumentsJsonText stays blank or null for formulas. "
                + "Do not assume submit or execution while the editor stays open. Submit-failure repair requests require user approval.";
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

    private String genericAiHostGuidance(boolean writeAuthorized, boolean compileAuthorized, boolean runAuthorized) {
        if (!writeAuthorized || !compileAuthorized || !runAuthorized) {
            return null;
        }
        return "AI-owned code tools are available in this chat. Use readCode, writeCode, compileCode, and runCode. "
            + "These tools act on the current code state of the targeted host, not on source text quoted in chat. "
            + "For new AI-owned code, first call writeCode with host AI and content. content contains sourceText and argumentsJsonText. If current AI-owned code already exists, writeCode, compileCode, and runCode require the current stateToken from readCode. "
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
        return new WriteCodeRequest(request.getHost(), request.getContent(), request.getExpectedStateToken());
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

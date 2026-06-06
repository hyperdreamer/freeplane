package org.freeplane.plugin.ai.tools.code;

import dev.langchain4j.agent.tool.Tool;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiCodeHostService;
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
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AiCodeToolSet {
    private static final Set<String> TOOL_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "readCode",
        "writeCode",
        "compileCode",
        "runScript")));
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

    @Tool("Read the current code state for the requested host or codeId. When no codeId is known for the attached editor, specify host ATTACHED_EDITOR.")
    public ReadCodeResponse readCode(ReadCodeToolRequest request) {
        try {
            ReadCodeRequest codeRequest = toReadCodeRequest(request);
            assertAuthorized("readCode", codeRequest == null ? null : codeRequest.getCodeId(),
                codeRequest == null ? null : codeRequest.getHost());
            ReadCodeResponse response = codeHostService.readCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "readCode",
                "readCode: status=" + response.getStatus() + ", host=" + response.getHost(),
                response.getStatus() == CodeLifecycleStatus.FAILED,
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

    @Tool("Replace the full current code text for the requested host or codeId. For the attached editor this updates only the draft text. Attached formula editing is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled.")
    public WriteCodeResponse writeCode(WriteCodeToolRequest request) {
        try {
            WriteCodeRequest codeRequest = toWriteCodeRequest(request);
            assertAuthorized("writeCode", codeRequest == null ? null : codeRequest.getCodeId(),
                codeRequest == null ? null : codeRequest.getHost());
            WriteCodeResponse response = codeHostService.writeCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "writeCode",
                "writeCode: status=" + response.getStatus() + ", host=" + response.getHost(),
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

    @Tool("Compile the current code for the requested host or codeId without executing it. Attached formula compilation is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled.")
    public CompileCodeResponse compileCode(CompileCodeToolRequest request) {
        try {
            CompileCodeRequest codeRequest = toCompileCodeRequest(request);
            assertAuthorized("compileCode", codeRequest == null ? null : codeRequest.getCodeId(),
                codeRequest == null ? null : codeRequest.getHost());
            CompileCodeResponse response = codeHostService.compileCode(codeRequest);
            publishSummary(new ToolCallSummary(
                "compileCode",
                "compileCode: status=" + response.getStatus() + ", host=" + response.getHost(),
                response.getStatus() == CodeLifecycleStatus.FAILED,
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

    @Tool("Run the current script for the requested host or codeId using the current Freeplane selection.")
    public RunScriptResponse runScript(RunScriptToolRequest request) {
        try {
            RunScriptRequest codeRequest = toRunScriptRequest(request);
            assertAuthorized("runScript", codeRequest == null ? null : codeRequest.getCodeId(),
                codeRequest == null ? null : codeRequest.getHost());
            RunScriptResponse response = codeHostService.runScript(codeRequest);
            publishSummary(new ToolCallSummary(
                "runScript",
                "runScript: status=" + response.getStatus() + ", host=" + response.getHost(),
                response.getStatus() == CodeLifecycleStatus.FAILED,
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "runScript",
                "runScript error: " + safeMessage(error),
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
        ReadCodeResponse response;
        try {
            response = codeHostService.readCode(new ReadCodeRequest(null, ScriptHost.ATTACHED_EDITOR, null));
        } catch (RuntimeException error) {
            return null;
        }
        if (response == null || response.getStatus() == CodeLifecycleStatus.NO_CODE) {
            return null;
        }
        if (FORMULA_CONTENT_TYPE.equals(response.getContentType())) {
            boolean writeAuthorized = authorizedToolNames().contains("writeCode");
            boolean compileAuthorized = authorizedToolNames().contains("compileCode");
            String toolGuidance = writeAuthorized && compileAuthorized
                ? "Use readCode, writeCode, and compileCode. "
                : "Use readCode. Formula authoring is available only when the current tool availability exposes writeCode and compileCode and AI formula editing is enabled. ";
            return "An editor is attached to this chat. " + toolGuidance
                + "When you do not know codeId yet, target host ATTACHED_EDITOR. The attached content is a formula. "
                + "Keep it value-computing. Avoid state-changing Freeplane API calls and avoid obviously UI-driving calls. "
                + "Use the available Freeplane API documentation for API surface and semantics, but do not assume it explicitly marks which methods are UI-related. "
                + "When writeCode is available, it changes only the draft text. Do not assume submit or execution while the editor stays open. "
                + "Submit-failure repair requests require user approval.";
        }
        boolean runAuthorized = authorizedToolNames().contains("runScript");
        if (SCRIPT_CONTENT_TYPE.equals(response.getContentType())) {
            return runAuthorized
                ? "An editor is attached to this chat. Use readCode, writeCode, compileCode, and runScript. "
                    + "When you do not know codeId yet, target host ATTACHED_EDITOR. The attached content is a script. "
                    + "writeCode changes only the draft text. Do not copy attached code into the AI-owned script host and run it there unless the user explicitly asks."
                : "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                    + "When you do not know codeId yet, target host ATTACHED_EDITOR. The attached content is a script. "
                    + "writeCode changes only the draft text. Do not assume execution support.";
        }
        return runAuthorized
            ? "An editor is attached to this chat. Use readCode, writeCode, compileCode, and runScript. "
                + "When you do not know codeId yet, target host ATTACHED_EDITOR."
            : "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                + "When you do not know codeId yet, target host ATTACHED_EDITOR.";
    }

    private void assertAuthorized(String operation, String codeId, ScriptHost host) {
        if (aiCodeOperationAuthorizer != null) {
            aiCodeOperationAuthorizer.assertAuthorized(operation, codeId, host);
        }
    }

    private ReadCodeRequest toReadCodeRequest(ReadCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new ReadCodeRequest(request.getCodeId(), request.getHost(), request.getFingerprint());
    }

    private WriteCodeRequest toWriteCodeRequest(WriteCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new WriteCodeRequest(request.getCodeId(), request.getHost(), request.getText(),
            request.getExpectedFingerprint());
    }

    private CompileCodeRequest toCompileCodeRequest(CompileCodeToolRequest request) {
        if (request == null) {
            return null;
        }
        return new CompileCodeRequest(request.getCodeId(), request.getHost(), request.getExpectedFingerprint());
    }

    private RunScriptRequest toRunScriptRequest(RunScriptToolRequest request) {
        if (request == null) {
            return null;
        }
        return new RunScriptRequest(request.getCodeId(), request.getHost(), request.getExpectedFingerprint());
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

package org.freeplane.plugin.ai.code;

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
        "compileCode")));
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";

    private final AiCodeHostService codeHostService;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolCaller toolCaller;

    public AiCodeToolSet(AiCodeHostService codeHostService,
                         ToolCallSummaryHandler toolCallSummaryHandler,
                         ToolCaller toolCaller) {
        this.codeHostService = codeHostService;
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
    }

    public static Collection<String> toolNames() {
        return TOOL_NAMES;
    }

    @Tool("Read the current code state for the requested host or codeId. When no codeId is known for the attached editor, specify host ATTACHED_EDITOR.")
    public ReadCodeResponse readCode(ReadCodeRequest request) {
        try {
            ReadCodeResponse response = codeHostService.readCode(request);
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

    @Tool("Replace the full current code text for the requested host or codeId. For the attached editor this updates only the draft text.")
    public WriteCodeResponse writeCode(WriteCodeRequest request) {
        try {
            WriteCodeResponse response = codeHostService.writeCode(request);
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

    @Tool("Compile the current code for the requested host or codeId without executing it.")
    public CompileCodeResponse compileCode(CompileCodeRequest request) {
        try {
            CompileCodeResponse response = codeHostService.compileCode(request);
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
            return "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                + "When you do not know codeId yet, target host ATTACHED_EDITOR. The attached content is a formula. "
                + "Keep the formula read-only and value-computing. Avoid state-changing Freeplane API calls and avoid "
                + "obviously UI-driving calls. Use the available Freeplane API documentation for API surface and semantics, "
                + "but do not assume it explicitly marks which methods are UI-related. writeCode changes only the draft text. "
                + "Do not assume submit or execution while the editor stays open. Submit-failure repair requests require user approval.";
        }
        if (SCRIPT_CONTENT_TYPE.equals(response.getContentType())) {
            return "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
                + "When you do not know codeId yet, target host ATTACHED_EDITOR. The attached content is a script. "
                + "writeCode changes only the draft text. Do not assume execution support.";
        }
        return "An editor is attached to this chat. Use readCode, writeCode, and compileCode. "
            + "When you do not know codeId yet, target host ATTACHED_EDITOR.";
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

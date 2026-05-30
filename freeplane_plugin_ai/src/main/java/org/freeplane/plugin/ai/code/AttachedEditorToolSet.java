package org.freeplane.plugin.ai.code;

import dev.langchain4j.agent.tool.Tool;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AttachedEditorToolSet {
    private static final Set<String> TOOL_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "readAttachedEditor",
        "overwriteAttachedEditorContent",
        "compileAttachedEditorContent",
        "getAttachedEditorLatestIssue")));
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";

    private final AttachedEditorProvider attachedEditorProvider;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolCaller toolCaller;

    public AttachedEditorToolSet(AttachedEditorProvider attachedEditorProvider,
                                 ToolCallSummaryHandler toolCallSummaryHandler,
                                 ToolCaller toolCaller) {
        this.attachedEditorProvider = attachedEditorProvider;
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
    }

    public static Collection<String> toolNames() {
        return TOOL_NAMES;
    }

    @Tool("Read the currently attached editor state, including attachment presence, live text, and latest issue presence.")
    public ReadAttachedEditorResponse readAttachedEditor() {
        try {
            ReadAttachedEditorResponse response = attachedEditorProvider.readAttachedEditor();
            publishSummary(new ToolCallSummary(
                "readAttachedEditor",
                "readAttachedEditor: attached=" + response.isAttached() + ", hasIssue=" + response.isHasIssue(),
                false,
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "readAttachedEditor",
                "readAttachedEditor error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Replace the full text of the currently attached editor.")
    public OverwriteAttachedEditorContentResponse overwriteAttachedEditorContent(
        OverwriteAttachedEditorContentRequest request) {
        try {
            OverwriteAttachedEditorContentResponse response = attachedEditorProvider.overwriteAttachedEditorContent(
                request == null ? null : request.getText());
            publishSummary(new ToolCallSummary(
                "overwriteAttachedEditorContent",
                "overwriteAttachedEditorContent: updated",
                false,
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "overwriteAttachedEditorContent",
                "overwriteAttachedEditorContent error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Compile the content of the currently attached editor without executing it.")
    public AiChatCodeOperationResult compileAttachedEditorContent() {
        try {
            AiChatCodeOperationResult response = attachedEditorProvider.compileAttachedEditorContent();
            publishSummary(new ToolCallSummary(
                "compileAttachedEditorContent",
                "compileAttachedEditorContent: successful=" + response.isSuccessful(),
                !response.isSuccessful(),
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "compileAttachedEditorContent",
                "compileAttachedEditorContent error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    @Tool("Read the latest unsuccessful issue recorded for the currently attached editor.")
    public ReadAttachedEditorLatestIssueResponse getAttachedEditorLatestIssue() {
        try {
            ReadAttachedEditorLatestIssueResponse response = attachedEditorProvider.getAttachedEditorLatestIssue();
            publishSummary(new ToolCallSummary(
                "getAttachedEditorLatestIssue",
                "getAttachedEditorLatestIssue: hasIssue=" + response.isHasIssue(),
                false,
                toolCaller));
            return response;
        } catch (RuntimeException error) {
            publishSummary(new ToolCallSummary(
                "getAttachedEditorLatestIssue",
                "getAttachedEditorLatestIssue error: " + safeMessage(error),
                true,
                toolCaller));
            throw error;
        }
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input) {
        if (!attachedEditorProvider.hasAttachedEditor()) {
            return null;
        }
        String contentType = attachedEditorProvider.attachedContentType();
        if (FORMULA_CONTENT_TYPE.equals(contentType)) {
            return "An editor is attached to this chat. You may use readAttachedEditor, "
                + "overwriteAttachedEditorContent, compileAttachedEditorContent, and "
                + "getAttachedEditorLatestIssue. The attached content is a formula. Keep "
                + "the formula read-only and value-computing. Avoid state-changing Freeplane "
                + "API calls and avoid obviously UI-driving calls. Use the available "
                + "Freeplane API documentation for API surface and semantics, but do not "
                + "assume it explicitly marks which methods are UI-related. Do not assume "
                + "live execution while the editor stays open. Submit-failure repair "
                + "requests require user approval.";
        }
        if (SCRIPT_CONTENT_TYPE.equals(contentType)) {
            return "An editor is attached to this chat. You may use readAttachedEditor, "
                + "overwriteAttachedEditorContent, compileAttachedEditorContent, and "
                + "getAttachedEditorLatestIssue. The attached content is a script. Do not "
                + "assume execution support.";
        }
        return "An editor is attached to this chat. You may use readAttachedEditor, "
            + "overwriteAttachedEditorContent, compileAttachedEditorContent, and "
            + "getAttachedEditorLatestIssue.";
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

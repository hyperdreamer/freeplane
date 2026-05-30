package org.freeplane.plugin.ai.code;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;

public class ReadAttachedEditorLatestIssueResponse {
    private final boolean hasIssue;
    private final AiChatCodeOperationResult issue;

    public ReadAttachedEditorLatestIssueResponse(boolean hasIssue, AiChatCodeOperationResult issue) {
        this.hasIssue = hasIssue;
        this.issue = issue;
    }

    public static ReadAttachedEditorLatestIssueResponse noIssue() {
        return new ReadAttachedEditorLatestIssueResponse(false, null);
    }

    public boolean isHasIssue() {
        return hasIssue;
    }

    public AiChatCodeOperationResult getIssue() {
        return issue;
    }
}

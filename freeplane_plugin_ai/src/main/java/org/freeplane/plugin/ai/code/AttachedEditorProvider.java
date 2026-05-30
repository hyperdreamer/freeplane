package org.freeplane.plugin.ai.code;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;

public interface AttachedEditorProvider {
    ReadAttachedEditorResponse readAttachedEditor();

    OverwriteAttachedEditorContentResponse overwriteAttachedEditorContent(String text);

    AiChatCodeOperationResult compileAttachedEditorContent();

    ReadAttachedEditorLatestIssueResponse getAttachedEditorLatestIssue();

    boolean hasAttachedEditor();

    String attachedContentType();
}

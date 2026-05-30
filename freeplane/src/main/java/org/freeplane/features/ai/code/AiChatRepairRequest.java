package org.freeplane.features.ai.code;

import java.util.Objects;

public class AiChatRepairRequest {
    private final String prompt;
    private final String sourceText;
    private final AiChatCodeOperationResult issue;

    public AiChatRepairRequest(String prompt, String sourceText, AiChatCodeOperationResult issue) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.sourceText = sourceText;
        this.issue = Objects.requireNonNull(issue, "issue");
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSourceText() {
        return sourceText;
    }

    public AiChatCodeOperationResult getIssue() {
        return issue;
    }
}

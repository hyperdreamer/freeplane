package org.freeplane.features.ai.code;

import java.util.Objects;

public class AiChatRepairRequest {
    private final String prompt;
    private final ReadCodeResponse codeState;

    public AiChatRepairRequest(String prompt, ReadCodeResponse codeState) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.codeState = Objects.requireNonNull(codeState, "codeState");
    }

    public String getPrompt() {
        return prompt;
    }

    public ReadCodeResponse getCodeState() {
        return codeState;
    }
}

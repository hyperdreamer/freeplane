package org.freeplane.features.ai.code;

public class WriteAndRunCodeRequest {
    private CodeStateContent content;

    public WriteAndRunCodeRequest() {
    }

    public WriteAndRunCodeRequest(CodeStateContent content) {
        this.content = content;
    }

    public CodeStateContent getContent() {
        return content;
    }

    public void setContent(CodeStateContent content) {
        this.content = content;
    }
}

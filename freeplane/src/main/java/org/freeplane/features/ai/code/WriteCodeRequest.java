package org.freeplane.features.ai.code;

public class WriteCodeRequest {
    private ScriptHost host;
    private CodeStateContent content;
    private CodeStateToken expectedStateToken;

    public WriteCodeRequest() {
    }

    public WriteCodeRequest(ScriptHost host, CodeStateContent content, CodeStateToken expectedStateToken) {
        this.host = host;
        this.content = content;
        this.expectedStateToken = expectedStateToken;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }

    public CodeStateContent getContent() {
        return content;
    }

    public void setContent(CodeStateContent content) {
        this.content = content;
    }

    public CodeStateToken getExpectedStateToken() {
        return expectedStateToken;
    }

    public void setExpectedStateToken(CodeStateToken expectedStateToken) {
        this.expectedStateToken = expectedStateToken;
    }
}

package org.freeplane.features.ai.code;

public class RunCodeRequest {
    private ScriptHost host;
    private CodeStateToken expectedStateToken;

    public RunCodeRequest() {
    }

    public RunCodeRequest(ScriptHost host, CodeStateToken expectedStateToken) {
        this.host = host;
        this.expectedStateToken = expectedStateToken;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }

    public CodeStateToken getExpectedStateToken() {
        return expectedStateToken;
    }

    public void setExpectedStateToken(CodeStateToken expectedStateToken) {
        this.expectedStateToken = expectedStateToken;
    }
}

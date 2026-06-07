package org.freeplane.features.ai.code;

public class RunCodeRequest {
    private ScriptHost host;
    private String expectedFingerprint;

    public RunCodeRequest() {
    }

    public RunCodeRequest(ScriptHost host, String expectedFingerprint) {
        this.host = host;
        this.expectedFingerprint = expectedFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }

    public void setExpectedFingerprint(String expectedFingerprint) {
        this.expectedFingerprint = expectedFingerprint;
    }
}

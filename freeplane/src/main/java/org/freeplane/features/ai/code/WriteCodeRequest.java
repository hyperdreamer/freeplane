package org.freeplane.features.ai.code;

public class WriteCodeRequest {
    private ScriptHost host;
    private String text;
    private String expectedFingerprint;

    public WriteCodeRequest() {
    }

    public WriteCodeRequest(ScriptHost host, String text, String expectedFingerprint) {
        this.host = host;
        this.text = text;
        this.expectedFingerprint = expectedFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }

    public void setExpectedFingerprint(String expectedFingerprint) {
        this.expectedFingerprint = expectedFingerprint;
    }
}

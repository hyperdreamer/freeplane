package org.freeplane.features.ai.code;

public class WriteCodeRequest {
    private String codeId;
    private ScriptHost host;
    private String text;
    private String expectedFingerprint;

    public WriteCodeRequest() {
    }

    public WriteCodeRequest(String codeId, ScriptHost host, String text, String expectedFingerprint) {
        this.codeId = codeId;
        this.host = host;
        this.text = text;
        this.expectedFingerprint = expectedFingerprint;
    }

    public String getCodeId() {
        return codeId;
    }

    public void setCodeId(String codeId) {
        this.codeId = codeId;
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

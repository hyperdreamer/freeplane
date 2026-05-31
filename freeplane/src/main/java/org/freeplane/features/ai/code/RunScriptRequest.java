package org.freeplane.features.ai.code;

public class RunScriptRequest {
    private String codeId;
    private ScriptHost host;
    private String expectedFingerprint;

    public RunScriptRequest() {
    }

    public RunScriptRequest(String codeId, ScriptHost host, String expectedFingerprint) {
        this.codeId = codeId;
        this.host = host;
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

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }

    public void setExpectedFingerprint(String expectedFingerprint) {
        this.expectedFingerprint = expectedFingerprint;
    }
}

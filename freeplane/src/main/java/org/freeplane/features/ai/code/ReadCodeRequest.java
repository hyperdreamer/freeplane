package org.freeplane.features.ai.code;

public class ReadCodeRequest {
    private String codeId;
    private ScriptHost host;
    private String fingerprint;

    public ReadCodeRequest() {
    }

    public ReadCodeRequest(String codeId, ScriptHost host, String fingerprint) {
        this.codeId = codeId;
        this.host = host;
        this.fingerprint = fingerprint;
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

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}

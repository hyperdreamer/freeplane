package org.freeplane.features.ai.code;

public class ReadCodeRequest {
    private ScriptHost host;
    private String fingerprint;

    public ReadCodeRequest() {
    }

    public ReadCodeRequest(ScriptHost host, String fingerprint) {
        this.host = host;
        this.fingerprint = fingerprint;
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

package org.freeplane.features.ai.code;

public class ReadCodeRequest {
    private ScriptHost host;
    private String knownStateFingerprint;

    public ReadCodeRequest() {
    }

    public ReadCodeRequest(ScriptHost host, String knownStateFingerprint) {
        this.host = host;
        this.knownStateFingerprint = knownStateFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }

    public String getKnownStateFingerprint() {
        return knownStateFingerprint;
    }

    public void setKnownStateFingerprint(String knownStateFingerprint) {
        this.knownStateFingerprint = knownStateFingerprint;
    }
}

package org.freeplane.features.ai.code;

public class ReadCodeRequest {
    private ScriptHost host;

    public ReadCodeRequest() {
    }

    public ReadCodeRequest(ScriptHost host) {
        this.host = host;
    }

    public ScriptHost getHost() {
        return host;
    }

    public void setHost(ScriptHost host) {
        this.host = host;
    }
}

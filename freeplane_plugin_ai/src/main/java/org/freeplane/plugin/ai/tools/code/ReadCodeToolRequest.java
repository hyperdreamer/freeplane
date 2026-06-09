package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class ReadCodeToolRequest {
    private final ScriptHost host;
    private final String knownStateFingerprint;

    @JsonCreator
    public ReadCodeToolRequest(@JsonProperty("host") ScriptHost host,
                               @JsonProperty("knownStateFingerprint") String knownStateFingerprint) {
        this.host = host;
        this.knownStateFingerprint = knownStateFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getKnownStateFingerprint() {
        return knownStateFingerprint;
    }
}

package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class ReadCodeToolRequest {
    private final ScriptHost host;
    private final String fingerprint;

    @JsonCreator
    public ReadCodeToolRequest(@JsonProperty("host") ScriptHost host,
                               @JsonProperty("fingerprint") String fingerprint) {
        this.host = host;
        this.fingerprint = fingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}

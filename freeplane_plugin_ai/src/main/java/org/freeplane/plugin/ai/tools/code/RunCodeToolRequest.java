package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class RunCodeToolRequest {
    private final ScriptHost host;
    private final String expectedFingerprint;

    @JsonCreator
    public RunCodeToolRequest(@JsonProperty("host") ScriptHost host,
                              @JsonProperty("expectedFingerprint") String expectedFingerprint) {
        this.host = host;
        this.expectedFingerprint = expectedFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }
}

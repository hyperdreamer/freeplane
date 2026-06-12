package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class ReadCodeToolRequest {
    private final ScriptHost host;

    @JsonCreator
    public ReadCodeToolRequest(@JsonProperty("host") ScriptHost host) {
        this.host = host;
    }

    public ScriptHost getHost() {
        return host;
    }
}

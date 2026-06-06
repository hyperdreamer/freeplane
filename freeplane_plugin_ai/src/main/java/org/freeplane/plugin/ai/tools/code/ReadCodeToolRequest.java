package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class ReadCodeToolRequest {
    private final String codeId;
    private final ScriptHost host;
    private final String fingerprint;

    @JsonCreator
    public ReadCodeToolRequest(@JsonProperty("codeId") String codeId,
                               @JsonProperty("host") ScriptHost host,
                               @JsonProperty("fingerprint") String fingerprint) {
        this.codeId = codeId;
        this.host = host;
        this.fingerprint = fingerprint;
    }

    public String getCodeId() {
        return codeId;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}

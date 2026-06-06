package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class CompileCodeToolRequest {
    private final String codeId;
    private final ScriptHost host;
    private final String expectedFingerprint;

    @JsonCreator
    public CompileCodeToolRequest(@JsonProperty("codeId") String codeId,
                                  @JsonProperty("host") ScriptHost host,
                                  @JsonProperty("expectedFingerprint") String expectedFingerprint) {
        this.codeId = codeId;
        this.host = host;
        this.expectedFingerprint = expectedFingerprint;
    }

    public String getCodeId() {
        return codeId;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }
}

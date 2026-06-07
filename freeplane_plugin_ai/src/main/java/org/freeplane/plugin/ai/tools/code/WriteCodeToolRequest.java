package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.ScriptHost;

public class WriteCodeToolRequest {
    private final ScriptHost host;
    private final String text;
    private final String expectedFingerprint;

    @JsonCreator
    public WriteCodeToolRequest(@JsonProperty("host") ScriptHost host,
                                @JsonProperty("text") String text,
                                @JsonProperty("expectedFingerprint") String expectedFingerprint) {
        this.host = host;
        this.text = text;
        this.expectedFingerprint = expectedFingerprint;
    }

    public ScriptHost getHost() {
        return host;
    }

    public String getText() {
        return text;
    }

    public String getExpectedFingerprint() {
        return expectedFingerprint;
    }
}

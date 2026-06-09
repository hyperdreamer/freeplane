package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.ScriptHost;

public class CompileCodeToolRequest {
    private final ScriptHost host;
    private final CodeStateToken expectedStateToken;

    @JsonCreator
    public CompileCodeToolRequest(@JsonProperty("host") ScriptHost host,
                                  @JsonProperty("expectedStateToken") CodeStateToken expectedStateToken) {
        this.host = host;
        this.expectedStateToken = expectedStateToken;
    }

    public ScriptHost getHost() {
        return host;
    }

    public CodeStateToken getExpectedStateToken() {
        return expectedStateToken;
    }
}

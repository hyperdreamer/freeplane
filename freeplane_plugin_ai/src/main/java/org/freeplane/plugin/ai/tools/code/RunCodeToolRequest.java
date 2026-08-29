package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.ScriptHost;

public class RunCodeToolRequest {
    @Description("Target code host: AI or ATTACHED_EDITOR.")
    private final ScriptHost host;
    @Description("State token from readCode.")
    private final CodeStateToken expectedStateToken;

    @JsonCreator
    public RunCodeToolRequest(@JsonProperty("host") ScriptHost host,
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

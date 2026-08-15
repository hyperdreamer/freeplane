package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.features.ai.code.ScriptHost;

public class ReadCodeToolRequest {
    @Description("Target code host: AI or ATTACHED_EDITOR.")
    private final ScriptHost host;

    @JsonCreator
    public ReadCodeToolRequest(@JsonProperty("host") ScriptHost host) {
        this.host = host;
    }

    public ScriptHost getHost() {
        return host;
    }
}

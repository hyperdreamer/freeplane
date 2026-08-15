package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.ScriptHost;

public class WriteCodeToolRequest {
    @Description("Target code host: AI or ATTACHED_EDITOR.")
    private final ScriptHost host;
    @Description("Source text and optional JSON arguments.")
    private final CodeStateContent content;
    @Description("State token from readCode; omit only for new AI-owned code.")
    private final CodeStateToken expectedStateToken;

    @JsonCreator
    public WriteCodeToolRequest(@JsonProperty("host") ScriptHost host,
                                @JsonProperty("content") CodeStateContent content,
                                @JsonProperty("expectedStateToken") CodeStateToken expectedStateToken) {
        this.host = host;
        this.content = content;
        this.expectedStateToken = expectedStateToken;
    }

    public ScriptHost getHost() {
        return host;
    }

    public CodeStateContent getContent() {
        return content;
    }

    public CodeStateToken getExpectedStateToken() {
        return expectedStateToken;
    }
}

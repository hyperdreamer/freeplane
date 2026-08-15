package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

public class WriteAndRunCodeToolRequest {
    @Description("Groovy script source and optional JSON arguments.")
    private final CodeStateContentPayload content;

    @JsonCreator
    public WriteAndRunCodeToolRequest(@JsonProperty("content") CodeStateContentPayload content) {
        this.content = content;
    }

    public CodeStateContentPayload getContent() {
        return content;
    }
}

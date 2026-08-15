package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.features.ai.code.CodeStateContent;

public class WriteAndRunCodeToolRequest {
    @Description("Script source and optional JSON arguments.")
    private final CodeStateContent content;

    @JsonCreator
    public WriteAndRunCodeToolRequest(@JsonProperty("content") CodeStateContent content) {
        this.content = content;
    }

    public CodeStateContent getContent() {
        return content;
    }
}

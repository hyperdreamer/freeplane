package org.freeplane.plugin.ai.tools.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.features.ai.code.CodeStateContent;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeStateContentPayload {
    @Description("Source text for the stored code or formula.")
    private final String sourceText;
    @JsonProperty(required = false)
    @Description("Optional JSON arguments.")
    private final String argumentsJsonText;

    @JsonCreator
    public CodeStateContentPayload(@JsonProperty(value = "sourceText", required = true) String sourceText,
                                   @JsonProperty(value = "argumentsJsonText", required = false)
                                   String argumentsJsonText) {
        this.sourceText = sourceText;
        this.argumentsJsonText = argumentsJsonText;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getArgumentsJsonText() {
        return argumentsJsonText;
    }

    public CodeStateContent toCodeStateContent() {
        return new CodeStateContent(sourceText, argumentsJsonText);
    }
}

package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableText {
    private final String raw;
    private final String transformed;
    private final String plain;
    private final ContentType contentType;
    private final Boolean isFormula;
    private final Boolean isEditable;

    @JsonCreator
    public EditableText(@JsonProperty("raw") String raw,
                        @JsonProperty("transformed") String transformed,
                        @JsonProperty("plain") String plain,
                        @JsonProperty("contentType") ContentType contentType,
                        @JsonProperty("isFormula") Boolean isFormula,
                        @JsonProperty("isEditable") Boolean isEditable) {
        this.raw = raw;
        this.transformed = transformed;
        this.plain = plain;
        this.contentType = contentType;
        this.isFormula = isFormula;
        this.isEditable = isEditable;
    }

    public String getRaw() {
        return raw;
    }

    public String getTransformed() {
        return transformed;
    }

    public String getPlain() {
        return plain;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public Boolean getIsFormula() {
        return isFormula;
    }

    public Boolean getIsEditable() {
        return isEditable;
    }
}

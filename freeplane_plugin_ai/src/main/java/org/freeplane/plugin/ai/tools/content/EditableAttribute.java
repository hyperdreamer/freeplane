package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableAttribute {
    private final String name;
    private final String rawValue;
    private final String transformedValue;
    private final String plainValue;
    private final Boolean isFormula;
    private final Boolean isEditable;
    private final Integer index;

    @JsonCreator
    public EditableAttribute(@JsonProperty("name") String name,
                             @JsonProperty("rawValue") String rawValue,
                             @JsonProperty("transformedValue") String transformedValue,
                             @JsonProperty("plainValue") String plainValue,
                             @JsonProperty("isFormula") Boolean isFormula,
                             @JsonProperty("isEditable") Boolean isEditable,
                             @JsonProperty("index") Integer index) {
        this.name = name;
        this.rawValue = rawValue;
        this.transformedValue = transformedValue;
        this.plainValue = plainValue;
        this.isFormula = isFormula;
        this.isEditable = isEditable;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getTransformedValue() {
        return transformedValue;
    }

    public String getPlainValue() {
        return plainValue;
    }

    public Boolean getIsFormula() {
        return isFormula;
    }

    public Boolean getIsEditable() {
        return isEditable;
    }

    public Integer getIndex() {
        return index;
    }
}

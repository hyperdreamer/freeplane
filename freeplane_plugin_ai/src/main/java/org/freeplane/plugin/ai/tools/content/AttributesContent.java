package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttributesContent {
    private final List<AttributeEntry> attributes;

    @JsonCreator
    public AttributesContent(@JsonProperty("attributes") List<AttributeEntry> attributes) {
        this.attributes = attributes;
    }

    public List<AttributeEntry> getAttributes() {
        return attributes;
    }
}

package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AttributeUpdate {
    private final String nodeIdentifier;
    private final List<AttributeEntry> attributes;

    @JsonCreator
    public AttributeUpdate(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                           @JsonProperty("attributes") List<AttributeEntry> attributes) {
        this.nodeIdentifier = nodeIdentifier;
        this.attributes = attributes;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public List<AttributeEntry> getAttributes() {
        return attributes;
    }
}

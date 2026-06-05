package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListResponse {
    private final String scope;
    private final String mapIdentifier;
    private final List<String> values;
    private final String note;

    @JsonCreator
    public ListResponse(@JsonProperty("scope") String scope,
                                 @JsonProperty("mapIdentifier") String mapIdentifier,
                                 @JsonProperty("values") List<String> values,
                                 @JsonProperty("note") String note) {
        this.scope = scope;
        this.mapIdentifier = mapIdentifier;
        this.values = values;
        this.note = note;
    }

    public String getScope() {
        return scope;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<String> getValues() {
        return values;
    }

    public String getNote() {
        return note;
    }
}

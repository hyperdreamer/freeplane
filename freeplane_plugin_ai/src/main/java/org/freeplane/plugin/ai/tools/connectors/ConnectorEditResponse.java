package org.freeplane.plugin.ai.tools.connectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorEditResponse {
    private final String mapIdentifier;
    private final List<ConnectorEditResultItem> items;

    @JsonCreator
    public ConnectorEditResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                 @JsonProperty("items") List<ConnectorEditResultItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<ConnectorEditResultItem> getItems() {
        return items;
    }
}

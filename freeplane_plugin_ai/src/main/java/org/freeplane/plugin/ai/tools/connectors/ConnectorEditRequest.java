package org.freeplane.plugin.ai.tools.connectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.Collections;
import java.util.List;

public class ConnectorEditRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @JsonProperty(required = false)
    private final List<ConnectorEditRequestItem> items;

    @JsonCreator
    public ConnectorEditRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                @JsonProperty("items") List<ConnectorEditRequestItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<ConnectorEditRequestItem> getItems() {
        return items;
    }
}

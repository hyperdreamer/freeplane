package org.freeplane.plugin.ai.tools.create;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CreateNodesRequest {
    private final String mapIdentifier;
    private final String userSummary;
    private final AnchorPlacement anchorPlacement;
    private final List<NodeCreationItem> nodes;

    @JsonCreator
    public CreateNodesRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                              @JsonProperty("userSummary") String userSummary,
                              @JsonProperty("anchorPlacement") AnchorPlacement anchorPlacement,
                              @JsonProperty("nodes") List<NodeCreationItem> nodes) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.anchorPlacement = anchorPlacement;
        this.nodes = nodes;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public AnchorPlacement getAnchorPlacement() {
        return anchorPlacement;
    }

    public List<NodeCreationItem> getNodes() {
        return nodes;
    }
}

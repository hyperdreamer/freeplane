package org.freeplane.plugin.ai.tools.move;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.create.AnchorPlacement;

public class MoveNodesRequest {
    private final String mapIdentifier;
    private final String userSummary;
    private final AnchorPlacement anchorPlacement;
    private final List<String> nodeIdentifiers;

    @JsonCreator
    public MoveNodesRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                            @JsonProperty("userSummary") String userSummary,
                            @JsonProperty("anchorPlacement") AnchorPlacement anchorPlacement,
                            @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.anchorPlacement = anchorPlacement;
        this.nodeIdentifiers = nodeIdentifiers;
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

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }
}

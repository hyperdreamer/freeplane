package org.freeplane.plugin.ai.tools.create;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;

public class CreateNodesResponse {
    private final String mapIdentifier;
    private final String userSummary;
    private final List<ModifiedNodeSummary> modifiedNodes;

    @JsonCreator
    public CreateNodesResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                               @JsonProperty("userSummary") String userSummary,
                               @JsonProperty("modifiedNodes") List<ModifiedNodeSummary> modifiedNodes) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.modifiedNodes = modifiedNodes;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public List<ModifiedNodeSummary> getModifiedNodes() {
        return modifiedNodes;
    }
}

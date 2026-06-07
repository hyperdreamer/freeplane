package org.freeplane.plugin.ai.tools.delete;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public class DeleteNodesRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Node IDs to delete.")
    private final List<String> nodeIdentifiers;
    @Description("Short summary for confirmations.")
    private final String userSummary;

    @JsonCreator
    public DeleteNodesRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                              @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers,
                              @JsonProperty("userSummary") String userSummary) {
        this.mapIdentifier = mapIdentifier;
        this.nodeIdentifiers = nodeIdentifiers;
        this.userSummary = userSummary;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }

    public String getUserSummary() {
        return userSummary;
    }
}

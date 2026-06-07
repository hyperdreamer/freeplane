package org.freeplane.plugin.ai.tools.move;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public class MoveNodesIntoSummaryRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Short summary for confirmations.")
    private final String userSummary;
    @Description("Summary anchor placement (first/last summarized siblings).")
    private final SummaryAnchorPlacement summaryAnchorPlacement;
    @Description("Ordered node IDs to move into summary content (non-empty).")
    private final List<String> nodeIdentifiers;

    @JsonCreator
    public MoveNodesIntoSummaryRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                       @JsonProperty("userSummary") String userSummary,
                                       @JsonProperty("summaryAnchorPlacement") SummaryAnchorPlacement summaryAnchorPlacement,
                                       @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.summaryAnchorPlacement = summaryAnchorPlacement;
        this.nodeIdentifiers = nodeIdentifiers;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public SummaryAnchorPlacement getSummaryAnchorPlacement() {
        return summaryAnchorPlacement;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }
}

package org.freeplane.plugin.ai.tools.move;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.create.NodeCreationItem;

public class CreateSummaryRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Short summary for confirmations.")
    private final String userSummary;
    @Description("Summary anchor placement (first/last summarized siblings).")
    private final SummaryAnchorPlacement summaryAnchorPlacement;
    @Description("Summary content nodes (non-empty).")
    private final List<NodeCreationItem> nodes;

    @JsonCreator
    public CreateSummaryRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                @JsonProperty("userSummary") String userSummary,
                                @JsonProperty("summaryAnchorPlacement") SummaryAnchorPlacement summaryAnchorPlacement,
                                @JsonProperty("nodes") List<NodeCreationItem> nodes) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.summaryAnchorPlacement = summaryAnchorPlacement;
        this.nodes = nodes;
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

    public List<NodeCreationItem> getNodes() {
        return nodes;
    }
}

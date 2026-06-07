package org.freeplane.plugin.ai.tools.selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public class SelectionIdentifiersResponse {
    @Description("Selected map ID.")
    private final String mapIdentifier;
    @Description("Primary selected node ID.")
    private final String nodeIdentifier;
    @Description("Root node ID for the selected map.")
    private final String rootNodeIdentifier;
    @Description("Selected nodes with short previews.")
    private final List<SelectedNodeSummary> selectedNodes;
    @Description("Total selected node count.")
    private final int selectedNodeCount;
    @Description("Total unique selected subtree count.")
    private final int selectedUniqueSubtreeCount;

    @JsonCreator
    public SelectionIdentifiersResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                        @JsonProperty("nodeIdentifier") String nodeIdentifier,
                                        @JsonProperty("rootNodeIdentifier") String rootNodeIdentifier,
                                        @JsonProperty("selectedNodes") List<SelectedNodeSummary> selectedNodes,
                                        @JsonProperty("selectedNodeCount") int selectedNodeCount,
                                        @JsonProperty("selectedUniqueSubtreeCount") int selectedUniqueSubtreeCount) {
        this.mapIdentifier = mapIdentifier;
        this.nodeIdentifier = nodeIdentifier;
        this.rootNodeIdentifier = rootNodeIdentifier;
        this.selectedNodes = selectedNodes;
        this.selectedNodeCount = selectedNodeCount;
        this.selectedUniqueSubtreeCount = selectedUniqueSubtreeCount;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public String getRootNodeIdentifier() {
        return rootNodeIdentifier;
    }

    public List<SelectedNodeSummary> getSelectedNodes() {
        return selectedNodes;
    }

    public int getSelectedNodeCount() {
        return selectedNodeCount;
    }

    public int getSelectedUniqueSubtreeCount() {
        return selectedUniqueSubtreeCount;
    }
}

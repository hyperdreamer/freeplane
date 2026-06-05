package org.freeplane.plugin.ai.tools.delete;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;

public class DeleteNodesResponse {
    @Description("Map ID.")
    private final String mapIdentifier;
    @Description("Short summary for confirmations.")
    private final String userSummary;
    @Description("Deleted subtree root summaries.")
    private final List<ModifiedNodeSummary> deletedNodes;
    @Description("Total deleted node count.")
    private final int deletedNodeCount;
    @Description("Total deleted subtree root count.")
    private final int deletedSubtreeRootCount;

    @JsonCreator
    public DeleteNodesResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                               @JsonProperty("userSummary") String userSummary,
                               @JsonProperty("deletedNodes") List<ModifiedNodeSummary> deletedNodes,
                               @JsonProperty("deletedNodeCount") int deletedNodeCount,
                               @JsonProperty("deletedSubtreeRootCount") int deletedSubtreeRootCount) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.deletedNodes = deletedNodes;
        this.deletedNodeCount = deletedNodeCount;
        this.deletedSubtreeRootCount = deletedSubtreeRootCount;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public List<ModifiedNodeSummary> getDeletedNodes() {
        return deletedNodes;
    }

    public int getDeletedNodeCount() {
        return deletedNodeCount;
    }

    public int getDeletedSubtreeRootCount() {
        return deletedSubtreeRootCount;
    }
}

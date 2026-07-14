package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;

public class ModifiedNodeSummaryBuilder {
    private final NodeTextPreviewFormatter nodeTextPreviewFormatter;

    public ModifiedNodeSummaryBuilder(NodeTextPreviewFormatter nodeTextPreviewFormatter) {
        this.nodeTextPreviewFormatter = Objects.requireNonNull(nodeTextPreviewFormatter, "nodeTextPreviewFormatter");
    }

    public List<ModifiedNodeSummary> buildSummaries(List<NodeModel> nodes, boolean includeDescendants) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<ModifiedNodeSummary> summaries = new ArrayList<>();
        for (NodeModel node : nodes) {
            if (includeDescendants) {
                addNodeWithDescendants(node, summaries);
            } else {
                addNode(node, summaries);
            }
        }
        return summaries;
    }

    private void addNodeWithDescendants(NodeModel node, List<ModifiedNodeSummary> summaries) {
        addNode(node, summaries);
        for (int index = 0; index < node.getChildCount(); index++) {
            addNodeWithDescendants(node.getChildAt(index), summaries);
        }
    }

    private void addNode(NodeModel node, List<ModifiedNodeSummary> summaries) {
        String nodeIdentifier = node.createID();
        String shortText = nodeTextPreviewFormatter.modifiedNodeSummaryText(node);
        summaries.add(new ModifiedNodeSummary(nodeIdentifier, shortText));
    }
}

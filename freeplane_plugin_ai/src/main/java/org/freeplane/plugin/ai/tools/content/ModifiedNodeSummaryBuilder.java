package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public class ModifiedNodeSummaryBuilder {
    private static final int DEFAULT_MAXIMUM_TEXT_CHARACTERS = 20;
    private static final String DEFAULT_CONTINUATION_MARK = " ...";

    private final TextController textController;
    private final int maximumTextCharacters;

    public ModifiedNodeSummaryBuilder(TextController textController) {
        this(textController, DEFAULT_MAXIMUM_TEXT_CHARACTERS);
    }

    ModifiedNodeSummaryBuilder(TextController textController, int maximumTextCharacters) {
        this.textController = Objects.requireNonNull(textController, "textController");
        this.maximumTextCharacters = maximumTextCharacters;
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
        String shortText = textController.getShortPlainText(node, maximumTextCharacters, DEFAULT_CONTINUATION_MARK);
        summaries.add(new ModifiedNodeSummary(nodeIdentifier, shortText));
    }
}

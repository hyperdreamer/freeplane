package org.freeplane.plugin.ai.tools.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public class SelectionIdentifiersBuilder {
    private static final int DEFAULT_MAXIMUM_TEXT_CHARACTERS = 20;
    private static final String DEFAULT_CONTINUATION_MARK = " ...";

    private final TextController textController;

    public SelectionIdentifiersBuilder(TextController textController) {
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    public SelectionIdentifiersResponse buildSelectionIdentifiersResponse(String mapIdentifierValue,
                                                                          MapModel mapModel,
                                                                          IMapSelection selection,
                                                                          SelectionCollectionMode selectionCollectionMode) {
        String rootNodeIdentifier = mapModel == null || mapModel.getRootNode() == null
            ? null
            : mapModel.getRootNode().getID();
        NodeModel primarySelectedNode = selection == null ? null : selection.getSelected();
        String primaryNodeIdentifier = primarySelectedNode == null ? null : primarySelectedNode.getID();
        List<NodeModel> selectedNodes = getSelectedNodes(selection, selectionCollectionMode);
        List<SelectedNodeSummary> selectedNodeSummaries = buildSelectedNodeSummaries(selectedNodes);
        int selectedNodeCount = selection == null ? 0 : selection.getOrderedSelection().size();
        int selectedUniqueSubtreeCount = selection == null ? 0 : selection.getSortedSelection(true).size();
        return new SelectionIdentifiersResponse(mapIdentifierValue, primaryNodeIdentifier, rootNodeIdentifier,
            selectedNodeSummaries, selectedNodeCount, selectedUniqueSubtreeCount);
    }

    private List<NodeModel> getSelectedNodes(IMapSelection selection, SelectionCollectionMode selectionCollectionMode) {
        if (selection == null) {
            return Collections.emptyList();
        }
        SelectionCollectionMode resolvedSelectionCollectionMode = selectionCollectionMode == null
            ? SelectionCollectionMode.ORDERED
            : selectionCollectionMode;
        switch (resolvedSelectionCollectionMode) {
            case SINGLE:
                NodeModel selectedNode = selection.getSelected();
                if (selectedNode == null) {
                    return Collections.emptyList();
                }
                List<NodeModel> selectedNodes = new ArrayList<>();
                selectedNodes.add(selectedNode);
                return selectedNodes;
            case SORTED:
                return selection.getSortedSelection(false);
            case SORTED_UNIQUE_SUBTREES:
                return selection.getSortedSelection(true);
            case ORDERED:
            default:
                return selection.getOrderedSelection();
        }
    }

    private List<SelectedNodeSummary> buildSelectedNodeSummaries(List<NodeModel> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<SelectedNodeSummary> selectedNodeSummaries = new ArrayList<>();
        for (NodeModel node : selectedNodes) {
            String nodeIdentifier = node.getID();
            if (nodeIdentifier == null) {
                nodeIdentifier = node.createID();
            }
            String shortText = textController.getShortPlainText(
                node, DEFAULT_MAXIMUM_TEXT_CHARACTERS, DEFAULT_CONTINUATION_MARK);
            selectedNodeSummaries.add(new SelectedNodeSummary(nodeIdentifier, shortText));
        }
        return selectedNodeSummaries;
    }
}

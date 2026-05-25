package org.freeplane.plugin.ai.chat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.api.MindMap;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.selection.SelectedNodeSummary;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;

class AiSelectionOverrideResolver {
    private static final int DEFAULT_MAXIMUM_TEXT_CHARACTERS = 20;
    private static final String DEFAULT_CONTINUATION_MARK = " ...";

    private final AvailableMaps availableMaps;
    private final TextController textController;

    AiSelectionOverrideResolver(AvailableMaps availableMaps, TextController textController) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    SelectionIdentifiersResponse resolve(AiSelectionOverride selectionOverride) {
        if (selectionOverride == null) {
            return null;
        }
        MapModel mapModel = resolveMapModel(selectionOverride.getMindMap());
        UUID mapIdentifier = availableMaps.getOrCreateMapIdentifier(mapModel);
        List<NodeModel> selectedNodes = resolveSelectedNodes(mapModel, selectionOverride.getSelectedNodeIds());
        String primaryNodeIdentifier = selectedNodes.isEmpty() ? null : selectedNodes.get(0).getID();
        String rootNodeIdentifier = mapModel.getRootNode() == null ? null : mapModel.getRootNode().getID();
        return new SelectionIdentifiersResponse(
            mapIdentifier == null ? null : mapIdentifier.toString(),
            primaryNodeIdentifier,
            rootNodeIdentifier,
            buildSelectedNodeSummaries(selectedNodes),
            selectedNodes.size(),
            uniqueSubtreeCount(selectedNodes));
    }

    private MapModel resolveMapModel(MindMap mindMap) {
        File expectedFile = mindMap == null ? null : mindMap.getFile();
        String expectedRootId = mindMap == null || mindMap.getRoot() == null ? null : mindMap.getRoot().getId();
        for (UUID mapIdentifier : availableMaps.getAvailableMapIdentifiers()) {
            MapModel mapModel = availableMaps.findMapModel(mapIdentifier);
            if (mapModel == null) {
                continue;
            }
            if (matchesFile(expectedFile, mapModel.getFile()) || matchesRootId(expectedRootId, mapModel)) {
                return mapModel;
            }
        }
        throw new IllegalStateException("Selection override map is not available.");
    }

    private boolean matchesFile(File expectedFile, File actualFile) {
        return expectedFile != null && actualFile != null && expectedFile.equals(actualFile);
    }

    private boolean matchesRootId(String expectedRootId, MapModel mapModel) {
        return expectedRootId != null && mapModel.getRootNode() != null
            && expectedRootId.equals(mapModel.getRootNode().getID());
    }

    private List<NodeModel> resolveSelectedNodes(MapModel mapModel, List<String> selectedNodeIds) {
        ArrayList<NodeModel> selectedNodes = new ArrayList<NodeModel>();
        for (String nodeId : selectedNodeIds) {
            NodeModel node = mapModel.getNodeForID(nodeId);
            if (node == null) {
                throw new IllegalStateException("Selection override node is not available: " + nodeId);
            }
            selectedNodes.add(node);
        }
        return selectedNodes;
    }

    private List<SelectedNodeSummary> buildSelectedNodeSummaries(List<NodeModel> selectedNodes) {
        ArrayList<SelectedNodeSummary> selectedNodeSummaries = new ArrayList<SelectedNodeSummary>(selectedNodes.size());
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

    private int uniqueSubtreeCount(List<NodeModel> selectedNodes) {
        int uniqueSubtreeCount = 0;
        for (NodeModel node : selectedNodes) {
            if (!isDescendantOfAnyOther(node, selectedNodes)) {
                uniqueSubtreeCount++;
            }
        }
        return uniqueSubtreeCount;
    }

    private boolean isDescendantOfAnyOther(NodeModel node, List<NodeModel> selectedNodes) {
        for (NodeModel other : selectedNodes) {
            if (node != other && node.isDescendantOf(other)) {
                return true;
            }
        }
        return false;
    }
}

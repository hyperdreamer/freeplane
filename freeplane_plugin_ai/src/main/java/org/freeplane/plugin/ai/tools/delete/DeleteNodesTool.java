package org.freeplane.plugin.ai.tools.delete;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeRelativePath;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class DeleteNodesTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final MMapController mapController;
    private final ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder;

    public DeleteNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                           MMapController mapController,
                           ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.modifiedNodeSummaryBuilder = Objects.requireNonNull(
            modifiedNodeSummaryBuilder, "modifiedNodeSummaryBuilder");
    }

    public DeleteNodesResponse deleteNodes(DeleteNodesRequest request) {
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        String userSummary = requireValue(request.getUserSummary(), "userSummary");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<String> nodeIdentifiers = requireIdentifiers(request.getNodeIdentifiers());
        List<NodeModel> requestedNodes = resolveNodes(mapModel, nodeIdentifiers);
        List<NodeModel> uniqueSubtreeRoots = filterUniqueSubtreeRoots(requestedNodes);
        uniqueSubtreeRoots = sortByMapOrder(uniqueSubtreeRoots);
        int deletedNodeCount = countDeletedNodes(uniqueSubtreeRoots);
        int deletedSubtreeRootCount = uniqueSubtreeRoots.size();
        List<ModifiedNodeSummary> deletedNodes = modifiedNodeSummaryBuilder.buildSummaries(uniqueSubtreeRoots, false);
        mapController.deleteNodes(uniqueSubtreeRoots);
        return new DeleteNodesResponse(mapIdentifierValue, userSummary, deletedNodes,
            deletedNodeCount, deletedSubtreeRootCount);
    }

    public ToolCallSummary buildToolCallSummary(DeleteNodesRequest request, DeleteNodesResponse response) {
        return new ToolCallSummary("deleteNodes",
            "deleteNodes: " + request.getUserSummary(), false);
    }

    public ToolCallSummary buildToolCallErrorSummary(DeleteNodesRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("deleteNodes", "deleteNodes error: " + safeMessage, true);
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + " value.");
        }
        return value.trim();
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier, error);
        }
    }

    private List<String> requireIdentifiers(List<String> nodeIdentifiers) {
        if (nodeIdentifiers == null || nodeIdentifiers.isEmpty()) {
            throw new IllegalArgumentException("Missing node identifiers.");
        }
        return nodeIdentifiers;
    }

    private List<NodeModel> resolveNodes(MapModel mapModel, List<String> nodeIdentifiers) {
        List<NodeModel> nodes = new ArrayList<>();
        for (String nodeIdentifier : nodeIdentifiers) {
            if (nodeIdentifier == null || nodeIdentifier.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing node identifier.");
            }
            NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier.trim());
            if (nodeModel == null) {
                throw new IllegalArgumentException("Unknown node identifier: " + nodeIdentifier);
            }
            if (nodeModel.getParentNode() == null) {
                throw new IllegalArgumentException("Root node deletion is not supported.");
            }
            nodes.add(nodeModel);
        }
        return nodes;
    }

    private List<NodeModel> filterUniqueSubtreeRoots(List<NodeModel> requestedNodes) {
        Set<NodeModel> requestedNodesSet = new HashSet<>(requestedNodes);
        List<NodeModel> uniqueRoots = new ArrayList<>();
        for (NodeModel node : requestedNodes) {
            if (!hasAncestorInSet(node, requestedNodesSet)) {
                uniqueRoots.add(node);
            }
        }
        return uniqueRoots;
    }

    private List<NodeModel> sortByMapOrder(List<NodeModel> nodes) {
        TreeSet<NodeModel> sortedNodes = new TreeSet<>(NodeRelativePath.comparator());
        sortedNodes.addAll(nodes);
        return new ArrayList<>(sortedNodes);
    }

    private boolean hasAncestorInSet(NodeModel node, Set<NodeModel> requestedNodes) {
        for (NodeModel parent = node.getParentNode(); parent != null; parent = parent.getParentNode()) {
            if (requestedNodes.contains(parent)) {
                return true;
            }
        }
        return false;
    }

    private int countDeletedNodes(List<NodeModel> uniqueSubtreeRoots) {
        int count = 0;
        for (NodeModel node : uniqueSubtreeRoots) {
            count += countSubtreeNodes(node);
        }
        return count;
    }

    private int countSubtreeNodes(NodeModel node) {
        int count = 1;
        for (int index = 0; index < node.getChildCount(); index++) {
            count += countSubtreeNodes(node.getChildAt(index));
        }
        return count;
    }
}

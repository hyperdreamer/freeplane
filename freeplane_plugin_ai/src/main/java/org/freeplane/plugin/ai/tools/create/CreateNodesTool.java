package org.freeplane.plugin.ai.tools.create;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolErrorHandler;

public class CreateNodesTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder;
    private final NodeInserter nodeInserter;
    private final ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder;
    private final MapController mapController;
    private final CreateNodesPreferences createNodesPreferences;

    public CreateNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                           NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder,
                           NodeInserter nodeInserter, ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder,
                           MapController mapController, CreateNodesPreferences createNodesPreferences) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeCreationHierarchyBuilder = Objects.requireNonNull(nodeCreationHierarchyBuilder,
            "nodeCreationHierarchyBuilder");
        this.nodeInserter = Objects.requireNonNull(nodeInserter, "nodeInserter");
        this.modifiedNodeSummaryBuilder = Objects.requireNonNull(modifiedNodeSummaryBuilder, "modifiedNodeSummaryBuilder");
        this.mapController = mapController;
        this.createNodesPreferences = Objects.requireNonNull(createNodesPreferences, "createNodesPreferences");
    }

    public CreateNodesResponse createNodes(CreateNodesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        String userSummary = requireValue(request.getUserSummary(), "userSummary");
        AnchorPlacement anchorPlacement = requireValue(request.getAnchorPlacement(), "anchorPlacement");
        String anchorNodeIdentifier = requireValue(anchorPlacement.getAnchorNodeIdentifier(), "anchorNodeIdentifier");
        AnchorPlacementMode placementMode = requireValue(anchorPlacement.getPlacementMode(), "placementMode");
        NodeModel anchorNode = mapModel.getNodeForID(anchorNodeIdentifier);
        if (anchorNode == null) {
            throw new IllegalArgumentException("Invalid anchor node identifier: " + anchorNodeIdentifier);
        }
        List<NodeCreationItem> nodes = requireNodes(request.getNodes());
        NodeCreationHierarchy hierarchy = nodeCreationHierarchyBuilder.buildHierarchy(nodes, mapModel);
        NodeModel parentNode = resolveParentNode(anchorNode, placementMode);
        List<NodeModel> insertedNodes = nodeInserter.insertNodes(
            hierarchy.getRootNodes(), anchorNode, placementMode, new ToolErrorHandler("Create failure: "));
        applyFoldingPreferences(parentNode, hierarchy.getFoldingStates());
        List<ModifiedNodeSummary> modifiedNodes = modifiedNodeSummaryBuilder.buildSummaries(insertedNodes, true);
        return new CreateNodesResponse(mapIdentifierValue, userSummary, modifiedNodes);
    }

    public ToolCallSummary buildToolCallSummary(CreateNodesRequest request, CreateNodesResponse response) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getNodes() == null ? 0 : request.getNodes().size();
        String summaryText = "createNodes: items=" + itemCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        return new ToolCallSummary("createNodes", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(CreateNodesRequest request, RuntimeException error) {
        String summaryText = "createNodes error: " + error.getMessage();
        return new ToolCallSummary("createNodes", summaryText, true);
    }

    private List<NodeCreationItem> requireNodes(List<NodeCreationItem> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Nodes list must be non-empty.");
        }
        return nodes;
    }

    private <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value;
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier);
        }
    }

    private NodeModel resolveParentNode(NodeModel anchorNode, AnchorPlacementMode placementMode) {
        AnchorPlacementCalculator calculator = new AnchorPlacementCalculator();
        return calculator.calculatePlacement(anchorNode, placementMode).getParentNode();
    }

    private void applyFoldingPreferences(NodeModel parentNode, Map<NodeModel, NodeFoldingState> foldingStates) {
        boolean unfoldsParent = createNodesPreferences.unfoldsParentsOnCreate();
        if (unfoldsParent && parentNode != null && parentNode.isFolded()) {
            if (mapController != null) {
                mapController.unfold(parentNode, null);
            } else {
                parentNode.setFolded(false);
            }
        }
        if (foldingStates == null || foldingStates.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<NodeModel, NodeFoldingState> entry : foldingStates.entrySet()) {
            NodeModel nodeModel = entry.getKey();
            if (nodeModel == null || nodeModel.getChildCount() == 0) {
                continue;
            }
            boolean shouldFold = entry.getValue() == NodeFoldingState.FOLD;
            if (mapController != null) {
                mapController.setFolded(nodeModel, shouldFold, null);
            } else {
                nodeModel.setFolded(shouldFold);
            }
        }
    }
}

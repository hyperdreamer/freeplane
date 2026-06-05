package org.freeplane.plugin.ai.tools.move;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.create.AnchorPlacement;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementCalculator;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementMode;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementResult;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolErrorHandler;

public class MoveNodesTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final MMapController mapController;
    private final AnchorPlacementCalculator anchorPlacementCalculator;

    public MoveNodesTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                         MMapController mapController,
                         AnchorPlacementCalculator anchorPlacementCalculator) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.anchorPlacementCalculator = Objects.requireNonNull(anchorPlacementCalculator, "anchorPlacementCalculator");
    }

    public MoveNodesResponse moveNodes(MoveNodesRequest request) {
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
        List<String> nodeIdentifiers = requireNodes(request.getNodeIdentifiers());
        List<NodeModel> nodesToMove = resolveNodes(mapModel, nodeIdentifiers);
        AnchorPlacementResult placement = anchorPlacementCalculator.calculatePlacement(anchorNode, placementMode);
        NodeModel parentNode = placement.getParentNode();
        int insertionIndex = placement.getInsertionIndex();
        mapController.moveNodes(nodesToMove, parentNode, insertionIndex,
            new ToolErrorHandler("Move failure: "));
        return new MoveNodesResponse(mapIdentifierValue, userSummary, parentNode.createID(), insertionIndex);
    }


    public ToolCallSummary buildToolCallSummary(MoveNodesRequest request, MoveNodesResponse response) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getNodeIdentifiers() == null ? 0 : request.getNodeIdentifiers().size();
        String summaryText = "moveNodes: items=" + itemCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        return new ToolCallSummary("moveNodes", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(MoveNodesRequest request, RuntimeException error) {
        String summaryText = "moveNodes error: " + error.getMessage();
        return new ToolCallSummary("moveNodes", summaryText, true);
    }

    private List<String> requireNodes(List<String> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Node identifiers list must be non-empty.");
        }
        return nodes;
    }

    private List<NodeModel> resolveNodes(MapModel mapModel, List<String> nodeIdentifiers) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String nodeIdentifier : nodeIdentifiers) {
            if (!seen.add(nodeIdentifier)) {
                duplicates.add(nodeIdentifier);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate node identifiers");
        }
        List<String> missingIdentifiers = new ArrayList<>();
        List<NodeModel> nodes = new ArrayList<>(nodeIdentifiers.size());
        for (String nodeIdentifier : nodeIdentifiers) {
            NodeModel node = mapModel.getNodeForID(nodeIdentifier);
            if (node == null) {
                missingIdentifiers.add(nodeIdentifier);
            } else {
                nodes.add(node);
            }
        }
        if (!missingIdentifiers.isEmpty()) {
            throw new IllegalArgumentException("Invalid node identifiers: " + missingIdentifiers);
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
}

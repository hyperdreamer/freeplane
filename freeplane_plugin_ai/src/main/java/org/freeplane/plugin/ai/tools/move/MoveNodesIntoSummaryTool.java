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
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolErrorHandler;

public class MoveNodesIntoSummaryTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final MMapController mapController;
    private final SummaryNodeCreator summaryNodeCreator;

    public MoveNodesIntoSummaryTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                                    MMapController mapController,
                                    SummaryNodeCreator summaryNodeCreator) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.summaryNodeCreator = Objects.requireNonNull(summaryNodeCreator, "summaryNodeCreator");
    }

    public MoveNodesIntoSummaryResponse moveNodesIntoSummary(MoveNodesIntoSummaryRequest request) {
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
        SummaryAnchorPlacement summaryAnchorPlacement = requireValue(request.getSummaryAnchorPlacement(),
            "summaryAnchorPlacement");
        NodeModel firstNode = resolveNode(mapModel, summaryAnchorPlacement.getFirstNodeIdentifier(),
            "firstNodeIdentifier");
        NodeModel lastNode = resolveNode(mapModel, summaryAnchorPlacement.getLastNodeIdentifier(),
            "lastNodeIdentifier");
        List<String> nodeIdentifiers = requireNodes(request.getNodeIdentifiers());
        List<NodeModel> nodesToMove = resolveNodes(mapModel, nodeIdentifiers);
        NodeModel summaryNode = summaryNodeCreator.createSummaryNode(mapModel.getRootNode(), firstNode, lastNode);
        mapController.moveNodes(nodesToMove, summaryNode, 0,
            new ToolErrorHandler("Move failure: "));
        String summaryNodeIdentifier = summaryNode.createID();
        return new MoveNodesIntoSummaryResponse(mapIdentifierValue, userSummary, summaryNodeIdentifier, 0,
            summaryNodeIdentifier);
    }


    public ToolCallSummary buildToolCallSummary(MoveNodesIntoSummaryRequest request, MoveNodesIntoSummaryResponse response) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getNodeIdentifiers() == null ? 0 : request.getNodeIdentifiers().size();
        String summaryText = "moveNodesIntoSummary: items=" + itemCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        return new ToolCallSummary("moveNodesIntoSummary", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(MoveNodesIntoSummaryRequest request, RuntimeException error) {
        String summaryText = "moveNodesIntoSummary error: " + error.getMessage();
        return new ToolCallSummary("moveNodesIntoSummary", summaryText, true);
    }

    private NodeModel resolveNode(MapModel mapModel, String nodeIdentifier, String fieldName) {
        String value = requireValue(nodeIdentifier, fieldName);
        NodeModel node = mapModel.getNodeForID(value);
        if (node == null) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
        return node;
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

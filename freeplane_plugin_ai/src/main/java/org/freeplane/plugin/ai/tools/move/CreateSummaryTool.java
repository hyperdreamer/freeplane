package org.freeplane.plugin.ai.tools.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementMode;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchy;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchyBuilder;
import org.freeplane.plugin.ai.tools.create.NodeCreationItem;
import org.freeplane.plugin.ai.tools.create.NodeInserter;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolErrorHandler;

public class CreateSummaryTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder;
    private final NodeInserter nodeInserter;
    private final SummaryNodeCreator summaryNodeCreator;
    private final ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder;

    public CreateSummaryTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                             NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder,
                             NodeInserter nodeInserter, SummaryNodeCreator summaryNodeCreator,
                             ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeCreationHierarchyBuilder = Objects.requireNonNull(nodeCreationHierarchyBuilder,
            "nodeCreationHierarchyBuilder");
        this.nodeInserter = Objects.requireNonNull(nodeInserter, "nodeInserter");
        this.summaryNodeCreator = Objects.requireNonNull(summaryNodeCreator, "summaryNodeCreator");
        this.modifiedNodeSummaryBuilder = Objects.requireNonNull(modifiedNodeSummaryBuilder, "modifiedNodeSummaryBuilder");
    }

    public CreateSummaryResponse createSummary(CreateSummaryRequest request) {
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
        List<NodeCreationItem> nodes = requireNodes(request.getNodes());
        NodeModel rootNode = mapModel.getRootNode();
        NodeModel summaryNode = summaryNodeCreator.createSummaryNode(rootNode, firstNode, lastNode);
        NodeCreationHierarchy hierarchy = nodeCreationHierarchyBuilder.buildHierarchy(nodes, mapModel);
        nodeInserter.insertNodes(
            hierarchy.getRootNodes(), summaryNode, AnchorPlacementMode.LAST_CHILD,
            new ToolErrorHandler("Summary creation failure: "));
        List<ModifiedNodeSummary> modifiedNodes = new ArrayList<>();
        modifiedNodes.addAll(modifiedNodeSummaryBuilder.buildSummaries(Collections.singletonList(summaryNode), false));
        modifiedNodes.addAll(modifiedNodeSummaryBuilder.buildSummaries(hierarchy.getRootNodes(), true));
        String summaryNodeIdentifier = summaryNode.createID();
        return new CreateSummaryResponse(mapIdentifierValue, userSummary, modifiedNodes, summaryNodeIdentifier);
    }

    public ToolCallSummary buildToolCallSummary(CreateSummaryRequest request, CreateSummaryResponse response) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getNodes() == null ? 0 : request.getNodes().size();
        String summaryText = "createSummary: items=" + itemCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        return new ToolCallSummary("createSummary", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(CreateSummaryRequest request, RuntimeException error) {
        String summaryText = "createSummary error: " + error.getMessage();
        return new ToolCallSummary("createSummary", summaryText, true);
    }

    private NodeModel resolveNode(MapModel mapModel, String nodeIdentifier, String fieldName) {
        String value = requireValue(nodeIdentifier, fieldName);
        NodeModel node = mapModel.getNodeForID(value);
        if (node == null) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
        return node;
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
}

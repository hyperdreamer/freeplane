package org.freeplane.plugin.ai.tools.connectors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ConnectorItem;
import org.freeplane.plugin.ai.tools.edit.AiEditsMarker;
import org.freeplane.plugin.ai.tools.edit.EditOperation;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class ConnectorEditTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final MLinkController linkController;
    private final AiEditsMarker aiEditsMarker;

    public ConnectorEditTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                             MLinkController linkController) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.linkController = Objects.requireNonNull(linkController, "linkController");
        this.aiEditsMarker = new AiEditsMarker();
    }

    public ConnectorEditResponse editConnectors(ConnectorEditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<ConnectorEditRequestItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Missing connector edit items");
        }
        List<ConnectorEditResultItem> results = new ArrayList<>(items.size());
        for (ConnectorEditRequestItem item : items) {
            if (item == null) {
                continue;
            }
            results.add(applyEdit(mapModel, item));
        }
        return new ConnectorEditResponse(mapIdentifierValue, results);
    }

    public ToolCallSummary buildToolCallSummary(ConnectorEditRequest request, ConnectorEditResponse response) {
        int itemCount = request == null || request.getItems() == null ? 0 : request.getItems().size();
        int resultCount = response == null || response.getItems() == null ? 0 : response.getItems().size();
        String summaryText = "editConnectors: items=" + itemCount + ", results=" + resultCount;
        return new ToolCallSummary("editConnectors", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(ConnectorEditRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("editConnectors", "editConnectors error: " + safeMessage, true);
    }

    private ConnectorEditResultItem applyEdit(MapModel mapModel, ConnectorEditRequestItem item) {
        String sourceNodeIdentifier = requireValue(item.getSourceNodeIdentifier(), "sourceNodeIdentifier");
        String targetNodeIdentifier = requireValue(item.getTargetNodeIdentifier(), "targetNodeIdentifier");
        EditOperation operation = requireValue(item.getOperation(), "operation");
        NodeModel sourceNode = mapModel.getNodeForID(sourceNodeIdentifier);
        if (sourceNode == null) {
            throw new IllegalArgumentException("Unknown source node identifier: " + sourceNodeIdentifier);
        }
        NodeModel targetNode = mapModel.getNodeForID(targetNodeIdentifier);
        if (targetNode == null) {
            throw new IllegalArgumentException("Unknown target node identifier: " + targetNodeIdentifier);
        }
        switch (operation) {
            case ADD:
                return addConnector(sourceNode, targetNode, item);
            case DELETE:
                return deleteConnector(sourceNode, targetNodeIdentifier, item);
            case REPLACE:
                return updateConnector(sourceNode, targetNodeIdentifier, item);
            default:
                throw new IllegalArgumentException("Unsupported connector operation: " + operation);
        }
    }

    private ConnectorEditResultItem addConnector(NodeModel sourceNode, NodeModel targetNode,
                                                 ConnectorEditRequestItem item) {
        ConnectorModel connector = linkController.addConnector(sourceNode, targetNode);
        applyLabelUpdates(connector, item);
        aiEditsMarker.addAiEditsMarkerWithUndo(sourceNode);
        ConnectorItem connectorItem = ConnectorItem.fromConnector(connector);
        return new ConnectorEditResultItem(
            item.getSourceNodeIdentifier(),
            item.getTargetNodeIdentifier(),
            "added",
            0,
            connectorItem);
    }

    private ConnectorEditResultItem updateConnector(NodeModel sourceNode, String targetNodeIdentifier,
                                                    ConnectorEditRequestItem item) {
        List<ConnectorModel> matches = findMatchingConnectors(sourceNode, targetNodeIdentifier, item);
        ConnectorModel connector = selectFirstMatch(matches);
        applyLabelUpdates(connector, item);
        aiEditsMarker.addAiEditsMarkerWithUndo(sourceNode);
        int ignored = Math.max(0, matches.size() - 1);
        ConnectorItem connectorItem = ConnectorItem.fromConnector(connector);
        return new ConnectorEditResultItem(
            item.getSourceNodeIdentifier(),
            item.getTargetNodeIdentifier(),
            "updated",
            ignored,
            connectorItem);
    }

    private ConnectorEditResultItem deleteConnector(NodeModel sourceNode, String targetNodeIdentifier,
                                                    ConnectorEditRequestItem item) {
        List<ConnectorModel> matches = findMatchingConnectors(sourceNode, targetNodeIdentifier, item);
        ConnectorModel connector = selectFirstMatch(matches);
        ConnectorItem connectorItem = ConnectorItem.fromConnector(connector);
        linkController.removeArrowLink(connector);
        aiEditsMarker.addAiEditsMarkerWithUndo(sourceNode);
        int ignored = Math.max(0, matches.size() - 1);
        return new ConnectorEditResultItem(
            item.getSourceNodeIdentifier(),
            item.getTargetNodeIdentifier(),
            "deleted",
            ignored,
            connectorItem);
    }

    private void applyLabelUpdates(ConnectorModel connector, ConnectorEditRequestItem item) {
        if (connector == null || item == null) {
            return;
        }
        if (item.getSourceLabel() != null) {
            linkController.setSourceLabel(connector, item.getSourceLabel());
        }
        if (item.getMiddleLabel() != null) {
            linkController.setMiddleLabel(connector, item.getMiddleLabel());
        }
        if (item.getTargetLabel() != null) {
            linkController.setTargetLabel(connector, item.getTargetLabel());
        }
    }

    private List<ConnectorModel> findMatchingConnectors(NodeModel sourceNode, String targetNodeIdentifier,
                                                        ConnectorEditRequestItem item) {
        Collection<NodeLinkModel> links = NodeLinks.getLinks(sourceNode);
        List<ConnectorModel> matches = new ArrayList<>();
        if (links == null || links.isEmpty()) {
            return matches;
        }
        for (NodeLinkModel link : links) {
            if (!(link instanceof ConnectorModel)) {
                continue;
            }
            ConnectorModel connector = (ConnectorModel) link;
            if (!targetNodeIdentifier.equals(connector.getTargetID())) {
                continue;
            }
            if (!matchesLabel(connector.getSourceLabel().orElse(null), item.getMatchSourceLabel())) {
                continue;
            }
            if (!matchesLabel(connector.getMiddleLabel().orElse(null), item.getMatchMiddleLabel())) {
                continue;
            }
            if (!matchesLabel(connector.getTargetLabel().orElse(null), item.getMatchTargetLabel())) {
                continue;
            }
            matches.add(connector);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No matching connectors found for source "
                + sourceNode.createID() + " and target " + targetNodeIdentifier);
        }
        return matches;
    }

    private ConnectorModel selectFirstMatch(List<ConnectorModel> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("No matching connectors found.");
        }
        return matches.get(0);
    }

    private boolean matchesLabel(String actual, String expected) {
        if (expected == null) {
            return true;
        }
        return Objects.equals(actual, expected);
    }

    private <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value;
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier, error);
        }
    }
}

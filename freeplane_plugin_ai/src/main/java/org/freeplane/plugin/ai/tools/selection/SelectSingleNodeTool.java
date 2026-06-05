package org.freeplane.plugin.ai.tools.selection;

import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class SelectSingleNodeTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final MMapController mapController;
    private final SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool;

    public SelectSingleNodeTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                                MMapController mapController,
                                SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.selectedMapAndNodeIdentifiersTool = Objects.requireNonNull(
            selectedMapAndNodeIdentifiersTool, "selectedMapAndNodeIdentifiersTool");
    }

    public SelectionIdentifiersResponse selectSingleNode(SelectSingleNodeRequest request) {
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        String nodeIdentifier = requireValue(request.getNodeIdentifier(), "nodeIdentifier");
        NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier);
        if (nodeModel == null) {
            throw new IllegalArgumentException("Unknown node identifier: " + nodeIdentifier);
        }
        mapController.displayNode(nodeModel);
        mapController.getModeController().getController().getSelection().selectAsTheOnlyOneSelected(nodeModel);
        return selectedMapAndNodeIdentifiersTool.getSelectedMapAndNodeIdentifiers(null);
    }

    public ToolCallSummary buildToolCallSummary(SelectionIdentifiersResponse response) {
        return new ToolCallSummary("selectSingleNode",
            "selectSingleNode: selection updated", false);
    }

    public ToolCallSummary buildToolCallErrorSummary(SelectSingleNodeRequest request, RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("selectSingleNode", "selectSingleNode error: " + safeMessage, true);
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
}

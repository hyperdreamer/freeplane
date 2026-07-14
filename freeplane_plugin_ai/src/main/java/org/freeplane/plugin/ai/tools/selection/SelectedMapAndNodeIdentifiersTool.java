package org.freeplane.plugin.ai.tools.selection;

import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class SelectedMapAndNodeIdentifiersTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final SelectionIdentifiersBuilder selectionIdentifiersBuilder;

    public SelectedMapAndNodeIdentifiersTool(AvailableMaps availableMaps,
                                             AvailableMaps.MapAccessListener mapAccessListener,
                                             NodeTextPreviewFormatter nodeTextPreviewFormatter) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.selectionIdentifiersBuilder = new SelectionIdentifiersBuilder(nodeTextPreviewFormatter);
    }

    public SelectionIdentifiersResponse getSelectedMapAndNodeIdentifiers(SelectionIdentifiersRequest request) {
        UUID mapIdentifier = availableMaps.getCurrentMapIdentifier();
        MapModel mapModel = availableMaps.getCurrentMapModel();
        if (mapIdentifier != null && mapModel != null && mapAccessListener != null) {
            mapAccessListener.onMapAccessed(mapIdentifier, mapModel);
        }
        String mapIdentifierValue = mapIdentifier == null ? null : mapIdentifier.toString();
        IMapSelection selection = Controller.getCurrentController().getSelection();
        SelectionCollectionMode selectionCollectionMode = request == null
            ? null
            : request.getSelectionCollectionMode();
        return selectionIdentifiersBuilder.buildSelectionIdentifiersResponse(
            mapIdentifierValue, mapModel, selection, selectionCollectionMode);
    }

    public ToolCallSummary buildToolCallSummary(SelectionIdentifiersResponse response) {
        return new ToolCallSummary("getSelectedMapAndNodeIdentifiers",
            "getSelectedMapAndNodeIdentifiers: selected identifiers read", false);
    }

    public ToolCallSummary buildToolCallErrorSummary(RuntimeException error) {
        String message = error == null ? "Unknown error" : error.getMessage();
        String safeMessage = ToolCallSummaryFormatter.sanitizeValue(message == null
            ? error.getClass().getSimpleName()
            : message);
        return new ToolCallSummary("getSelectedMapAndNodeIdentifiers",
            "getSelectedMapAndNodeIdentifiers error: " + safeMessage, true);
    }
}

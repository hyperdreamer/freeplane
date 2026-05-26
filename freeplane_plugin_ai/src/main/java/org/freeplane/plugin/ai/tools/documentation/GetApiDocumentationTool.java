package org.freeplane.plugin.ai.tools.documentation;

import java.io.File;
import java.util.Objects;
import java.util.UUID;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;

public class GetApiDocumentationTool {
    private final AvailableMaps availableMaps;
    private final ApiDocumentationMapLoader mapLoader;
    private final ApiDocumentationStructureSummaryReader structureSummaryReader;

    public GetApiDocumentationTool(AvailableMaps availableMaps, MMapController mapController,
                                   TextController textController) {
        this(availableMaps, new ApiDocumentationMapLoader(mapController),
            new ApiDocumentationStructureSummaryReader(textController));
    }

    GetApiDocumentationTool(AvailableMaps availableMaps, ApiDocumentationMapLoader mapLoader,
                            ApiDocumentationStructureSummaryReader structureSummaryReader) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapLoader = Objects.requireNonNull(mapLoader, "mapLoader");
        this.structureSummaryReader = Objects.requireNonNull(structureSummaryReader, "structureSummaryReader");
    }

    public GetApiDocumentationResponse getApiDocumentation() {
        ApiDocumentationMapLoader.LoadedApiDocumentationMap loadedMap = mapLoader.loadInstalledApiMap();
        MapModel mapModel = loadedMap.getMapModel();
        UUID mapIdentifier = availableMaps.getOrCreateMapIdentifier(mapModel);
        NodeModel rootNode = requireRootNode(mapModel, loadedMap.getMapFile());
        String rootNodeIdentifier = rootNode.getID();
        if (rootNodeIdentifier == null) {
            rootNodeIdentifier = rootNode.createID();
        }
        String structureSummary = structureSummaryReader.readStructureSummary(mapModel, loadedMap.getMapFile());
        return new GetApiDocumentationResponse(mapIdentifier.toString(), rootNodeIdentifier, structureSummary);
    }

    public ToolCallSummary buildToolCallSummary(GetApiDocumentationResponse response) {
        if (response == null) {
            return new ToolCallSummary("getApiDocumentation", "getApiDocumentation: no response", true);
        }
        String summaryText = "getApiDocumentation: mapIdentifier=\""
            + ToolCallSummaryFormatter.sanitizeValue(response.getMapIdentifier())
            + "\", rootNodeIdentifier=\""
            + ToolCallSummaryFormatter.sanitizeValue(response.getRootNodeIdentifier())
            + "\"";
        return new ToolCallSummary("getApiDocumentation", summaryText, false);
    }

    public ToolCallSummary buildToolCallErrorSummary(RuntimeException error) {
        String errorMessage = error == null ? "" : ToolCallSummaryFormatter.sanitizeValue(error.getMessage());
        String summaryText = errorMessage.isEmpty()
            ? "getApiDocumentation error"
            : "getApiDocumentation error: " + errorMessage;
        return new ToolCallSummary("getApiDocumentation", summaryText, true);
    }

    private NodeModel requireRootNode(MapModel mapModel, File mapFile) {
        NodeModel rootNode = mapModel.getRootNode();
        if (rootNode != null) {
            return rootNode;
        }
        throw new IllegalStateException("API documentation map is invalid at " + mapFile.getAbsolutePath()
            + ": missing root node. Remedy: regenerate freeplane-api.mm from the current build.");
    }
}

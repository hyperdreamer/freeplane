package org.freeplane.plugin.ai.tools.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;

public class BreadcrumbsTool {
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final NodeContentItemReader nodeContentItemReader;

    public BreadcrumbsTool(AvailableMaps availableMaps, AvailableMaps.MapAccessListener mapAccessListener,
                           NodeContentItemReader nodeContentItemReader) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        this.nodeContentItemReader = Objects.requireNonNull(nodeContentItemReader, "nodeContentItemReader");
    }

    public BreadcrumbsResponse getBreadcrumbs(BreadcrumbsRequest request) {
        Objects.requireNonNull(request, "request");
        String mapIdentifier = requireValue(request.getMapIdentifier(), "mapIdentifier");
        String nodeIdentifier = requireValue(request.getNodeIdentifier(), "nodeIdentifier");
        UUID mapIdentifierValue = parseMapIdentifier(mapIdentifier);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifierValue, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifier);
        }
        NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier);
        if (nodeModel == null) {
            throw new IllegalArgumentException("Unknown node identifier: " + nodeIdentifier);
        }
        List<BreadcrumbItem> breadcrumbs = buildBreadcrumbs(nodeModel, request.includesNodeIdentifiers());
        return new BreadcrumbsResponse(mapIdentifier, breadcrumbs);
    }

    private List<BreadcrumbItem> buildBreadcrumbs(NodeModel nodeModel, boolean includesNodeIdentifiers) {
        List<BreadcrumbItem> breadcrumbs = new ArrayList<>();
        NodeModel current = nodeModel;
        while (current != null) {
            if (!SummaryNode.isHidden(current)) {
                breadcrumbs.add(buildBreadcrumbItem(current, includesNodeIdentifiers));
            }
            current = current.getParentNode();
        }
        Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private BreadcrumbItem buildBreadcrumbItem(NodeModel nodeModel, boolean includesNodeIdentifiers) {
        NodeContentItem contentItem = nodeContentItemReader.readNodeContentItem(
            nodeModel, NodeContentPreset.BRIEF, includesNodeIdentifiers);
        String text = null;
        if (contentItem != null && contentItem.getContent() != null) {
            text = contentItem.getContent().getBriefText();
        }
        String nodeIdentifier = includesNodeIdentifiers && contentItem != null
            ? contentItem.getNodeIdentifier()
            : null;
        return new BreadcrumbItem(text, nodeIdentifier);
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier, error);
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value;
    }
}

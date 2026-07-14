package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.maps.AvailableMaps;

class MapRootShortTextFormatter {
    private final AvailableMaps availableMaps;
    private final NodeTextPreviewFormatter nodeTextPreviewFormatter;

    MapRootShortTextFormatter(AvailableMaps availableMaps, NodeTextPreviewFormatter nodeTextPreviewFormatter) {
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.nodeTextPreviewFormatter = Objects.requireNonNull(nodeTextPreviewFormatter, "nodeTextPreviewFormatter");
    }

    List<MapRootShortTextCount> buildCounts(List<String> mapIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (mapIds == null || mapIds.isEmpty()) {
            return new ArrayList<>();
        }
        for (String mapId : mapIds) {
            String rootText = resolveRootShortText(mapId);
            if (rootText == null || rootText.isEmpty()) {
                continue;
            }
            counts.put(rootText, counts.getOrDefault(rootText, 0) + 1);
        }
        List<MapRootShortTextCount> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            results.add(new MapRootShortTextCount(entry.getKey(), entry.getValue()));
        }
        return results;
    }


    String formatCounts(List<MapRootShortTextCount> counts) {
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (MapRootShortTextCount entry : counts) {
            if (entry == null || entry.getText() == null || entry.getText().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getText());
            if (entry.getCount() > 1) {
                builder.append(" (x").append(entry.getCount()).append(")");
            }
        }
        return builder.toString();
    }

    private String resolveRootShortText(String mapId) {
        if (mapId == null || mapId.isEmpty()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(mapId);
        } catch (IllegalArgumentException error) {
            return null;
        }
        MapModel mapModel = availableMaps.findMapModel(uuid);
        if (mapModel == null) {
            return null;
        }
        NodeModel rootNode = mapModel.getRootNode();
        if (rootNode == null) {
            return null;
        }
        return nodeTextPreviewFormatter.shortText(rootNode);
    }
}

package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.freeplane.plugin.ai.maps.AvailableMaps;

class MapRootShortTextFormatter {
    private static final int MAXIMUM_ROOT_TEXT_LENGTH = 40;
    private static final String CONTINUATION_MARK = " ...";

    private final AvailableMaps availableMaps;
    private final TextController textController;

    MapRootShortTextFormatter(AvailableMaps availableMaps, TextController textController) {
        this.availableMaps = availableMaps;
        this.textController = textController;
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
        return textController.getShortPlainText(rootNode, MAXIMUM_ROOT_TEXT_LENGTH, CONTINUATION_MARK);
    }
}

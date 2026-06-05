package org.freeplane.plugin.ai.chat.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;

class MapRootShortTextCountsMerger {
    List<MapRootShortTextCount> mergeByMax(List<MapRootShortTextCount> existingCounts,
                                          List<MapRootShortTextCount> currentCounts) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        mergeInto(merged, existingCounts);
        mergeInto(merged, currentCounts);
        List<MapRootShortTextCount> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            results.add(new MapRootShortTextCount(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    private void mergeInto(Map<String, Integer> merged, List<MapRootShortTextCount> counts) {
        if (counts == null) {
            return;
        }
        for (MapRootShortTextCount entry : counts) {
            if (entry == null || entry.getText() == null || entry.getText().isEmpty()) {
                continue;
            }
            int count = Math.max(0, entry.getCount());
            Integer existing = merged.get(entry.getText());
            if (existing == null || count > existing) {
                merged.put(entry.getText(), count);
            }
        }
    }
}

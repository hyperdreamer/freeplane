package org.freeplane.plugin.ai.chat.memory;

import java.util.ArrayList;
import java.util.List;

class ToolWindowSelector {

    List<ChatOwnedToolActivityGroup> selectHiddenGroups(List<ChatOwnedToolActivityGroup> groups,
                                                        long historicalToolWindowBudget) {
        List<ChatOwnedToolActivityGroup> hiddenGroups = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            return hiddenGroups;
        }
        long visibleHistoricalToolTokens = 0L;
        for (ChatOwnedToolActivityGroup group : groups) {
            visibleHistoricalToolTokens += group.tokenCount();
        }
        for (ChatOwnedToolActivityGroup group : groups) {
            if (visibleHistoricalToolTokens <= historicalToolWindowBudget) {
                break;
            }
            hiddenGroups.add(group);
            visibleHistoricalToolTokens -= group.tokenCount();
        }
        return hiddenGroups;
    }
}

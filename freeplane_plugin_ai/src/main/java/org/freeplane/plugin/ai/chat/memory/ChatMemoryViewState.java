package org.freeplane.plugin.ai.chat.memory;

import java.util.ArrayList;
import java.util.List;

class ChatMemoryViewState {

    private int activeStartIndex;
    private int currentTurnCount;
    private final List<HistoricalToolCycle> hiddenHistoricalToolCycles = new ArrayList<>();

    void clear() {
        activeStartIndex = 0;
        currentTurnCount = 0;
        hiddenHistoricalToolCycles.clear();
    }

    int activeStartIndex() {
        return activeStartIndex;
    }

    void activeStartIndex(int activeStartIndex) {
        this.activeStartIndex = activeStartIndex;
    }

    int currentTurnCount() {
        return currentTurnCount;
    }

    void currentTurnCount(int currentTurnCount) {
        this.currentTurnCount = currentTurnCount;
    }

    List<HistoricalToolCycle> hiddenHistoricalToolCycles() {
        return hiddenHistoricalToolCycles;
    }

    void clearHiddenHistoricalToolCycles() {
        hiddenHistoricalToolCycles.clear();
    }

    boolean replaceHiddenHistoricalToolCycles(List<HistoricalToolCycle> hiddenHistoricalToolCycles) {
        List<HistoricalToolCycle> replacement = hiddenHistoricalToolCycles == null
            ? new ArrayList<>()
            : new ArrayList<>(hiddenHistoricalToolCycles);
        if (this.hiddenHistoricalToolCycles.equals(replacement)) {
            return false;
        }
        this.hiddenHistoricalToolCycles.clear();
        this.hiddenHistoricalToolCycles.addAll(replacement);
        return true;
    }
}

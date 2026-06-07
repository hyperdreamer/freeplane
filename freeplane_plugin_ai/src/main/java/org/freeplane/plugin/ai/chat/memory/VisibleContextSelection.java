package org.freeplane.plugin.ai.chat.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class VisibleContextSelection {

    private final int visibleStartIndex;
    private final int firstVisibleHistoryIndex;
    private final boolean[] inclusionMask;
    private final List<HistoricalToolCycle> hiddenHistoricalToolCycles;
    private final long visibleTokenCount;

    VisibleContextSelection(int visibleStartIndex,
                            int firstVisibleHistoryIndex,
                            boolean[] inclusionMask,
                            List<HistoricalToolCycle> hiddenHistoricalToolCycles,
                            long visibleTokenCount) {
        this.visibleStartIndex = visibleStartIndex;
        this.firstVisibleHistoryIndex = firstVisibleHistoryIndex;
        this.inclusionMask = inclusionMask == null ? new boolean[0] : inclusionMask;
        this.hiddenHistoricalToolCycles = hiddenHistoricalToolCycles == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(hiddenHistoricalToolCycles));
        this.visibleTokenCount = visibleTokenCount;
    }

    int visibleStartIndex() {
        return visibleStartIndex;
    }

    int firstVisibleHistoryIndex() {
        return firstVisibleHistoryIndex;
    }

    boolean[] inclusionMask() {
        return inclusionMask;
    }

    boolean includes(int index) {
        return index >= visibleStartIndex
            && index < inclusionMask.length
            && inclusionMask[index];
    }

    List<HistoricalToolCycle> hiddenHistoricalToolCycles() {
        return hiddenHistoricalToolCycles;
    }

    long visibleTokenCount() {
        return visibleTokenCount;
    }
}

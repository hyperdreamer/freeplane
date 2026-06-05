package org.freeplane.plugin.ai.chat.memory;

import java.util.Objects;

class HistoricalToolCycle {

    private final int startIndex;
    private final int endIndex;
    private final long tokenCount;

    HistoricalToolCycle(int startIndex, int endIndex, long tokenCount) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.tokenCount = tokenCount;
    }

    int startIndex() {
        return startIndex;
    }

    int endIndex() {
        return endIndex;
    }

    long tokenCount() {
        return tokenCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HistoricalToolCycle)) {
            return false;
        }
        HistoricalToolCycle that = (HistoricalToolCycle) other;
        return startIndex == that.startIndex
            && endIndex == that.endIndex
            && tokenCount == that.tokenCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startIndex, endIndex, tokenCount);
    }
}

package org.freeplane.plugin.ai.chat.memory;

class ActiveTurnRange {

    private final int startIndex;
    private final int endExclusive;

    ActiveTurnRange(int startIndex, int endExclusive) {
        this.startIndex = startIndex;
        this.endExclusive = endExclusive;
    }

    int startIndex() {
        return startIndex;
    }

    int endExclusive() {
        return endExclusive;
    }
}

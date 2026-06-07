package org.freeplane.plugin.ai.chat.memory;

class ActiveTurnRange {

    private final int startIndex;
    private final int endIndex;

    ActiveTurnRange(int startIndex, int endIndex) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    int startIndex() {
        return startIndex;
    }

    int endIndex() {
        return endIndex;
    }
}

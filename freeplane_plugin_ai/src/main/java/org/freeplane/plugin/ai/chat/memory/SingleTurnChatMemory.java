package org.freeplane.plugin.ai.chat.memory;

public interface SingleTurnChatMemory {

    int snapshotSize();

    void truncateTo(int size);

    boolean evictOldestTurn();
}

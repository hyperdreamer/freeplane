package org.freeplane.plugin.ai.chat.memory;

class ChatOwnedToolActivityGroup {

    private final int startIndex;
    private final int endExclusive;
    private final long tokenCount;

    ChatOwnedToolActivityGroup(int startIndex, int endExclusive, long tokenCount) {
        this.startIndex = startIndex;
        this.endExclusive = endExclusive;
        this.tokenCount = tokenCount;
    }

    int startIndex() {
        return startIndex;
    }

    int endExclusive() {
        return endExclusive;
    }

    long tokenCount() {
        return tokenCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatOwnedToolActivityGroup)) {
            return false;
        }
        ChatOwnedToolActivityGroup that = (ChatOwnedToolActivityGroup) other;
        return startIndex == that.startIndex
            && endExclusive == that.endExclusive
            && tokenCount == that.tokenCount;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(startIndex);
        result = 31 * result + Integer.hashCode(endExclusive);
        result = 31 * result + Long.hashCode(tokenCount);
        return result;
    }
}

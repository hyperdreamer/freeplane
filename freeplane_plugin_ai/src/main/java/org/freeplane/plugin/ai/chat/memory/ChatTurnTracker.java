package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;

class ChatTurnTracker {

    List<Integer> rebuildTurnEndIndexes(List<ChatMessage> conversationMessages) {
        List<Integer> turnEndIndexes = new ArrayList<>();
        for (int index = 0; index < conversationMessages.size(); index++) {
            if (isCompletedAssistantMessage(conversationMessages.get(index))) {
                turnEndIndexes.add(index + 1);
            }
        }
        return turnEndIndexes;
    }

    int activeConversationEndIndex(List<Integer> turnEndIndexes, ChatMemoryViewState viewState, int conversationSize) {
        if (canRedo(turnEndIndexes, viewState)) {
            int firstActive = firstActiveTurnIndex(turnEndIndexes, viewState.activeStartIndex(), conversationSize);
            if (viewState.currentTurnCount() <= firstActive) {
                return viewState.activeStartIndex();
            }
            return turnEndIndexes.get(viewState.currentTurnCount() - 1);
        }
        return conversationSize;
    }

    int conversationEndIndexForCurrentTurnRange(List<Integer> turnEndIndexes,
                                                ChatMemoryViewState viewState,
                                                int conversationSize) {
        if (canRedo(turnEndIndexes, viewState)) {
            if (viewState.currentTurnCount() <= 0) {
                return 0;
            }
            return turnEndIndexes.get(viewState.currentTurnCount() - 1);
        }
        return conversationSize;
    }

    int firstActiveTurnIndex(List<Integer> turnEndIndexes, int activeStartIndex, int conversationSize) {
        int startIndex = Math.min(activeStartIndex, conversationSize);
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd > startIndex) {
                return index;
            }
        }
        return turnEndIndexes.size();
    }

    int turnStartIndex(List<Integer> turnEndIndexes, int turnIndex) {
        if (turnIndex <= 0) {
            return 0;
        }
        return turnEndIndexes.get(turnIndex - 1);
    }

    int previousTurnStartFor(List<Integer> turnEndIndexes, int startIndex) {
        int safeStart = Math.max(0, startIndex);
        int previousTurnIndex = -1;
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd <= safeStart) {
                previousTurnIndex = index;
                continue;
            }
            break;
        }
        if (previousTurnIndex < 0) {
            return -1;
        }
        return turnStartIndex(turnEndIndexes, previousTurnIndex);
    }

    int findNextTurnEndAfter(List<Integer> turnEndIndexes, int startIndex) {
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            if (turnEnd > startIndex) {
                return turnEnd;
            }
        }
        return -1;
    }

    List<ActiveTurnRange> activeTurnRanges(List<Integer> turnEndIndexes, int activeStartIndex, int endIndex) {
        List<ActiveTurnRange> ranges = new ArrayList<>();
        int startIndex = Math.min(activeStartIndex, endIndex);
        int previousEnd = 0;
        for (int index = 0; index < turnEndIndexes.size(); index++) {
            int turnEnd = turnEndIndexes.get(index);
            int turnStart = previousEnd;
            previousEnd = turnEnd;
            if (turnEnd <= startIndex) {
                continue;
            }
            int rangeStart = Math.max(turnStart, startIndex);
            int rangeEnd = Math.min(turnEnd, endIndex);
            if (rangeEnd > rangeStart) {
                ranges.add(new ActiveTurnRange(rangeStart, rangeEnd));
            }
        }
        return ranges;
    }

    boolean canRedo(List<Integer> turnEndIndexes, ChatMemoryViewState viewState) {
        return viewState.currentTurnCount() < turnEndIndexes.size();
    }

    private boolean isCompletedAssistantMessage(ChatMessage message) {
        if (!(message instanceof AiMessage) || message instanceof InstructionAckMessage) {
            return false;
        }
        return !((AiMessage) message).hasToolExecutionRequests();
    }
}

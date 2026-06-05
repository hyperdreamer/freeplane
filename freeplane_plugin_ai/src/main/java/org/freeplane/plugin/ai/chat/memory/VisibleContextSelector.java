package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

class VisibleContextSelector {

    private final ChatTurnTracker turnTracker;
    private final int protectedRecentTurnCount;
    private final double historicalToolTokenShare;
    private final Predicate<ChatMessage> removableMessagePredicate;
    private final ToIntFunction<ChatMessage> tokenCounter;

    VisibleContextSelector(ChatTurnTracker turnTracker,
                           int protectedRecentTurnCount,
                           double historicalToolTokenShare,
                           Predicate<ChatMessage> removableMessagePredicate,
                           ToIntFunction<ChatMessage> tokenCounter) {
        this.turnTracker = turnTracker;
        this.protectedRecentTurnCount = protectedRecentTurnCount;
        this.historicalToolTokenShare = historicalToolTokenShare;
        this.removableMessagePredicate = removableMessagePredicate;
        this.tokenCounter = tokenCounter;
    }

    VisibleContextSelection currentSelection(List<ChatMessage> conversationMessages,
                                             int activeStartIndex,
                                             int conversationEndIndex,
                                             List<HistoricalToolCycle> hiddenHistoricalToolCycles) {
        return visibleContextSelectionForHiddenCycles(conversationMessages, activeStartIndex, conversationEndIndex,
            hiddenHistoricalToolCycles);
    }

    VisibleContextSelection selectVisibleContext(List<ChatMessage> conversationMessages,
                                                 List<Integer> turnEndIndexes,
                                                 int activeStartIndex,
                                                 int conversationEndIndex,
                                                 int targetTokens) {
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        int visibleStartIndex = Math.min(activeStartIndex, endIndex);
        List<HistoricalToolCycle> hiddenCycles = new ArrayList<>();
        if (endIndex <= visibleStartIndex) {
            return new VisibleContextSelection(visibleStartIndex, visibleStartIndex, new boolean[endIndex],
                hiddenCycles, 0L);
        }
        int historicalEndIndex = firstProtectedTurnStartIndex(turnEndIndexes, activeStartIndex, endIndex);
        long protectedTokens = estimateTokens(conversationMessages, historicalEndIndex, endIndex);
        long historicalTokens = Math.max(0L, (long) targetTokens - protectedTokens);
        long historicalToolTokenCap = (long) Math.floor(historicalTokens * historicalToolTokenShare);
        List<HistoricalToolCycle> historicalCycles =
            collectHistoricalToolCycles(conversationMessages, activeStartIndex, historicalEndIndex);
        hiddenCycles.addAll(trimHistoricalToolCycles(historicalCycles, historicalToolTokenCap));
        return visibleContextSelectionForHiddenCycles(conversationMessages, activeStartIndex, endIndex, hiddenCycles);
    }

    private VisibleContextSelection visibleContextSelectionForHiddenCycles(List<ChatMessage> conversationMessages,
                                                                           int activeStartIndex,
                                                                           int conversationEndIndex,
                                                                           List<HistoricalToolCycle> hiddenCycles) {
        int endIndex = Math.max(0, Math.min(conversationEndIndex, conversationMessages.size()));
        int visibleStartIndex = Math.min(activeStartIndex, endIndex);
        boolean[] inclusionMask = new boolean[endIndex];
        for (int index = visibleStartIndex; index < endIndex; index++) {
            inclusionMask[index] = true;
        }
        for (HistoricalToolCycle cycle : hiddenCycles) {
            int hiddenStart = Math.max(cycle.startIndex(), visibleStartIndex);
            int hiddenEnd = Math.min(cycle.endIndex(), endIndex);
            for (int index = hiddenStart; index < hiddenEnd; index++) {
                inclusionMask[index] = false;
            }
        }
        int firstVisibleHistoryIndex =
            alignVisibleStartIndex(conversationMessages, visibleStartIndex, endIndex, inclusionMask);
        long visibleTokenCount =
            estimateVisibleTokens(conversationMessages, inclusionMask, firstVisibleHistoryIndex, endIndex);
        return new VisibleContextSelection(visibleStartIndex, firstVisibleHistoryIndex, inclusionMask,
            hiddenCycles, visibleTokenCount);
    }

    private int alignVisibleStartIndex(List<ChatMessage> conversationMessages,
                                       int startIndex,
                                       int endIndex,
                                       boolean[] inclusionMask) {
        int alignedStart = Math.max(0, Math.min(startIndex, endIndex));
        while (alignedStart < endIndex) {
            if (inclusionMask != null && !inclusionMask[alignedStart]) {
                alignedStart++;
                continue;
            }
            ChatMessage message = conversationMessages.get(alignedStart);
            if (message instanceof ToolCallSummaryMessage) {
                if (!hasVisibleMessageAfter(inclusionMask, alignedStart + 1, endIndex)) {
                    break;
                }
                alignedStart++;
                continue;
            }
            break;
        }
        return alignedStart;
    }

    private boolean hasVisibleMessageAfter(boolean[] inclusionMask, int startIndex, int endIndex) {
        for (int index = Math.max(0, startIndex); index < endIndex; index++) {
            if (inclusionMask == null || inclusionMask[index]) {
                return true;
            }
        }
        return false;
    }

    private long estimateVisibleTokens(List<ChatMessage> conversationMessages,
                                       boolean[] inclusionMask,
                                       int startIndex,
                                       int endIndex) {
        long total = 0L;
        int safeStart = Math.max(0, Math.min(startIndex, endIndex));
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            if (inclusionMask != null && !inclusionMask[index]) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            if (!removableMessagePredicate.test(message)) {
                continue;
            }
            total += tokenCounter.applyAsInt(message);
        }
        return total;
    }

    private int firstProtectedTurnStartIndex(List<Integer> turnEndIndexes, int activeStartIndex, int endIndex) {
        List<ActiveTurnRange> ranges = turnTracker.activeTurnRanges(turnEndIndexes, activeStartIndex, endIndex);
        if (ranges.isEmpty()) {
            return Math.min(activeStartIndex, endIndex);
        }
        int protectedCount = Math.min(protectedRecentTurnCount, ranges.size());
        int protectedIndex = ranges.size() - protectedCount;
        return ranges.get(protectedIndex).startIndex();
    }

    private long estimateTokens(List<ChatMessage> conversationMessages, int startIndex, int endIndex) {
        long total = 0L;
        int safeStart = Math.max(0, Math.min(startIndex, endIndex));
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!removableMessagePredicate.test(message)) {
                continue;
            }
            total += tokenCounter.applyAsInt(message);
        }
        return total;
    }

    List<HistoricalToolCycle> collectHistoricalToolCycles(List<ChatMessage> conversationMessages,
                                                          int activeStartIndex,
                                                          int historicalEndIndex) {
        List<HistoricalToolCycle> cycles = new ArrayList<>();
        int startIndex = Math.min(activeStartIndex, historicalEndIndex);
        for (int index = startIndex; index < historicalEndIndex; index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!isToolRequestMessage(message)) {
                continue;
            }
            int cycleEndIndex = index + 1;
            long tokenCount = tokenCounter.applyAsInt(message);
            while (cycleEndIndex < historicalEndIndex) {
                ChatMessage nextMessage = conversationMessages.get(cycleEndIndex);
                if (nextMessage instanceof ToolExecutionResultMessage) {
                    tokenCount += tokenCounter.applyAsInt(nextMessage);
                    cycleEndIndex++;
                    continue;
                }
                if (nextMessage instanceof ToolCallSummaryMessage) {
                    cycleEndIndex++;
                    continue;
                }
                break;
            }
            cycles.add(new HistoricalToolCycle(index, cycleEndIndex, tokenCount));
            index = cycleEndIndex - 1;
        }
        return cycles;
    }

    List<HistoricalToolCycle> trimHistoricalToolCycles(List<HistoricalToolCycle> historicalCycles,
                                                       long historicalToolTokenCap) {
        List<HistoricalToolCycle> hiddenCycles = new ArrayList<>();
        long visibleHistoricalToolTokens = 0L;
        for (HistoricalToolCycle cycle : historicalCycles) {
            visibleHistoricalToolTokens += cycle.tokenCount();
        }
        for (HistoricalToolCycle cycle : historicalCycles) {
            if (visibleHistoricalToolTokens <= historicalToolTokenCap) {
                break;
            }
            hiddenCycles.add(cycle);
            visibleHistoricalToolTokens -= cycle.tokenCount();
        }
        return hiddenCycles;
    }

    private boolean isToolRequestMessage(ChatMessage message) {
        if (!(message instanceof AiMessage) || message instanceof InstructionAckMessage) {
            return false;
        }
        return ((AiMessage) message).hasToolExecutionRequests();
    }
}

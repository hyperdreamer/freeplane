package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

class ChatMessageFilter {

    private final ChatTurnTracker turnTracker;
    private final ToolWindowSelector toolWindowSelector;
    private final ToIntFunction<ChatMessage> tokenCounter;

    ChatMessageFilter(ChatTurnTracker turnTracker,
                      ToolWindowSelector toolWindowSelector,
                      ToIntFunction<ChatMessage> tokenCounter) {
        this.turnTracker = turnTracker;
        this.toolWindowSelector = toolWindowSelector;
        this.tokenCounter = tokenCounter;
    }

    FilteredChatMessages filterMessages(List<ChatMessage> messages,
                                        ChatMemoryViewState viewState,
                                        List<Integer> turnEndIndexes,
                                        int maxTokens) {
        return computeFiltering(messages, viewState, turnEndIndexes, maxTokens).filteredMessages();
    }

    FilteringComputation computeFiltering(List<ChatMessage> messages,
                                          ChatMemoryViewState viewState,
                                          List<Integer> turnEndIndexes,
                                          int maxTokens) {
        List<ChatMessage> safeMessages = messages == null ? Collections.emptyList() : messages;
        List<Integer> safeTurnEndIndexes = turnEndIndexes == null ? Collections.emptyList() : turnEndIndexes;
        List<ChatEntryCategory> categories = classifyMessages(safeMessages);
        int activeEndIndex = turnTracker.activeConversationEndIndex(safeTurnEndIndexes, viewState, safeMessages.size());
        int chatWindowStartIndex = Math.min(viewState.chatWindowStartIndex(), activeEndIndex);
        List<ActiveTurnRange> activeTurnRanges = turnTracker.activeTurnRanges(safeTurnEndIndexes, viewState, activeEndIndex);
        long currentWindowTokenCount = estimateCompactionTokenCount(categories, safeMessages, chatWindowStartIndex,
            activeEndIndex);
        List<ChatOwnedToolActivityGroup> hiddenGroups = Collections.emptyList();
        if (currentWindowTokenCount >= Math.max(0, maxTokens)) {
            int protectedTurnStartIndex = protectedTurnStartIndex(activeTurnRanges, chatWindowStartIndex, activeEndIndex);
            long protectedTokenCount = estimateCompactionTokenCount(categories, safeMessages, protectedTurnStartIndex,
                activeEndIndex);
            int postResponseTarget = Math.max(0, maxTokens) / 4;
            long remainingHistoricalBudget = Math.max(0L, (long) postResponseTarget - protectedTokenCount);
            long historicalToolWindowBudget = remainingHistoricalBudget / 2L;
            List<ChatOwnedToolActivityGroup> historicalGroups = collectHistoricalToolActivityGroups(
                categories,
                safeMessages,
                chatWindowStartIndex,
                protectedTurnStartIndex);
            hiddenGroups = toolWindowSelector.selectHiddenGroups(historicalGroups, historicalToolWindowBudget);
        }
        List<ChatMessage> filteredMessageList = new ArrayList<>();
        long filteredTokenCount = 0L;
        for (int index = 0; index < activeEndIndex; index++) {
            if (index < chatWindowStartIndex || isHiddenByToolWindow(index, hiddenGroups)) {
                continue;
            }
            ChatEntryCategory category = categories.get(index);
            if (category == null) {
                continue;
            }
            ChatMessage message = safeMessages.get(index);
            filteredMessageList.add(message);
            if (countsForCompaction(category)) {
                filteredTokenCount += tokenCounter.applyAsInt(message);
            }
        }
        FilteredChatMessages filteredMessages = new FilteredChatMessages(
            filteredMessageList,
            chatWindowStartIndex > 0,
            hiddenGroups.size(),
            viewState.isRestoredTranscriptSession());
        return new FilteringComputation(filteredMessages, hiddenGroups, filteredTokenCount, activeEndIndex,
            currentWindowTokenCount);
    }

    private List<ChatEntryCategory> classifyMessages(List<ChatMessage> messages) {
        List<ChatEntryCategory> categories = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            categories.add(classifyMessage(message));
        }
        return categories;
    }

    private ChatEntryCategory classifyMessage(ChatMessage message) {
        if (message instanceof AssistantProfileSwitchMessage) {
            return ChatEntryCategory.ASSISTANT_PROFILE_SWITCH;
        }
        if (message instanceof AutomaticCodeStatusMessage) {
            return ChatEntryCategory.AUTOMATIC_CODE_STATUS;
        }
        if (message instanceof ToolCallSummaryMessage) {
            return ((ToolCallSummaryMessage) message).toolCaller() == ToolCaller.MCP
                ? ChatEntryCategory.MCP_MESSAGE
                : ChatEntryCategory.CHAT_TOOL_SUMMARY;
        }
        if (message instanceof ToolExecutionResultMessage) {
            return ChatEntryCategory.CHAT_TOOL_RESULT;
        }
        if (message instanceof AiMessage && !(message instanceof InstructionAckMessage)) {
            return ((AiMessage) message).hasToolExecutionRequests()
                ? ChatEntryCategory.CHAT_TOOL_REQUEST
                : ChatEntryCategory.ASSISTANT_TEXT;
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            if (text != null && text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                return null;
            }
            return ChatEntryCategory.USER_TEXT;
        }
        return null;
    }

    private int protectedTurnStartIndex(List<ActiveTurnRange> activeTurnRanges,
                                        int chatWindowStartIndex,
                                        int activeEndIndex) {
        if (activeTurnRanges == null || activeTurnRanges.isEmpty()) {
            return Math.min(chatWindowStartIndex, activeEndIndex);
        }
        return activeTurnRanges.get(activeTurnRanges.size() - 1).startIndex();
    }

    private long estimateCompactionTokenCount(List<ChatEntryCategory> categories,
                                              List<ChatMessage> messages,
                                              int startIndex,
                                              int endIndex) {
        long total = 0L;
        int safeStart = Math.max(0, Math.min(startIndex, endIndex));
        int safeEnd = Math.min(endIndex, messages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            ChatEntryCategory category = categories.get(index);
            if (!countsForCompaction(category)) {
                continue;
            }
            total += tokenCounter.applyAsInt(messages.get(index));
        }
        return total;
    }

    private List<ChatOwnedToolActivityGroup> collectHistoricalToolActivityGroups(List<ChatEntryCategory> categories,
                                                                                 List<ChatMessage> messages,
                                                                                 int startIndex,
                                                                                 int historicalEndIndex) {
        List<ChatOwnedToolActivityGroup> groups = new ArrayList<>();
        int safeStart = Math.max(0, Math.min(startIndex, historicalEndIndex));
        int safeEnd = Math.min(historicalEndIndex, messages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            if (categories.get(index) != ChatEntryCategory.CHAT_TOOL_REQUEST) {
                continue;
            }
            long tokenCount = tokenCounter.applyAsInt(messages.get(index));
            int groupEndExclusive = index + 1;
            while (groupEndExclusive < safeEnd) {
                ChatEntryCategory nextCategory = categories.get(groupEndExclusive);
                if (nextCategory == ChatEntryCategory.CHAT_TOOL_RESULT) {
                    tokenCount += tokenCounter.applyAsInt(messages.get(groupEndExclusive));
                    groupEndExclusive++;
                    continue;
                }
                if (nextCategory == ChatEntryCategory.CHAT_TOOL_SUMMARY) {
                    groupEndExclusive++;
                    continue;
                }
                break;
            }
            groups.add(new ChatOwnedToolActivityGroup(index, groupEndExclusive, tokenCount));
            index = groupEndExclusive - 1;
        }
        return groups;
    }

    private boolean isHiddenByToolWindow(int index, List<ChatOwnedToolActivityGroup> hiddenGroups) {
        for (ChatOwnedToolActivityGroup hiddenGroup : hiddenGroups) {
            if (index >= hiddenGroup.startIndex() && index < hiddenGroup.endExclusive()) {
                return true;
            }
        }
        return false;
    }

    private boolean countsForCompaction(ChatEntryCategory category) {
        return category == ChatEntryCategory.USER_TEXT
            || category == ChatEntryCategory.ASSISTANT_TEXT
            || category == ChatEntryCategory.CHAT_TOOL_REQUEST
            || category == ChatEntryCategory.CHAT_TOOL_RESULT
            || category == ChatEntryCategory.AUTOMATIC_CODE_STATUS;
    }

    static class FilteringComputation {
        private final FilteredChatMessages filteredMessages;
        private final List<ChatOwnedToolActivityGroup> hiddenGroups;
        private final long filteredTokenCount;
        private final int activeEndIndex;
        private final long currentWindowTokenCount;

        FilteringComputation(FilteredChatMessages filteredMessages,
                             List<ChatOwnedToolActivityGroup> hiddenGroups,
                             long filteredTokenCount,
                             int activeEndIndex,
                             long currentWindowTokenCount) {
            this.filteredMessages = filteredMessages;
            this.hiddenGroups = hiddenGroups == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(hiddenGroups));
            this.filteredTokenCount = filteredTokenCount;
            this.activeEndIndex = activeEndIndex;
            this.currentWindowTokenCount = currentWindowTokenCount;
        }

        FilteredChatMessages filteredMessages() {
            return filteredMessages;
        }

        List<ChatOwnedToolActivityGroup> hiddenGroups() {
            return hiddenGroups;
        }

        long filteredTokenCount() {
            return filteredTokenCount;
        }

        int activeEndIndex() {
            return activeEndIndex;
        }

        long currentWindowTokenCount() {
            return currentWindowTokenCount;
        }
    }
}

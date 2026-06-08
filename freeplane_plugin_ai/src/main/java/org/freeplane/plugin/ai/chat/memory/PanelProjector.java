package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

class PanelProjector {

    List<ChatMemoryRenderEntry> buildRenderEntries(GeneralSystemMessage generalSystemMessage,
                                                   FilteredChatMessages filteredChatMessages) {
        List<ChatMemoryRenderEntry> entries = new ArrayList<>();
        if (generalSystemMessage != null) {
            entries.add(ChatMemoryRenderEntry.forMessage(generalSystemMessage));
        }
        if (filteredChatMessages == null) {
            return entries;
        }
        List<ChatMessage> filteredMessageList = filteredChatMessages.messages();
        if (filteredChatMessages.hasOmittedEarlierChat() && !filteredMessageList.isEmpty()) {
            entries.add(ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()));
        }
        for (int index = 0; index < filteredMessageList.size(); index++) {
            ChatMessage message = filteredMessageList.get(index);
            if (message instanceof ToolCallSummaryMessage
                && ((ToolCallSummaryMessage) message).toolCaller() == ToolCaller.MCP) {
                ToolCallSummaryMessage summaryMessage = (ToolCallSummaryMessage) message;
                entries.add(ChatMemoryRenderEntry.forToolSummary(summaryMessage.text(), summaryMessage.toolCaller()));
                continue;
            }
            if (isChatToolRequest(message)) {
                int groupEnd = endOfFilteredChatToolActivityGroup(filteredMessageList, index);
                List<ToolCallSummaryMessage> summaries = filteredChatToolSummaries(filteredMessageList, index, groupEnd);
                if (!summaries.isEmpty()) {
                    for (ToolCallSummaryMessage summary : summaries) {
                        entries.add(ChatMemoryRenderEntry.forToolSummary(summary.text(), summary.toolCaller()));
                    }
                }
                else {
                    appendFilteredChatToolDetail(entries, filteredMessageList, index, groupEnd);
                }
                index = groupEnd - 1;
                continue;
            }
            if (message instanceof ToolExecutionResultMessage) {
                entries.add(ChatMemoryRenderEntry.forMessage(message));
                continue;
            }
            if (message instanceof ToolCallSummaryMessage) {
                ToolCallSummaryMessage summaryMessage = (ToolCallSummaryMessage) message;
                entries.add(ChatMemoryRenderEntry.forToolSummary(summaryMessage.text(), summaryMessage.toolCaller()));
                continue;
            }
            entries.add(ChatMemoryRenderEntry.forMessage(message));
        }
        return entries;
    }

    private void appendFilteredChatToolDetail(List<ChatMemoryRenderEntry> entries,
                                              List<ChatMessage> filteredMessageList,
                                              int startIndex,
                                              int endExclusive) {
        for (int index = startIndex; index < endExclusive; index++) {
            ChatMessage message = filteredMessageList.get(index);
            if (message instanceof ToolCallSummaryMessage) {
                continue;
            }
            entries.add(ChatMemoryRenderEntry.forMessage(message));
        }
    }

    private List<ToolCallSummaryMessage> filteredChatToolSummaries(List<ChatMessage> filteredMessageList,
                                                                   int startIndex,
                                                                   int endExclusive) {
        if (filteredMessageList == null || filteredMessageList.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolCallSummaryMessage> summaries = new ArrayList<>();
        for (int index = startIndex; index < endExclusive; index++) {
            ChatMessage message = filteredMessageList.get(index);
            if (message instanceof ToolCallSummaryMessage
                && ((ToolCallSummaryMessage) message).toolCaller() != ToolCaller.MCP) {
                summaries.add((ToolCallSummaryMessage) message);
            }
        }
        return summaries;
    }

    private int endOfFilteredChatToolActivityGroup(List<ChatMessage> filteredMessageList, int startIndex) {
        int index = startIndex + 1;
        while (index < filteredMessageList.size()) {
            ChatMessage message = filteredMessageList.get(index);
            if (message instanceof ToolExecutionResultMessage) {
                index++;
                continue;
            }
            if (message instanceof ToolCallSummaryMessage
                && ((ToolCallSummaryMessage) message).toolCaller() != ToolCaller.MCP) {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private boolean isChatToolRequest(ChatMessage message) {
        return message instanceof AiMessage
            && !(message instanceof InstructionAckMessage)
            && ((AiMessage) message).hasToolExecutionRequests();
    }
}

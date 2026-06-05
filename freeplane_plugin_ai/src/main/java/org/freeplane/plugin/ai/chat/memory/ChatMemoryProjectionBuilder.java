package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.tools.MessageBuilder;

class ChatMemoryProjectionBuilder {

    List<ChatTranscriptEntry> buildTranscriptEntries(List<ChatMessage> conversationMessages,
                                                     VisibleContextSelection selection,
                                                     Function<ChatMessage, ChatTranscriptEntry> transcriptEntryFactory) {
        if (selection == null) {
            return Collections.emptyList();
        }
        List<ChatTranscriptEntry> entries = new ArrayList<>();
        int endIndex = Math.min(selection.inclusionMask().length, conversationMessages.size());
        int startIndex = selection.firstVisibleHistoryIndex();
        for (int index = 0; index < endIndex; index++) {
            if (index == startIndex && startIndex > 0 && endIndex > startIndex) {
                entries.add(new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                    RemovedForSpaceSystemMessage.DEFAULT_TEXT));
            }
            if (!selection.includes(index)) {
                continue;
            }
            ChatTranscriptEntry entry = transcriptEntryFactory.apply(conversationMessages.get(index));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    List<ChatMemoryRenderEntry> buildRenderEntries(List<ChatMessage> conversationMessages,
                                                   GeneralSystemMessage generalSystemMessage,
                                                   VisibleContextSelection selection) {
        if (selection == null || selection.inclusionMask().length == 0) {
            return Collections.emptyList();
        }
        List<ChatMemoryRenderEntry> entries = new ArrayList<>();
        if (generalSystemMessage != null) {
            entries.add(ChatMemoryRenderEntry.forMessage(generalSystemMessage));
        }
        int endIndex = Math.min(selection.inclusionMask().length, conversationMessages.size());
        int startIndex = selection.firstVisibleHistoryIndex();
        for (int index = startIndex; index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            if (index == startIndex && startIndex > 0 && endIndex > startIndex) {
                entries.add(ChatMemoryRenderEntry.forMessage(new RemovedForSpaceSystemMessage()));
            }
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof ToolCallSummaryMessage) {
                ToolCallSummaryMessage summaryMessage = (ToolCallSummaryMessage) message;
                entries.add(ChatMemoryRenderEntry.forToolSummary(summaryMessage.text(), summaryMessage.toolCaller()));
                continue;
            }
            entries.add(ChatMemoryRenderEntry.forMessage(message));
        }
        return entries;
    }

    List<ChatMessage> buildMessages(List<ChatMessage> conversationMessages,
                                    GeneralSystemMessage generalSystemMessage,
                                    VisibleContextSelection selection,
                                    int latestProfileSwitchIndex,
                                    UserMessage latestProfileInstruction) {
        List<ChatMessage> messages = new ArrayList<>();
        if (generalSystemMessage != null) {
            messages.add(generalSystemMessage);
        }
        if (selection == null) {
            return messages;
        }
        int endIndex = Math.min(selection.inclusionMask().length, conversationMessages.size());
        int startIndex = selection.firstVisibleHistoryIndex();
        if (latestProfileInstruction != null && latestProfileSwitchIndex >= 0
            && latestProfileSwitchIndex < startIndex) {
            messages.add(latestProfileInstruction);
        }
        for (int index = startIndex; index < endIndex; index++) {
            if (!selection.includes(index)) {
                continue;
            }
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof AssistantProfileSwitchMessage) {
                if (index == latestProfileSwitchIndex && latestProfileInstruction != null) {
                    messages.add(latestProfileInstruction);
                }
                continue;
            }
            if (message instanceof ToolCallSummaryMessage) {
                continue;
            }
            if (message instanceof TranscriptHiddenSystemMessage
                || message instanceof RemovedForSpaceSystemMessage) {
                messages.add(MessageBuilder.buildSystemInstructionUserMessage(
                    ((SystemMessage) message).text()));
                continue;
            }
            messages.add(message);
        }
        return messages;
    }

    List<ChatMessage> buildRawMessages(List<ChatMessage> conversationMessages,
                                       GeneralSystemMessage generalSystemMessage,
                                       VisibleContextSelection selection) {
        List<ChatMessage> messages = new ArrayList<>();
        if (generalSystemMessage != null) {
            messages.add(generalSystemMessage);
        }
        if (selection == null) {
            return messages;
        }
        int endIndex = Math.min(selection.inclusionMask().length, conversationMessages.size());
        for (int index = selection.firstVisibleHistoryIndex(); index < endIndex; index++) {
            if (selection.includes(index)) {
                messages.add(conversationMessages.get(index));
            }
        }
        return messages;
    }
}

package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.tools.MessageBuilder;

class ModelProjector {

    List<ChatMessage> buildMessages(GeneralSystemMessage generalSystemMessage,
                                    FilteredChatMessages filteredChatMessages,
                                    UserMessage latestProfileInstruction) {
        List<ChatMessage> messages = new ArrayList<>();
        if (generalSystemMessage != null) {
            messages.add(generalSystemMessage);
        }
        if (filteredChatMessages == null) {
            return messages;
        }
        if (filteredChatMessages.isRestoredTranscriptSession()) {
            messages.add(MessageBuilder.buildSystemInstructionUserMessage(TranscriptHiddenSystemMessage.DEFAULT_TEXT));
        }
        int latestProfileSwitchIndex = latestProfileSwitchIndex(filteredChatMessages.messages());
        if (latestProfileInstruction != null && latestProfileSwitchIndex < 0) {
            appendDerivedLatestProfilePair(messages, latestProfileInstruction);
        }
        List<ChatMessage> bodyMessages = buildBodyMessages(
            filteredChatMessages.messages(),
            latestProfileInstruction,
            latestProfileSwitchIndex);
        if (filteredChatMessages.hasOmittedEarlierChat() && !bodyMessages.isEmpty()) {
            messages.add(MessageBuilder.buildSystemInstructionUserMessage(
                RemovedForSpaceSystemMessage.DEFAULT_TEXT));
        }
        messages.addAll(bodyMessages);
        return messages;
    }

    private List<ChatMessage> buildBodyMessages(List<ChatMessage> filteredMessageList,
                                                UserMessage latestProfileInstruction,
                                                int latestProfileSwitchIndex) {
        List<ChatMessage> bodyMessages = new ArrayList<>();
        for (int index = 0; index < filteredMessageList.size(); index++) {
            ChatMessage message = filteredMessageList.get(index);
            if (message instanceof AssistantProfileSwitchMessage) {
                if (index == latestProfileSwitchIndex && latestProfileInstruction != null) {
                    appendDerivedLatestProfilePair(bodyMessages, latestProfileInstruction);
                }
                continue;
            }
            if (message instanceof ToolCallSummaryMessage || message instanceof InstructionAckMessage) {
                continue;
            }
            bodyMessages.add(message);
        }
        return bodyMessages;
    }

    private void appendDerivedLatestProfilePair(List<ChatMessage> messages,
                                                UserMessage latestProfileInstruction) {
        if (messages == null || latestProfileInstruction == null) {
            return;
        }
        messages.add(latestProfileInstruction);
        messages.add(new InstructionAckMessage());
    }

    private int latestProfileSwitchIndex(List<ChatMessage> filteredMessageList) {
        for (int index = filteredMessageList.size() - 1; index >= 0; index--) {
            if (filteredMessageList.get(index) instanceof AssistantProfileSwitchMessage) {
                return index;
            }
        }
        return -1;
    }
}

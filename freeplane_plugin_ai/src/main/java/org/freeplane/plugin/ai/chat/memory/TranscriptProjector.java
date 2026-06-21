package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.tools.MessageBuilder;

class TranscriptProjector {

    List<ChatTranscriptEntry> buildTranscriptEntries(GeneralSystemMessage generalSystemMessage,
                                                      FilteredChatMessages filteredChatMessages) {
        List<ChatTranscriptEntry> entries = new ArrayList<>();
        if (generalSystemMessage != null) {
            entries.add(new ChatTranscriptEntry(
                ChatTranscriptRole.SYSTEM,
                generalSystemMessage.text(),
                generalSystemMessage.baseText()));
        }
        if (filteredChatMessages == null) {
            return entries;
        }
        List<ChatTranscriptEntry> durableEntries = new ArrayList<>();
        for (ChatMessage message : filteredChatMessages.messages()) {
            ChatTranscriptEntry entry = toTranscriptEntry(message);
            if (entry != null) {
                durableEntries.add(entry);
            }
        }
        if (filteredChatMessages.hasOmittedEarlierChat() && !durableEntries.isEmpty()) {
            entries.add(new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                RemovedForSpaceSystemMessage.DEFAULT_TEXT));
        }
        entries.addAll(durableEntries);
        return entries;
    }

    private ChatTranscriptEntry toTranscriptEntry(ChatMessage message) {
        if (message == null) {
            return null;
        }
        if (message instanceof AssistantProfileSwitchMessage) {
            AssistantProfileSwitchMessage profileMessage = (AssistantProfileSwitchMessage) message;
            return new AssistantProfileTranscriptEntry(
                profileMessage.getProfileId(),
                profileMessage.getProfileName(),
                profileMessage.getProfileMessage(),
                false);
        }
        if (message instanceof AutomaticCodeStatusMessage) {
            String text = ((AutomaticCodeStatusMessage) message).singleText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.AUTOMATIC_CODE_STATUS, text);
        }
        if (message instanceof PromptReferenceUserMessage) {
            PromptReferenceUserMessage promptReferenceMessage = (PromptReferenceUserMessage) message;
            ChatTranscriptEntry entry = new ChatTranscriptEntry(
                ChatTranscriptRole.USER,
                promptReferenceMessage.getVisibleText());
            entry.setPromptName(promptReferenceMessage.getPromptName());
            entry.setPromptText(promptReferenceMessage.getPromptText());
            entry.setModelFacingText(promptReferenceMessage.getModelFacingText());
            entry.setPromptReferenceEndOffset(Integer.valueOf(promptReferenceMessage.getReferenceEndOffset()));
            return entry;
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            if (text == null || text.trim().isEmpty() || text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.USER, text);
        }
        if (message instanceof AiMessage && !(message instanceof InstructionAckMessage)) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                return null;
            }
            String text = aiMessage.text();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, text);
        }
        return null;
    }
}

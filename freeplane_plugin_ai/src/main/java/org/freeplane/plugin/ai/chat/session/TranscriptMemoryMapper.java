package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.AutomaticCodeStatusMessage;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.memory.InstructionAckMessage;
import org.freeplane.plugin.ai.chat.memory.RemovedForSpaceSystemMessage;
import org.freeplane.plugin.ai.chat.memory.TranscriptHiddenSystemMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;

class TranscriptMemoryMapper {

    void seedTranscriptWithHiddenExchange(ChatMemory memory,
                                          Iterable<ChatTranscriptEntry> entries,
                                          String hiddenSystemMessage) {
        if (memory == null) {
            return;
        }
        memory.clear();
        AssistantProfileChatMemory assistantProfileMemory =
            memory instanceof AssistantProfileChatMemory ? (AssistantProfileChatMemory) memory : null;
        if (entries != null) {
            for (ChatTranscriptEntry entry : entries) {
                if (assistantProfileMemory != null
                    && entry != null
                    && entry.getRole() == ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM) {
                    assistantProfileMemory.markContextWindowStart();
                    continue;
                }
                ChatMessage message = toChatMessage(entry);
                if (message != null) {
                    memory.add(message);
                }
            }
        }
        if (hiddenSystemMessage != null && !hiddenSystemMessage.trim().isEmpty()) {
            memory.add(new TranscriptHiddenSystemMessage(hiddenSystemMessage));
        }
    }

    List<ChatTranscriptEntry> toTranscriptEntries(ChatMemory memory) {
        if (memory == null) {
            return new ArrayList<>();
        }
        if (memory instanceof AssistantProfileChatMemory) {
            return ((AssistantProfileChatMemory) memory).transcriptEntriesForPersistence();
        }
        List<ChatTranscriptEntry> entries = new ArrayList<>();
        for (ChatMessage message : memory.messages()) {
            ChatTranscriptEntry entry = toTranscriptEntry(message);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private ChatMessage toChatMessage(ChatTranscriptEntry entry) {
        if (entry == null || entry.getRole() == null) {
            return null;
        }
        if (entry.getRole() == ChatTranscriptRole.SYSTEM) {
            return new GeneralSystemMessage(entry.getText() == null ? "" : entry.getText());
        }
        if (entry.getRole() == ChatTranscriptRole.ASSISTANT) {
            if (entry.getText() == null) {
                return null;
            }
            return new AiMessage(entry.getText());
        }
        if (entry.getRole() == ChatTranscriptRole.ASSISTANT_PROFILE_SYSTEM) {
            if (!(entry instanceof AssistantProfileTranscriptEntry)) {
                return null;
            }
            AssistantProfileTranscriptEntry assistantProfileEntry = (AssistantProfileTranscriptEntry) entry;
            return new AssistantProfileSwitchMessage(
                assistantProfileEntry.getProfileId(),
                assistantProfileEntry.getProfileName(),
                assistantProfileEntry.getProfileMessage());
        }
        if (entry.getRole() == ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM) {
            if (entry.getText() == null) {
                return null;
            }
            return MessageBuilder.buildSystemInstructionUserMessage(entry.getText());
        }
        if (entry.getRole() == ChatTranscriptRole.AUTOMATIC_CODE_STATUS) {
            if (entry.getText() == null) {
                return null;
            }
            return new AutomaticCodeStatusMessage(entry.getText());
        }
        if (entry.getText() == null) {
            return null;
        }
        return new UserMessage(entry.getText());
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
        if (message instanceof GeneralSystemMessage) {
            return new ChatTranscriptEntry(ChatTranscriptRole.SYSTEM,
                ((GeneralSystemMessage) message).text());
        }
        if (message instanceof RemovedForSpaceSystemMessage) {
            return new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                ((RemovedForSpaceSystemMessage) message).text());
        }
        if (message instanceof AutomaticCodeStatusMessage) {
            String text = ((AutomaticCodeStatusMessage) message).singleText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.AUTOMATIC_CODE_STATUS, text);
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            if (text == null || text.trim().isEmpty() || text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.USER, text);
        }
        if (message instanceof AiMessage && !(message instanceof InstructionAckMessage)) {
            String text = ((AiMessage) message).text();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, text);
        }
        return null;
    }

}

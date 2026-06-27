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
import org.freeplane.plugin.ai.chat.memory.PromptReferenceUserMessage;
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
            String composedText = entry.getText() == null ? "" : entry.getText();
            String baseText = entry.getBaseSystemText() == null ? composedText : entry.getBaseSystemText();
            return new GeneralSystemMessage(baseText, composedText, entry.isSystemMessageExact());
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
                assistantProfileEntry.getProfileMessage(),
                assistantProfileEntry.getModelConfiguration());
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
        if (entry.getRole() == ChatTranscriptRole.USER && hasPromptReferencePayload(entry)) {
            return new PromptReferenceUserMessage(
                entry.getText(),
                entry.getPromptName(),
                promptText(entry),
                entry.getModelFacingText(),
                promptReferenceEndOffset(entry));
        }
        return new UserMessage(entry.getText());
    }

    private boolean hasPromptReferencePayload(ChatTranscriptEntry entry) {
        return entry != null
            && entry.getModelFacingText() != null
            && entry.getPromptReferenceEndOffset() != null;
    }

    private String promptText(ChatTranscriptEntry entry) {
        String promptText = entry.getPromptText();
        if (promptText != null) {
            return promptText;
        }
        String visibleSuffix = entry.getText() == null
            ? ""
            : entry.getText().substring(promptReferenceEndOffset(entry));
        String modelFacingText = entry.getModelFacingText() == null ? "" : entry.getModelFacingText();
        return modelFacingText.endsWith(visibleSuffix)
            ? modelFacingText.substring(0, modelFacingText.length() - visibleSuffix.length())
            : modelFacingText;
    }

    private int promptReferenceEndOffset(ChatTranscriptEntry entry) {
        Integer offset = entry.getPromptReferenceEndOffset();
        int visibleLength = entry.getText() == null ? 0 : entry.getText().length();
        return offset == null ? 0 : Math.max(0, Math.min(offset.intValue(), visibleLength));
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
                profileMessage.getModelConfiguration(),
                false);
        }
        if (message instanceof GeneralSystemMessage) {
            GeneralSystemMessage systemMessage = (GeneralSystemMessage) message;
            return new ChatTranscriptEntry(
                ChatTranscriptRole.SYSTEM,
                systemMessage.text(),
                systemMessage.baseText(),
                systemMessage.isSystemMessageExact());
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
            String text = ((AiMessage) message).text();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, text);
        }
        return null;
    }

}

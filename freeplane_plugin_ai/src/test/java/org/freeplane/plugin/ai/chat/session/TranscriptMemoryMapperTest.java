package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Arrays;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.AutomaticCodeStatusMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.memory.InstructionAckMessage;
import org.freeplane.plugin.ai.chat.memory.RemovedForSpaceSystemMessage;
import org.freeplane.plugin.ai.chat.memory.TranscriptHiddenSystemMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TranscriptMemoryMapperTest {
    @Test
    public void seedTranscriptWithHiddenExchange_appendsHiddenMessagesAfterTranscript() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        List<ChatTranscriptEntry> entries = Arrays.asList(
            new ChatTranscriptEntry(ChatTranscriptRole.SYSTEM, "captured system"),
            new ChatTranscriptEntry(ChatTranscriptRole.USER, "first user"),
            new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "first assistant"));

        uut.seedTranscriptWithHiddenExchange(memory, entries, "hidden user");

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(GeneralSystemMessage.class);
        assertThat(((GeneralSystemMessage) messages.get(0)).text()).isEqualTo("captured system");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX
                + TranscriptHiddenSystemMessage.DEFAULT_TEXT);
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText())
            .isEqualTo("first user");
        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(3)).text()).isEqualTo("first assistant");
    }

    @Test
    public void seedTranscriptWithHiddenExchange_mapsAssistantProfileSubtypeWithoutText() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        List<ChatTranscriptEntry> entries = Arrays.asList(
            new AssistantProfileTranscriptEntry("profile-a", "A sayer", false),
            new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"));

        uut.seedTranscriptWithHiddenExchange(memory, entries, "hidden user");

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX
                + TranscriptHiddenSystemMessage.DEFAULT_TEXT);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText())
            .isEqualTo(MessageBuilder.CONTROL_INSTRUCTION_PREFIX + "Now you have the profile A sayer.");
        assertThat(messages.get(2)).isInstanceOf(InstructionAckMessage.class);
        assertThat(((AiMessage) messages.get(2)).text()).isEqualTo("ok");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(3)).singleText()).isEqualTo("hello");
    }

    @Test
    public void toTranscriptEntries_exportsConversationMessages() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        memory.add(new GeneralSystemMessage("captured system"));
        memory.add(new AssistantProfileSwitchMessage("profile-a", "A sayer", "Profile instructions"));
        memory.add(UserMessage.from("hello"));
        memory.add(AiMessage.from("world"));
        memory.add(new TranscriptHiddenSystemMessage("hidden"));

        List<ChatTranscriptEntry> entries = uut.toTranscriptEntries(memory);

        assertThat(entries).hasSize(4);
        assertThat(entries.get(0).getRole()).isEqualTo(ChatTranscriptRole.SYSTEM);
        assertThat(entries.get(0).getText()).isEqualTo("captured system");
        assertThat(entries.get(1)).isInstanceOf(AssistantProfileTranscriptEntry.class);
        assertThat(entries.get(1).getRole()).isEqualTo(ChatTranscriptRole.ASSISTANT_PROFILE_SYSTEM);
        assertThat(((AssistantProfileTranscriptEntry) entries.get(1)).getProfileMessage())
            .isEqualTo("Profile instructions");
        assertThat(entries.get(2).getRole()).isEqualTo(ChatTranscriptRole.USER);
        assertThat(entries.get(2).getText()).isEqualTo("hello");
        assertThat(entries.get(3).getRole()).isEqualTo(ChatTranscriptRole.ASSISTANT);
        assertThat(entries.get(3).getText()).isEqualTo("world");
    }

    @Test
    public void automaticCodeStatusMessagesPersistWithDedicatedTranscriptRole() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        AutomaticCodeStatusMessage message = new AutomaticCodeStatusMessage(
            "Automatic app-authored code-status message:\ncodeState=RUN_SUCCEEDED");
        memory.add(message);

        List<ChatTranscriptEntry> entries = uut.toTranscriptEntries(memory);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getRole()).isEqualTo(ChatTranscriptRole.AUTOMATIC_CODE_STATUS);
        assertThat(entries.get(0).getText()).isEqualTo(message.singleText());
    }

    @Test
    public void automaticCodeStatusTranscriptEntriesRestoreAsDedicatedUserMessages() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);

        uut.seedTranscriptWithHiddenExchange(memory, Arrays.asList(
            new ChatTranscriptEntry(
                ChatTranscriptRole.AUTOMATIC_CODE_STATUS,
                "Automatic app-authored code-status message:\ncodeState=RUN_FAILED")), null);

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(AutomaticCodeStatusMessage.class);
        assertThat(((AutomaticCodeStatusMessage) messages.get(0)).singleText())
            .isEqualTo("Automatic app-authored code-status message:\ncodeState=RUN_FAILED");
    }

    @Test
    public void toTranscriptEntries_omitMessagesBeforeContextWindowBoundary() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        memory.add(UserMessage.from("first user"));
        memory.add(AiMessage.from("first assistant"));
        memory.add(UserMessage.from("second user"));
        memory.add(AiMessage.from("second assistant"));
        memory.evictOldestTurn();

        List<ChatTranscriptEntry> entries = uut.toTranscriptEntries(memory);

        assertThat(entries)
            .extracting(entry -> entry.getRole().name() + ":" + entry.getText())
            .contains("REMOVED_FOR_SPACE_SYSTEM:" + RemovedForSpaceSystemMessage.DEFAULT_TEXT)
            .contains("USER:second user")
            .contains("ASSISTANT:second assistant")
            .doesNotContain("USER:first user", "ASSISTANT:first assistant");
    }

    @Test
    public void transcriptRestoredMessagesUseLocalTokenAccounting() {
        TranscriptMemoryMapper uut = new TranscriptMemoryMapper();
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        List<ChatTranscriptEntry> entries = Arrays.asList(
            new ChatTranscriptEntry(ChatTranscriptRole.USER, "one"),
            new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM,
                RemovedForSpaceSystemMessage.DEFAULT_TEXT),
            new ChatTranscriptEntry(ChatTranscriptRole.USER, "three"));

        uut.seedTranscriptWithHiddenExchange(memory, entries, null);
        List<ChatMemoryRenderEntry> renderEntries = memory.activeConversationRenderEntries();
        assertThat(renderEntries)
            .anyMatch(entry -> entry.chatMessage() instanceof RemovedForSpaceSystemMessage);
    }
}

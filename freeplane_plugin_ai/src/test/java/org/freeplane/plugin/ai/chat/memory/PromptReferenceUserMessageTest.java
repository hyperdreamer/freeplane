package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PromptReferenceUserMessageTest {

    @Test
    public void promptReferenceMessageUsesModelFacingTextForModelAndVisibleTextForUndoAndTranscript() {
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        PromptReferenceUserMessage message = new PromptReferenceUserMessage(
            "/Summarize branch suffix",
            "Summarize branch",
            "Prompt text",
            "Prompt text suffix",
            "/Summarize branch".length());

        memory.add(message);
        memory.add(AiMessage.from("answer"));

        List<ChatMessage> modelMessages = memory.messages();
        assertThat(modelMessages).hasSize(2);
        assertThat(((UserMessage) modelMessages.get(0)).singleText()).isEqualTo("Prompt text suffix");
        assertThat(memory.undo()).isEqualTo("/Summarize branch suffix");

        memory.redo();
        List<ChatTranscriptEntry> transcriptEntries = memory.transcriptEntriesForPersistence();
        assertThat(transcriptEntries).hasSize(2);
        ChatTranscriptEntry entry = transcriptEntries.get(0);
        assertThat(entry.getRole()).isEqualTo(ChatTranscriptRole.USER);
        assertThat(entry.getText()).isEqualTo("/Summarize branch suffix");
        assertThat(entry.getPromptName()).isEqualTo("Summarize branch");
        assertThat(entry.getPromptText()).isEqualTo("Prompt text");
        assertThat(entry.getModelFacingText()).isEqualTo("Prompt text suffix");
        assertThat(entry.getPromptReferenceEndOffset()).isEqualTo("/Summarize branch".length());
    }

    @Test
    public void nextPromptReferenceConvertsMatchingUserMessage() {
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        memory.useNextPromptReference(new PromptReferenceUserMessage(
            "/Summarize branch suffix",
            "Summarize branch",
            "Prompt text",
            "Prompt text suffix",
            "/Summarize branch".length()));

        memory.add(UserMessage.from("Prompt text suffix"));

        ChatMessage storedMessage = memory.activeConversationRenderEntries().get(0).chatMessage();
        assertThat(storedMessage).isInstanceOf(PromptReferenceUserMessage.class);
        assertThat(((PromptReferenceUserMessage) storedMessage).getVisibleText())
            .isEqualTo("/Summarize branch suffix");
    }
}

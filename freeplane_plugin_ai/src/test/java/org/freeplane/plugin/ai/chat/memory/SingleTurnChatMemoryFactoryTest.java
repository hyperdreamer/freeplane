package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleTurnChatMemoryFactoryTest {
    @Test
    public void forMemory_truncatesAssistantProfileMemory() {
        AssistantProfileChatMemory memory = AssistantProfileChatMemory.withMaxTokens(500);
        memory.add(UserMessage.from("hello"));
        memory.add(AiMessage.from("world"));
        memory.add(UserMessage.from("extra"));
        SingleTurnChatMemory uut = SingleTurnChatMemoryFactory.forMemory(memory);

        assertThat(uut.snapshotSize()).isEqualTo(3);

        uut.truncateTo(2);

        assertThat(memory.conversationMessageCount()).isEqualTo(2);
    }

    @Test
    public void forMemory_truncatesGenericChatMemory() {
        ListBackedChatMemory memory = new ListBackedChatMemory();
        memory.add(UserMessage.from("hello"));
        memory.add(AiMessage.from("world"));
        memory.add(UserMessage.from("extra"));
        SingleTurnChatMemory uut = SingleTurnChatMemoryFactory.forMemory(memory);

        assertThat(uut.snapshotSize()).isEqualTo(3);

        uut.truncateTo(2);

        assertThat(memory.messages()).hasSize(2);
        assertThat(memory.messages().get(0)).isInstanceOf(UserMessage.class);
        assertThat(memory.messages().get(1)).isInstanceOf(AiMessage.class);
    }

    private static class ListBackedChatMemory implements ChatMemory {
        private final List<ChatMessage> messages = new ArrayList<>();

        @Override
        public Object id() {
            return "test";
        }

        @Override
        public void add(ChatMessage message) {
            if (message != null) {
                messages.add(message);
            }
        }

        @Override
        public List<ChatMessage> messages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }
}

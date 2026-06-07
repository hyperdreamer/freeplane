package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.List;

public class SingleTurnChatMemoryFactory {

    private SingleTurnChatMemoryFactory() {
    }

    public static SingleTurnChatMemory forMemory(ChatMemory memory) {
        if (memory instanceof AssistantProfileChatMemory) {
            return new AssistantProfileMemoryAdapter((AssistantProfileChatMemory) memory);
        }
        return new GenericChatMemoryAdapter(memory);
    }

    private static class AssistantProfileMemoryAdapter implements SingleTurnChatMemory {

        private final AssistantProfileChatMemory memory;

        private AssistantProfileMemoryAdapter(AssistantProfileChatMemory memory) {
            this.memory = memory;
        }

        @Override
        public int snapshotSize() {
            return memory.conversationMessageCount();
        }

        @Override
        public void truncateTo(int size) {
            memory.truncateConversationMessagesTo(size);
        }

        @Override
        public boolean evictOldestTurn() {
            return memory.evictOldestTurn();
        }
    }

    private static class GenericChatMemoryAdapter implements SingleTurnChatMemory {

        private final ChatMemory memory;

        private GenericChatMemoryAdapter(ChatMemory memory) {
            this.memory = memory;
        }

        @Override
        public int snapshotSize() {
            if (memory == null) {
                return 0;
            }
            return memory.messages().size();
        }

        @Override
        public void truncateTo(int size) {
            if (memory == null) {
                return;
            }
            List<ChatMessage> current = memory.messages();
            int targetSize = Math.max(0, Math.min(size, current.size()));
            if (targetSize == current.size()) {
                return;
            }
            memory.clear();
            for (int index = 0; index < targetSize; index++) {
                ChatMessage message = current.get(index);
                if (message != null) {
                    memory.add(message);
                }
            }
        }

        @Override
        public boolean evictOldestTurn() {
            if (memory == null) {
                return false;
            }
            List<ChatMessage> current = memory.messages();
            if (current.isEmpty()) {
                return false;
            }
            memory.clear();
            for (int index = 1; index < current.size(); index++) {
                ChatMessage message = current.get(index);
                if (message != null) {
                    memory.add(message);
                }
            }
            return true;
        }
    }
}

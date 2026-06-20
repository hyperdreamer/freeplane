package org.freeplane.plugin.ai.chat.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptId;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRecord;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptRole;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptStore;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.ChatMemorySettings;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class LiveChatControllerTest {
    @Test
    public void startNewPromptChat_tracksSelectedModelAndToolOverridesInSession() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.startNewPromptChat(
                AssistantProfileChatMemory.withMaxTokens(500),
                "Prompt: Rewrite",
                "openrouter|openai/gpt-4.1-mini",
                ToolAvailabilityLevel.READING);

            assertThat(uut.currentSessionUsesAssistantProfile()).isFalse();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isEqualTo(ToolAvailabilityLevel.READING);
            assertThat(uut.currentSessionSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");

            uut.clearCurrentSessionSelectedModelOverride();
            uut.clearCurrentSessionToolAvailabilityOverride();

            assertThat(uut.currentSessionSelectedModelOverride()).isNull();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isNull();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void persistCurrentSession_writesAssistantProfileModelAndToolOverrideMetadata() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));
            AssistantProfileChatMemory promptMemory = AssistantProfileChatMemory.withMaxTokens(500);

            uut.startNewPromptChat(
                promptMemory,
                "Prompt: Rewrite",
                "openrouter|openai/gpt-4.1-mini",
                ToolAvailabilityLevel.READING);
            promptMemory.add(UserMessage.from("hello"));
            promptMemory.add(AiMessage.from("world"));
            uut.persistCurrentSessionIfNeeded();

            ChatTranscriptId transcriptId = store.list().get(0).getId();
            ChatTranscriptRecord record = store.load(transcriptId);

            assertThat(record.getAssistantProfileEnabled()).isFalse();
            assertThat(record.getSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(record.getToolAvailabilityOverride()).isEqualTo("reading");
            assertThat(record.hasToolAvailabilityOverrideMetadata()).isTrue();
            assertThat(record.getEntries())
                .extracting(ChatTranscriptEntry::getRole)
                .containsExactly(ChatTranscriptRole.SYSTEM, ChatTranscriptRole.USER, ChatTranscriptRole.ASSISTANT);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void startChatFromTranscript_restoresPromptSessionMetadataWhenPresent() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setDisplayName("Prompt: Rewrite");
            record.setAssistantProfileEnabled(false);
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setToolAvailabilityOverride("disabled");
            record.setEntries(java.util.Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "world")));
            ChatTranscriptId transcriptId = store.save(record, null);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.startChatFromTranscript(transcriptId);

            assertThat(uut.currentSessionUsesAssistantProfile()).isFalse();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isEqualTo(ToolAvailabilityLevel.DISABLED);
            assertThat(uut.currentSessionSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void chatListHandler_startChatFromTranscript_delegatesToController() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setDisplayName("Prompt: Rewrite");
            record.setAssistantProfileEnabled(false);
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setToolAvailabilityOverride("disabled");
            record.setEntries(java.util.Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "world")));
            ChatTranscriptId transcriptId = store.save(record, null);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.createChatListHandler().startChatFromTranscript(transcriptId);

            assertThat(uut.currentSessionUsesAssistantProfile()).isFalse();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isEqualTo(ToolAvailabilityLevel.DISABLED);
            assertThat(uut.currentSessionSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void startChatFromTranscript_restoresNoToolOverrideWhenMetadataFieldIsPresentWithNull() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setDisplayName("Prompt: Rewrite");
            record.setAssistantProfileEnabled(false);
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setToolAvailabilityOverride(null);
            record.setEntries(java.util.Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "world")));
            ChatTranscriptId transcriptId = store.save(record, null);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.startChatFromTranscript(transcriptId);

            assertThat(uut.currentSessionUsesAssistantProfile()).isFalse();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isNull();
            assertThat(uut.currentSessionSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void startChatFromTranscript_keepsEditingToolsForModelOnlyPromptTranscripts() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setDisplayName("Prompt: Rewrite");
            record.setAssistantProfileEnabled(false);
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setEntries(java.util.Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "world")));
            ChatTranscriptId transcriptId = store.save(record, null);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.startChatFromTranscript(transcriptId);

            assertThat(uut.currentSessionUsesAssistantProfile()).isFalse();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isEqualTo(ToolAvailabilityLevel.EDITING);
            assertThat(uut.currentSessionSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void startNewChatCreatesMemoryThatUsesUpdatedMaximumTokenCount() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatMemorySettings chatMemorySettings = mock(ChatMemorySettings.class);
            AtomicInteger maxTokens = new AtomicInteger(5000);
            org.mockito.Mockito.when(chatMemorySettings.getMaximumTokenCount()).thenAnswer(invocation -> maxTokens.get());
            LiveChatController uut = newController(store, chatMemorySettings);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            LiveChatSessionId sessionId = uut.startNewChat();
            AssistantProfileChatMemory memory = (AssistantProfileChatMemory) uut.chatMemory(sessionId);
            String firstQuestion = "first first first first first first first first first first first first first first first first";
            String firstAnswer = "answer one answer one answer one answer one answer one answer one answer one answer one";
            String secondQuestion = "second second second second second second second second second second second second second second second second";
            String secondAnswer = "answer two answer two answer two answer two answer two answer two answer two answer two";
            String thirdQuestion = "third third third third third third third third third third third third third third third third";
            String thirdAnswer = "answer three answer three answer three answer three answer three answer three answer three";
            memory.add(UserMessage.from(firstQuestion));
            memory.add(AiMessage.from(firstAnswer));
            memory.add(UserMessage.from(secondQuestion));
            memory.add(AiMessage.from(secondAnswer));
            memory.add(UserMessage.from(thirdQuestion));
            memory.add(AiMessage.from(thirdAnswer));

            int visibleAfterReductionTokens = estimateTokens(
                UserMessage.from(secondQuestion),
                AiMessage.from(secondAnswer),
                UserMessage.from(thirdQuestion),
                AiMessage.from(thirdAnswer));
            maxTokens.set(visibleAfterReductionTokens);
            memory.refreshCompactionForCurrentMaxTokens();


            assertThat(memory.messages())
                .extracting(message -> message instanceof UserMessage ? ((UserMessage) message).singleText() : null)
                .contains(secondQuestion, thirdQuestion)
                .doesNotContain(firstQuestion);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void startChatFromTranscript_defaultsMissingMetadataToRegularChatSemantics() throws IOException {
        Path tempDir = Files.createTempDirectory("live-chat-controller");
        try {
            ChatTranscriptStore store = newTestStore(tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setDisplayName("Prompt: Rewrite");
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setEntries(java.util.Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "hello"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "world")));
            ChatTranscriptId transcriptId = store.save(record, null);
            LiveChatController uut = newController(store);
            uut.initialize(AssistantProfileChatMemory.withMaxTokens(500));

            uut.startChatFromTranscript(transcriptId);

            assertThat(uut.currentSessionUsesAssistantProfile()).isTrue();
            assertThat(uut.currentSessionToolAvailabilityOverride()).isNull();
            assertThat(uut.currentSessionSelectedModelOverride()).isNull();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private ChatTranscriptStore newTestStore(Path tempDir) {
        try {
            java.lang.reflect.Constructor<ChatTranscriptStore> constructor =
                ChatTranscriptStore.class.getDeclaredConstructor(ObjectMapper.class, Path.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new ObjectMapper(), tempDir);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private LiveChatController newController(ChatTranscriptStore store) {
        ChatMemorySettings chatMemorySettings = mock(ChatMemorySettings.class);
        org.mockito.Mockito.when(chatMemorySettings.getMaximumTokenCount()).thenReturn(500);
        return newController(store, chatMemorySettings);
    }

    private LiveChatController newController(ChatTranscriptStore store, ChatMemorySettings chatMemorySettings) {
        return new LiveChatController(
            mock(AIChatPanel.class),
            mock(AvailableMaps.class),
            mock(TextController.class),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            (chatMemory, fromTranscriptRestore) -> {
            },
            () -> null,
            store,
            chatMemorySettings);
    }

    private int estimateTokens(ChatMessage... messages) {
        OpenAiTokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimator.estimateTokenCountInMessage(message);
        }
        return total;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatTranscriptStoreTest {

    @Test
    public void saveAndLoad_preservesSystemRoles() throws IOException {
        Path tempDir = Files.createTempDirectory("chat-transcripts");
        try {
            ChatTranscriptStore store = new ChatTranscriptStore(new ObjectMapper(), tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            List<ChatTranscriptEntry> entries = Arrays.asList(
                new ChatTranscriptEntry(ChatTranscriptRole.SYSTEM, "system"),
                new ChatTranscriptEntry(ChatTranscriptRole.USER, "user"),
                new ChatTranscriptEntry(ChatTranscriptRole.ASSISTANT, "assistant"),
                new AssistantProfileTranscriptEntry("profile-a", "A sayer", "Profile definition", true),
                new ChatTranscriptEntry(ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM, "removed"));
            record.setAssistantProfileEnabled(false);
            record.setSelectedModelOverride("openrouter|openai/gpt-4.1-mini");
            record.setToolAvailabilityOverride("reading");
            record.setEntries(entries);

            ChatTranscriptId id = store.save(record, null);
            ChatTranscriptRecord loaded = store.load(id);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getEntries())
                .extracting(ChatTranscriptEntry::getRole)
                .containsExactly(
                    ChatTranscriptRole.SYSTEM,
                    ChatTranscriptRole.USER,
                    ChatTranscriptRole.ASSISTANT,
                    ChatTranscriptRole.ASSISTANT_PROFILE_SYSTEM,
                    ChatTranscriptRole.REMOVED_FOR_SPACE_SYSTEM);
            assertThat(loaded.getEntries().get(3)).isInstanceOf(AssistantProfileTranscriptEntry.class);
            AssistantProfileTranscriptEntry profileEntry =
                (AssistantProfileTranscriptEntry) loaded.getEntries().get(3);
            assertThat(profileEntry.getProfileId()).isEqualTo("profile-a");
            assertThat(profileEntry.getProfileName()).isEqualTo("A sayer");
            assertThat(profileEntry.getProfileMessage()).isEqualTo("Profile definition");
            assertThat(profileEntry.containsProfileDefinition()).isTrue();
            assertThat(loaded.getAssistantProfileEnabled()).isFalse();
            assertThat(loaded.getSelectedModelOverride()).isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(loaded.getToolAvailabilityOverride()).isEqualTo("reading");
            assertThat(loaded.hasToolAvailabilityOverrideMetadata()).isTrue();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void saveAndLoad_preservesNullToolAvailabilityOverrideMetadataPresence() throws IOException {
        Path tempDir = Files.createTempDirectory("chat-transcripts");
        try {
            ChatTranscriptStore store = new ChatTranscriptStore(new ObjectMapper(), tempDir);
            ChatTranscriptRecord record = new ChatTranscriptRecord();
            record.setAssistantProfileEnabled(false);
            record.setToolAvailabilityOverride(null);

            ChatTranscriptId id = store.save(record, null);
            ChatTranscriptRecord loaded = store.load(id);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getToolAvailabilityOverride()).isNull();
            assertThat(loaded.hasToolAvailabilityOverrideMetadata()).isTrue();
        } finally {
            deleteRecursively(tempDir);
        }
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

package org.freeplane.plugin.ai.chat.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AssistantProfileStoreTest {

    @Test
    public void loadProfiles_acceptsOldProfilesWithoutModelConfiguration() throws IOException {
        Path tempDir = Files.createTempDirectory("assistant-profiles");
        try {
            Path path = tempDir.resolve(AssistantProfileStore.PROFILES_FILE_NAME);
            Files.write(
                path,
                "[{\"id\":\"id-1\",\"name\":\"First\",\"prompt\":\"one\"}]"
                    .getBytes(StandardCharsets.UTF_8));
            AssistantProfileStore store = new AssistantProfileStore(new ObjectMapper(), path);

            List<AssistantProfile> loaded = store.loadProfiles();

            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getModelConfiguration()).isNull();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void loadProfiles_ignoresInvalidPersistedTemperatureWithoutDroppingProfile() throws IOException {
        Path tempDir = Files.createTempDirectory("assistant-profiles");
        try {
            Path path = tempDir.resolve(AssistantProfileStore.PROFILES_FILE_NAME);
            Files.write(
                path,
                ("[{\"id\":\"id-1\",\"name\":\"First\",\"prompt\":\"one\","
                    + "\"modelConfiguration\":{\"thinkingEffort\":\"HIGH\",\"temperature\":\"broken\"}}]")
                    .getBytes(StandardCharsets.UTF_8));
            AssistantProfileStore store = new AssistantProfileStore(new ObjectMapper(), path);

            List<AssistantProfile> loaded = store.loadProfiles();

            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).getThinkingEffort()).isEqualTo(AiThinkingEffort.HIGH);
            assertThat(loaded.get(0).getTemperature()).isNull();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void saveAndLoad_preservesProfiles() throws IOException {
        Path tempDir = Files.createTempDirectory("assistant-profiles");
        try {
            Path path = tempDir.resolve(AssistantProfileStore.PROFILES_FILE_NAME);
            AssistantProfileStore store = new AssistantProfileStore(new ObjectMapper(), path);
            List<AssistantProfile> profiles = Arrays.asList(
                new AssistantProfile("id-1", "First", "one",
                    AIModelConfiguration.of(
                        AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
                        AiThinkingEffort.HIGH,
                        Double.valueOf(0.4))),
                new AssistantProfile("id-2", "Second", "two"));

            store.saveProfiles(profiles);
            List<AssistantProfile> loaded = store.loadProfiles();

            assertThat(loaded)
                .extracting(AssistantProfile::getId)
                .containsExactly("id-1", "id-2");
            assertThat(loaded)
                .extracting(AssistantProfile::getPrompt)
                .containsExactly("one", "two");
            assertThat(loaded.get(0).getModelConfiguration()).isEqualTo(profiles.get(0).getModelConfiguration());
            assertThat(loaded.get(1).getModelConfiguration()).isNull();
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

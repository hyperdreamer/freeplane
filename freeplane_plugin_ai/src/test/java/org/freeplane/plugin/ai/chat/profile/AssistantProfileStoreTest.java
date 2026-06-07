package org.freeplane.plugin.ai.chat.profile;

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

public class AssistantProfileStoreTest {

    @Test
    public void saveAndLoad_preservesProfiles() throws IOException {
        Path tempDir = Files.createTempDirectory("assistant-profiles");
        try {
            Path path = tempDir.resolve(AssistantProfileStore.PROFILES_FILE_NAME);
            AssistantProfileStore store = new AssistantProfileStore(new ObjectMapper(), path);
            List<AssistantProfile> profiles = Arrays.asList(
                new AssistantProfile("id-1", "First", "one"),
                new AssistantProfile("id-2", "Second", "two"));

            store.saveProfiles(profiles);
            List<AssistantProfile> loaded = store.loadProfiles();

            assertThat(loaded)
                .extracting(AssistantProfile::getId)
                .containsExactly("id-1", "id-2");
            assertThat(loaded)
                .extracting(AssistantProfile::getPrompt)
                .containsExactly("one", "two");
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

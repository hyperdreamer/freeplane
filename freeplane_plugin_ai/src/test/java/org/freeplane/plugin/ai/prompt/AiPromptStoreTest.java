package org.freeplane.plugin.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiPromptStoreTest {

    @Test
    public void saveAndLoad_preservesPromptsAndDialogState() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptStore store = new AiPromptStore(new ObjectMapper(), path);
            AiPromptStore.PersistedState state = new AiPromptStore.PersistedState(
                Arrays.asList(
                    new AiPrompt("Rewrite", "Rewrite node", true,
                        "openrouter|openai/gpt-4.1-mini", "reading"),
                    new AiPrompt("Summarize", "Summarize subtree", false, "", "")),
                new AiPromptStore.PersistedDialogState(
                    "",
                    new AiPrompt("", "Draft prompt", true,
                        "gemini|gemini-2.5-flash", "disabled")));

            store.saveState(state);
            String writtenJson = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            AiPromptStore.PersistedState loaded = store.loadState();

            assertThat(loaded.getSavedPrompts())
                .extracting(
                    AiPrompt::getName,
                    AiPrompt::getPrompt,
                    AiPrompt::isShowInChat,
                    AiPrompt::getModelSelectionValue,
                    AiPrompt::getToolAvailabilitySelectionValue)
                .containsExactly(
                    tuple("Rewrite", "Rewrite node", true, "openrouter|openai/gpt-4.1-mini", "reading"),
                    tuple("Summarize", "Summarize subtree", false, "", ""));
            assertThat(loaded.getDialogState().getSelectedPromptName()).isEmpty();
            assertThat(loaded.getDialogState().getDraft().getPrompt()).isEqualTo("Draft prompt");
            assertThat(loaded.getDialogState().getDraft().isShowInChat()).isTrue();
            assertThat(loaded.getDialogState().getDraft().getModelSelectionValue()).isEqualTo("gemini|gemini-2.5-flash");
            assertThat(loaded.getDialogState().getDraft().getToolAvailabilitySelectionValue()).isEqualTo("disabled");
            assertThat(writtenJson).doesNotContain("modelSelectionValue");
            assertThat(writtenJson).contains("modelConfiguration");
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void loadState_recoversLegacyModelSelectionValueIntoModelConfiguration() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            Files.write(
                path,
                ("{\"savedPrompts\":[{\"name\":\"Rewrite\",\"prompt\":\"Prompt\","
                    + "\"showInChat\":true,\"modelSelectionValue\":\"openrouter|openai/gpt-4.1-mini\"}],"
                    + "\"dialogState\":{\"draft\":{\"name\":\"Draft\",\"prompt\":\"Draft prompt\","
                    + "\"modelSelectionValue\":\"gemini|gemini-2.5-flash\"}}}")
                    .getBytes(StandardCharsets.UTF_8));
            AiPromptStore store = new AiPromptStore(new ObjectMapper(), path);

            AiPromptStore.PersistedState loaded = store.loadState();

            AiPrompt savedPrompt = loaded.getSavedPrompts().get(0);
            assertThat(savedPrompt.getModelSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(savedPrompt.getModelConfiguration().getModelSelection())
                .isEqualTo(AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"));
            assertThat(loaded.getDialogState().getDraft().getModelSelectionValue())
                .isEqualTo("gemini|gemini-2.5-flash");
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void saveAndLoad_preservesPromptThinkingEffortAndTemperature() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPrompt prompt = new AiPrompt("Rewrite", "Prompt", false);
            prompt.setModelConfiguration(AIModelConfiguration.of(
                AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
                AiThinkingEffort.LOW,
                Double.valueOf(0.2)));
            AiPromptStore store = new AiPromptStore(new ObjectMapper(), path);

            store.saveState(new AiPromptStore.PersistedState(
                Arrays.asList(prompt),
                new AiPromptStore.PersistedDialogState()));
            AiPrompt loaded = store.loadState().getSavedPrompts().get(0);

            assertThat(loaded.getModelConfiguration()).isEqualTo(prompt.getModelConfiguration());
            assertThat(loaded.getThinkingEffort()).isEqualTo(AiThinkingEffort.LOW);
            assertThat(loaded.getTemperature()).isEqualTo(0.2);
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void loadState_ignoresInvalidPersistedTemperatureWithoutDroppingPrompt() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            Files.write(
                path,
                ("{\"savedPrompts\":[{\"name\":\"Rewrite\",\"prompt\":\"Prompt\","
                    + "\"modelConfiguration\":{\"thinkingEffort\":\"LOW\",\"temperature\":\"broken\"}}]}")
                    .getBytes(StandardCharsets.UTF_8));
            AiPromptStore store = new AiPromptStore(new ObjectMapper(), path);

            AiPrompt loaded = store.loadState().getSavedPrompts().get(0);

            assertThat(loaded.getThinkingEffort()).isEqualTo(AiThinkingEffort.LOW);
            assertThat(loaded.getTemperature()).isNull();
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void loadState_returnsEmptyStateForObsoleteArrayShape() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            Files.write(
                path,
                "[{\"name\":\"Rewrite\",\"prompt\":\"Prompt\",\"showInChat\":true}]".getBytes(StandardCharsets.UTF_8));
            AiPromptStore store = new AiPromptStore(new ObjectMapper(), path);

            AiPromptStore.PersistedState loaded = store.loadState();

            assertThat(loaded.getSavedPrompts()).isEmpty();
            assertThat(loaded.getDialogState().getSelectedPromptName()).isEmpty();
            assertThat(loaded.getDialogState().getDraft().getName()).isEmpty();
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
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

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}

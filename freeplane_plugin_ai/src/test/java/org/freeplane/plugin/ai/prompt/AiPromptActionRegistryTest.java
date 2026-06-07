package org.freeplane.plugin.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import javax.swing.JPanel;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.prompt.ui.AiPromptManagerDialog;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AiPromptActionRegistryTest {

    @Test
    public void actionKey_usesTrimmedPromptName() {
        assertThat(AiPromptActionRegistry.actionKey("  Rewrite node  "))
            .isEqualTo("RunAiPromptAction.Rewrite node");
    }

    @Test
    public void persistStateIfChanged_doesNotCreateFileForUnchangedEmptyState() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                mock(AIChatPanel.class),
                () -> {
                });

            registry.persistStateIfChanged();

            assertThat(Files.exists(path)).isFalse();
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void persistStateIfChanged_createsFileForChangedDialogState() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                mock(AIChatPanel.class),
                () -> {
                });
            registry.getDialogState().loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
            registry.getDialogState().beginNewDraft();
            registry.getDialogState().updateDraft(
                "",
                "Draft prompt",
                true,
                "openrouter|openai/gpt-4.1-mini",
                "reading");

            registry.persistStateIfChanged();

            assertThat(Files.exists(path)).isTrue();
            AiPromptStore.PersistedState loaded = new AiPromptStore(new ObjectMapper(), path).loadState();
            assertThat(loaded.getDialogState().getSelectedPromptName()).isEmpty();
            assertThat(loaded.getDialogState().getDraft().getPrompt()).isEqualTo("Draft prompt");
            assertThat(loaded.getDialogState().getDraft().isShowInChat()).isTrue();
            assertThat(loaded.getDialogState().getDraft().getModelSelectionValue())
                .isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(loaded.getDialogState().getDraft().getToolAvailabilitySelectionValue())
                .isEqualTo("reading");
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void runPrompt_withoutOwnerPassesPromptCopyAndNullOwnerToChatPanel() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            AIChatPanel aiChatPanel = mock(AIChatPanel.class);
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                aiChatPanel,
                () -> {
                });
            AiPrompt prompt = new AiPrompt("Rewrite", "Prompt", false);

            registry.runPrompt(prompt);

            verify(aiChatPanel).runPrompt(
                argThat(copiedPrompt -> copiedPrompt != null
                    && copiedPrompt != prompt
                    && copiedPrompt.getName().equals("Rewrite")
                    && copiedPrompt.getPrompt().equals("Prompt")
                    && !copiedPrompt.isShowInChat()),
                isNull());
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void runPrompt_withOwnerPassesPromptCopyAndOwnerToChatPanel() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            AIChatPanel aiChatPanel = mock(AIChatPanel.class);
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                aiChatPanel,
                () -> {
                });
            AiPrompt prompt = new AiPrompt("Rewrite", "Prompt", false);
            JPanel owner = new JPanel();

            registry.runPrompt(prompt, owner);

            verify(aiChatPanel).runPrompt(
                argThat(copiedPrompt -> copiedPrompt != null
                    && copiedPrompt != prompt
                    && copiedPrompt.getName().equals("Rewrite")
                    && copiedPrompt.getPrompt().equals("Prompt")
                    && !copiedPrompt.isShowInChat()),
                same(owner));
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void findSavedPromptByName_trimsAndCaseNormalizesAndReturnsDefensiveCopy() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            AIChatPanel aiChatPanel = mock(AIChatPanel.class);
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                aiChatPanel,
                () -> {
                });
            registry.getDialogState().loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
            registry.persistStateIfChanged();

            AiPrompt found = registry.findSavedPromptByName("  rewrite  ");
            found.setPrompt("Changed");

            assertThat(found.getPrompt()).isEqualTo("Changed");
            assertThat(registry.findSavedPromptByName("Rewrite").getPrompt()).isEqualTo("Prompt");
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void findSavedPromptByName_ignoresUnsavedDraftState() throws IOException {
        Path tempDir = Files.createTempDirectory("ai-prompts");
        try {
            AIChatPanel aiChatPanel = mock(AIChatPanel.class);
            Path path = tempDir.resolve(AiPromptStore.PROMPTS_FILE_NAME);
            AiPromptActionRegistry registry = new AiPromptActionRegistry(
                new AiPromptStore(new ObjectMapper(), path),
                aiChatPanel,
                () -> {
                });
            registry.getDialogState().loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
            registry.persistStateIfChanged();
            registry.getDialogState().beginNewDraft();
            registry.getDialogState().updateDraft("Draft only", "Draft prompt", true, "", "reading");

            assertThat(registry.findSavedPromptByName("Draft only")).isNull();
            assertThat(registry.findSavedPromptByName("Rewrite")).isNotNull();
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
}

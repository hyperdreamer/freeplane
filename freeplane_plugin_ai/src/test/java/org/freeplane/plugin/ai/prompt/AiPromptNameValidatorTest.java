package org.freeplane.plugin.ai.prompt;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiPromptNameValidatorTest {
    private static final String DEFAULT_NEW_PROMPT_NAME = "New Prompt";

    @Test
    public void normalizeAndDeduplicate_trimsPromptNames() {
        List<AiPrompt> prompts = Arrays.asList(new AiPrompt("  Rewrite node  ", "Prompt", true));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("Rewrite node");
        assertThat(normalized.get(0).getPrompt()).isEqualTo("Prompt");
        assertThat(normalized.get(0).isShowInChat()).isTrue();
    }

    @Test
    public void normalizeAndDeduplicate_keepsExplicitNewPromptNameWhenUnique() {
        List<AiPrompt> prompts = Arrays.asList(new AiPrompt("New Prompt", "Prompt", false));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("New Prompt");
    }

    @Test
    public void normalizeAndDeduplicate_usesDefaultNameForEmptyNames() {
        List<AiPrompt> prompts = Arrays.asList(new AiPrompt("   ", "Prompt", false));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("New Prompt");
    }

    @Test
    public void normalizeAndDeduplicate_autoNumbersDuplicatePromptNamesCaseInsensitively() {
        List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("Rewrite", "Prompt", false),
            new AiPrompt("rewrite", "Other", true));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("Rewrite", "rewrite 1");
    }

    @Test
    public void normalizeAndDeduplicate_autoNumbersNewPromptWhenDefaultNameAlreadyExists() {
        List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("New Prompt", "Prompt", false),
            new AiPrompt("   ", "Other", true));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("New Prompt", "New Prompt 1");
    }

    @Test
    public void normalizeAndDeduplicate_preservesUniqueExplicitNumberedNames() {
        List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("Rewrite", "Prompt", false),
            new AiPrompt("Rewrite 1", "Other", true),
            new AiPrompt("Rewrite", "Third", false));

        List<AiPrompt> normalized = AiPromptNameValidator.normalizeAndDeduplicate(
            prompts, DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized)
            .extracting(AiPrompt::getName)
            .containsExactly("Rewrite", "Rewrite 1", "Rewrite 2");
    }

    @Test
    public void normalizeForSave_resolvesNameAgainstExistingPrompts() {
        AiPrompt normalized = AiPromptNameValidator.normalizeForSave(
            new AiPrompt("Rewrite", "Prompt", false),
            Arrays.asList(new AiPrompt("Rewrite", "Other", true)),
            DEFAULT_NEW_PROMPT_NAME);

        assertThat(normalized.getName()).isEqualTo("Rewrite 1");
    }
}

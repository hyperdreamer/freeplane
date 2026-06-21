package org.freeplane.plugin.ai.chat.ui;

import java.util.Arrays;
import java.util.List;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PromptReferenceResolverTest {
    private final PromptReferenceResolver resolver = new PromptReferenceResolver();

    @Test
    public void completionCandidates_requireLeadingSlashAndFilterFromWordStart() {
        List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("Summarize branch", "Prompt", false),
            new AiPrompt("Rewrite selected node", "Other", true));

        assertThat(resolver.completionCandidates("", 0, prompts)).isEmpty();
        assertThat(resolver.completionCandidates("/", 1, prompts))
            .extracting(AiPrompt::getName)
            .containsExactly("Summarize branch", "Rewrite selected node");
        assertThat(resolver.completionCandidates("/branch", 7, prompts))
            .extracting(AiPrompt::getName)
            .containsExactly("Summarize branch");
        assertThat(resolver.completionCandidates("/SEL", 4, prompts))
            .extracting(AiPrompt::getName)
            .containsExactly("Rewrite selected node");
        assertThat(resolver.completionCandidates("/node", 5, prompts))
            .extracting(AiPrompt::getName)
            .containsExactly("Rewrite selected node");
        assertThat(resolver.completionCandidates("/t", 2, prompts)).isEmpty();
        assertThat(resolver.completionCandidates("/missing", 8, prompts)).isEmpty();
    }

    @Test
    public void resolveLeadingReference_usesLongestPromptNameWithBoundaryAndPreservesSuffix() {
        List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("Summarize", "Short prompt", false),
            new AiPrompt("Summarize branch", "Long prompt", false));

        PromptReferenceMatch match = resolver.resolveLeadingReference(
            "/summarize branch for release notes",
            prompts);

        assertThat(match).isNotNull();
        assertThat(match.getPromptName()).isEqualTo("Summarize branch");
        assertThat(match.getReferenceStartOffset()).isZero();
        assertThat(match.getReferenceEndOffset()).isEqualTo("/Summarize branch".length());
        assertThat(match.getModelFacingText()).isEqualTo("Long prompt for release notes");
    }

    @Test
    public void resolveLeadingReference_treatsUnmatchedOrJoinedSlashTextAsOrdinaryText() {
        List<AiPrompt> prompts = Arrays.asList(new AiPrompt("Summarize", "Prompt", false));

        assertThat(resolver.resolveLeadingReference("/Summarizes", prompts)).isNull();
        assertThat(resolver.resolveLeadingReference("/Missing prompt", prompts)).isNull();
        assertThat(resolver.resolveLeadingReference("not /Summarize", prompts)).isNull();
    }

    @Test
    public void promptRelevantChange_usesFirstCharacterAndLeadingScanWindow() {
        List<AiPrompt> prompts = Arrays.asList(new AiPrompt("Summarize branch", "Prompt", false));
        String oldText = "/Summarize branch existing suffix";
        String newText = "/Summarize branch existing suffix!";

        assertThat(resolver.isPromptRelevantChange("text", "/text", 0, prompts)).isTrue();
        assertThat(resolver.isPromptRelevantChange(oldText, newText, newText.length() - 1, prompts)).isFalse();
        assertThat(resolver.isPromptRelevantChange(oldText, "/Summarize bran", 10, prompts)).isTrue();
    }
}

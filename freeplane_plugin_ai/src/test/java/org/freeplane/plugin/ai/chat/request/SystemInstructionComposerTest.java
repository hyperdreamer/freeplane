package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemInstructionComposerTest {
    @Test
    public void visibleRequestIncludesBaseToolProfileMarkdownAndCodeGuidance() {
        SystemInstructionComposer uut = new SystemInstructionComposer();

        String message = uut.compose(new SystemInstructionContext(
            " base ",
            ToolAvailabilityLevel.READING,
            RequestVisibility.VISIBLE,
            true,
            " code guidance "));

        assertThat(message).startsWith("base\n\n");
        assertThat(message).contains("Available Freeplane tools are limited to reading");
        assertThat(message).contains("Profile changes are communicated");
        assertThat(message).contains("Respond in Markdown.");
        assertThat(message).contains("code guidance");
    }

    @Test
    public void hiddenRequestOmitsMarkdownButKeepsDynamicGuidance() {
        SystemInstructionComposer uut = new SystemInstructionComposer();

        String message = uut.compose(new SystemInstructionContext(
            "base",
            ToolAvailabilityLevel.DISABLED,
            RequestVisibility.HIDDEN,
            false,
            null));

        assertThat(message).contains("base");
        assertThat(message).contains("No application tools are available");
        assertThat(message).doesNotContain("Respond in Markdown.");
        assertThat(message).doesNotContain("Profile changes are communicated");
    }

    @Test
    public void profileControlGuidanceIsConditional() {
        SystemInstructionComposer uut = new SystemInstructionComposer();

        String withoutProfile = uut.compose(new SystemInstructionContext(
            "base",
            ToolAvailabilityLevel.EDITING,
            RequestVisibility.VISIBLE,
            false,
            null));
        String withProfile = uut.compose(new SystemInstructionContext(
            "base",
            ToolAvailabilityLevel.EDITING,
            RequestVisibility.VISIBLE,
            true,
            null));

        assertThat(withoutProfile).doesNotContain("Profile changes are communicated");
        assertThat(withProfile).contains("Profile changes are communicated");
    }
}

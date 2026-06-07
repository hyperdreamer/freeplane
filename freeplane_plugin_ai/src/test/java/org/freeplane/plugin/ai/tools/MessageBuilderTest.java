package org.freeplane.plugin.ai.tools;

import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageBuilderTest {
    private static final String MARKDOWN_RESPONSE_GUIDANCE = "Respond in Markdown.";
    private static final String TOOL_CALL_GUIDANCE_TEXT =
        "Any tool calls in this chat require arguments wrapped under the single parameter named request. ";
    private static final String TOOL_CALL_EXAMPLE_TEXT = "Example: tool({ \"request\": { ... } })";
    private static final String CONTROL_INSTRUCTION_GUIDANCE_PREFIX =
        "Control instructions start with: " + MessageBuilder.CONTROL_INSTRUCTION_PREFIX;
    private static final String NO_TOOLS_GUIDANCE_PREFIX =
        "No application tools are available in this chat.";
    private static final String MAP_SELECTION_GUIDANCE_PREFIX =
        "Map selection can change between messages.";
    private static final String READ_ONLY_FREEPLANE_GUIDANCE_PREFIX =
        "Available Freeplane tools are limited to reading, searching, and node selection.";
    private static final String DO_NOT_CHANGE_MAP_GUIDANCE = "Do not change the map.";

    @Test
    public void buildForChat_returnsConfiguredMessage() {
        String configuredMessage = "Use tools when needed.";
        MessageBuilder.MessageTextProvider textProvider = () -> configuredMessage;
        MessageBuilder uut = new MessageBuilder(textProvider);

        String message = uut.buildForChat(ToolAvailabilityLevel.EDITING);

        assertThat(message).contains(configuredMessage);
        assertThat(message).contains(MARKDOWN_RESPONSE_GUIDANCE);
        assertThat(message).contains(TOOL_CALL_GUIDANCE_TEXT);
        assertThat(message).contains(TOOL_CALL_EXAMPLE_TEXT);
        assertThat(message).contains(CONTROL_INSTRUCTION_GUIDANCE_PREFIX);
        assertThat(message).contains(MAP_SELECTION_GUIDANCE_PREFIX);
        assertThat(message).doesNotContain(NO_TOOLS_GUIDANCE_PREFIX);
    }

    @Test
    public void buildForChat_returnsEmptyWhenBlank() {
        MessageBuilder.MessageTextProvider textProvider = () -> "  ";
        MessageBuilder uut = new MessageBuilder(textProvider);

        String message = uut.buildForChat(ToolAvailabilityLevel.EDITING);

        assertThat(message).contains(MARKDOWN_RESPONSE_GUIDANCE);
        assertThat(message).contains(TOOL_CALL_GUIDANCE_TEXT);
        assertThat(message).contains(TOOL_CALL_EXAMPLE_TEXT);
        assertThat(message).contains(CONTROL_INSTRUCTION_GUIDANCE_PREFIX);
    }

    @Test
    public void buildForChat_returnsEmptyWhenNull() {
        MessageBuilder.MessageTextProvider textProvider = () -> null;
        MessageBuilder uut = new MessageBuilder(textProvider);

        String message = uut.buildForChat(ToolAvailabilityLevel.EDITING);

        assertThat(message).contains(MARKDOWN_RESPONSE_GUIDANCE);
        assertThat(message).contains(TOOL_CALL_GUIDANCE_TEXT);
        assertThat(message).contains(TOOL_CALL_EXAMPLE_TEXT);
        assertThat(message).contains(CONTROL_INSTRUCTION_GUIDANCE_PREFIX);
    }

    @Test
    public void buildForChat_omitsToolGuidanceWhenToolAvailabilityIsDisabled() {
        MessageBuilder.MessageTextProvider textProvider = () -> "Use tools when needed.";
        MessageBuilder uut = new MessageBuilder(textProvider);

        String message = uut.buildForChat(ToolAvailabilityLevel.DISABLED);

        assertThat(message).contains(MARKDOWN_RESPONSE_GUIDANCE);
        assertThat(message).contains(CONTROL_INSTRUCTION_GUIDANCE_PREFIX);
        assertThat(message).contains(NO_TOOLS_GUIDANCE_PREFIX);
        assertThat(message).doesNotContain(TOOL_CALL_GUIDANCE_TEXT);
        assertThat(message).doesNotContain(TOOL_CALL_EXAMPLE_TEXT);
        assertThat(message).doesNotContain(MAP_SELECTION_GUIDANCE_PREFIX);
        assertThat(message).doesNotContain("Freeplane");
    }

    @Test
    public void buildForChat_addsReadOnlyGuidanceWhenToolAvailabilityIsReading() {
        MessageBuilder.MessageTextProvider textProvider = () -> "Use tools when needed.";
        MessageBuilder uut = new MessageBuilder(textProvider);

        String message = uut.buildForChat(ToolAvailabilityLevel.READING);

        assertThat(message).contains(MARKDOWN_RESPONSE_GUIDANCE);
        assertThat(message).contains(CONTROL_INSTRUCTION_GUIDANCE_PREFIX);
        assertThat(message).contains(MAP_SELECTION_GUIDANCE_PREFIX);
        assertThat(message).contains(TOOL_CALL_GUIDANCE_TEXT);
        assertThat(message).contains(TOOL_CALL_EXAMPLE_TEXT);
        assertThat(message).contains(READ_ONLY_FREEPLANE_GUIDANCE_PREFIX);
        assertThat(message).contains(DO_NOT_CHANGE_MAP_GUIDANCE);
        assertThat(message).doesNotContain(NO_TOOLS_GUIDANCE_PREFIX);
    }

    @Test
    public void buildAssistantProfileInstruction_returnsNameAndDefinition() {
        String message = MessageBuilder.buildAssistantProfileInstruction("Analyst", "Be strict.", true);

        assertThat(message).isEqualTo("Now you have the profile Analyst.\nProfile definition: Be strict.");
    }

    @Test
    public void buildAssistantProfileInstruction_returnsFallbackForEmptyDefinition() {
        String message = MessageBuilder.buildAssistantProfileInstruction("Analyst", "  ", true);

        assertThat(message).isEqualTo("Now you have the profile Analyst.");
    }

    @Test
    public void buildAssistantProfileInstruction_nonCurrentProfile_keepsOnlyProfileName() {
        String historical = MessageBuilder.buildAssistantProfileInstruction("Analyst", "Be strict.", false);

        assertThat(historical).isEqualTo("Now you have the profile Analyst.");
    }

}

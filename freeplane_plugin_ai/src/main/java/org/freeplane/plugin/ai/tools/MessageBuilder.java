package org.freeplane.plugin.ai.tools;

import dev.langchain4j.data.message.UserMessage;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;

public class MessageBuilder {
    public static final String SYSTEM_MESSAGE_PROPERTY = "ai_system_message";
    public static final String CONTROL_INSTRUCTION_PREFIX =
        "control instruction, please confirm with \"ok\": ";
    private static final String TOOL_CALL_REQUEST_WRAPPER_GUIDANCE =
        "Any tool calls in this chat require arguments wrapped under the single parameter named request. "
            + "Example: tool({ \"request\": { ... } })";
    private static final String MARKDOWN_RESPONSE_GUIDANCE = "Respond in Markdown.";
    private static final String NO_TOOLS_GUIDANCE =
        "No application tools are available in this chat. Do not call or invent tools.";
    private static final String MAP_SELECTION_GUIDANCE =
        "Map selection can change between messages. If a request seems misaligned with prior map references, "
            + "confirm the current map before proceeding.";
    private static final String READ_ONLY_FREEPLANE_GUIDANCE =
        "Available Freeplane tools are limited to reading, searching, and node selection. "
            + "Do not change the map.";
    private static final String PROFILE_CONTROL_GUIDANCE =
        "Control instructions start with: " + CONTROL_INSTRUCTION_PREFIX
            + "Profile changes are communicated through these control instructions. "
            + "Treat the latest profile change as authoritative.";
    
    @FunctionalInterface
    interface MessageTextProvider {
        String getMessageText();
    }

    private final MessageTextProvider messageTextProvider;

    public MessageBuilder() {
        this(new ResourceControllerMessageTextProvider());
    }

    MessageBuilder(MessageTextProvider messageTextProvider) {
        this.messageTextProvider = messageTextProvider;
    }

    public String buildForChat() {
        ToolAvailabilityLevel toolAvailability = ToolAvailabilityLevel.EDITING;
        try {
            ResourceController rc = ResourceController.getResourceController();
            if (rc != null) {
                toolAvailability = new ToolAvailabilityLevelSettings().getToolAvailability();
            }
        } catch (Exception ignored) {
            // In test or non-UI environments, ResourceController may not be available
        }
        return buildForChat(toolAvailability);
    }

    public String buildForChat(ToolAvailabilityLevel toolAvailability) {
        return buildForChat(messageTextProvider.getMessageText(), toolAvailability);
    }

    public String buildForChat(String systemMessage, ToolAvailabilityLevel toolAvailability) {
        return buildForChat(systemMessage, toolAvailability, true, true);
    }

    public String buildForChat(String systemMessage,
                               ToolAvailabilityLevel toolAvailability,
                               boolean includeProfileControlGuidance,
                               boolean includeMarkdownResponseGuidance) {
        ToolAvailabilityLevel normalizedAvailability = toolAvailability == null
            ? ToolAvailabilityLevel.EDITING
            : toolAvailability;
        String message = systemMessage;
        String guidance = buildGuidance(
            normalizedAvailability,
            includeProfileControlGuidance,
            includeMarkdownResponseGuidance);
        if (message == null) {
            return guidance;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return guidance;
        }
        if (guidance == null || guidance.trim().isEmpty()) {
            return trimmed;
        }
        return trimmed + "\n\n" + guidance;
    }

    private String buildGuidance(ToolAvailabilityLevel toolAvailability,
                                 boolean includeProfileControlGuidance,
                                 boolean includeMarkdownResponseGuidance) {
        StringBuilder guidance = new StringBuilder();
        if (toolAvailability == ToolAvailabilityLevel.DISABLED) {
            appendGuidance(guidance, NO_TOOLS_GUIDANCE);
            appendOptionalCommonGuidance(guidance, includeProfileControlGuidance, includeMarkdownResponseGuidance);
            return guidance.toString();
        }
        if (toolAvailability == ToolAvailabilityLevel.READING) {
            appendGuidance(guidance, MAP_SELECTION_GUIDANCE);
            appendGuidance(guidance, READ_ONLY_FREEPLANE_GUIDANCE);
            appendOptionalCommonGuidance(guidance, includeProfileControlGuidance, includeMarkdownResponseGuidance);
            appendGuidance(guidance, TOOL_CALL_REQUEST_WRAPPER_GUIDANCE);
            return guidance.toString();
        }
        appendGuidance(guidance, MAP_SELECTION_GUIDANCE);
        appendOptionalCommonGuidance(guidance, includeProfileControlGuidance, includeMarkdownResponseGuidance);
        appendGuidance(guidance, TOOL_CALL_REQUEST_WRAPPER_GUIDANCE);
        return guidance.toString();
    }

    private void appendOptionalCommonGuidance(StringBuilder guidance,
                                              boolean includeProfileControlGuidance,
                                              boolean includeMarkdownResponseGuidance) {
        if (includeProfileControlGuidance) {
            appendGuidance(guidance, PROFILE_CONTROL_GUIDANCE);
        }
        if (includeMarkdownResponseGuidance) {
            appendGuidance(guidance, MARKDOWN_RESPONSE_GUIDANCE);
        }
    }

    private void appendGuidance(StringBuilder guidance, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (guidance.length() > 0) {
            guidance.append("\n\n");
        }
        guidance.append(text.trim());
    }

    public static String configuredSystemMessage() {
        try {
            ResourceController resourceController = ResourceController.getResourceController();
            if (resourceController == null) {
                return "";
            }
            String message = resourceController.getProperty(SYSTEM_MESSAGE_PROPERTY);
            return message == null ? "" : message.trim();
        } catch (RuntimeException error) {
            return "";
        }
    }

    public static String buildAssistantProfileInstruction(String profileName,
                                                          String profileDefinition,
                                                          boolean containsProfileDefinition) {
        String marker = buildAssistantProfileMarker(profileName);
        if (!containsProfileDefinition) {
            return marker;
        }
        String definition = profileDefinition == null ? "" : profileDefinition.trim();
        if (definition.isEmpty()) {
            return marker;
        }
        return marker + "\nProfile definition: " + definition;
    }

    public static String buildAssistantProfileMarker(String profileName) {
        String name = profileName == null ? "" : profileName.trim();
        if (name.isEmpty()) {
            return "Now you have the profile.";
        }
        return "Now you have the profile " + name + ".";
    }

    public static UserMessage buildSystemInstructionUserMessage(String text) {
        return UserMessage.from(buildSystemInstructionText(text));
    }

    public static String buildSystemInstructionText(String text) {
        return CONTROL_INSTRUCTION_PREFIX + (text == null ? "" : text);
    }

    public static String buildInstructionAcknowledgementText() {
        return "ok";
    }

    private static class ResourceControllerMessageTextProvider implements MessageTextProvider {

        @Override
        public String getMessageText() {
            ResourceController resourceController = ResourceController.getResourceController();
            String message = resourceController.getProperty(SYSTEM_MESSAGE_PROPERTY);
            return message;
        }
    }
}

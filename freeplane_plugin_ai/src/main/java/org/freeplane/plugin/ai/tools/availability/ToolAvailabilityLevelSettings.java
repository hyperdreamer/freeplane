package org.freeplane.plugin.ai.tools.availability;

import java.util.Objects;
import org.freeplane.core.resources.ResourceController;

public class ToolAvailabilityLevelSettings {
    public static final String TOOL_AVAILABILITY_PROPERTY = "ai_tool_availability";
    public static final String LEGACY_CHAT_TOOL_AVAILABILITY_PROPERTY = "ai_chat_tool_availability";

    private final ResourceController resourceController;

    public ToolAvailabilityLevelSettings() {
        this(ResourceController.getResourceController());
    }

    public ToolAvailabilityLevelSettings(ResourceController resourceController) {
        this.resourceController = Objects.requireNonNull(resourceController, "resourceController");
    }

    public ToolAvailabilityLevel getToolAvailability() {
        String canonicalValue = resourceController.getProperty(TOOL_AVAILABILITY_PROPERTY, null);
        if (canonicalValue != null && !canonicalValue.trim().isEmpty()) {
            return resourceController.getEnumProperty(TOOL_AVAILABILITY_PROPERTY, ToolAvailabilityLevel.EDITING);
        }
        return ToolAvailabilityLevel.fromPreferenceValue(
            resourceController.getProperty(LEGACY_CHAT_TOOL_AVAILABILITY_PROPERTY, null));
    }
}

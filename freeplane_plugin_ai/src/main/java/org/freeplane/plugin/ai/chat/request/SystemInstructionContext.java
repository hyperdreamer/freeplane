package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

public final class SystemInstructionContext {
    private final String baseSystemMessage;
    private final boolean isSystemMessageExact;
    private final ToolAvailabilityLevel toolAvailability;
    private final RequestVisibility visibility;
    private final boolean hasProfileInstruction;
    private final String codeHostGuidance;

    public SystemInstructionContext(String baseSystemMessage,
                                    boolean isSystemMessageExact,
                                    ToolAvailabilityLevel toolAvailability,
                                    RequestVisibility visibility,
                                    boolean hasProfileInstruction,
                                    String codeHostGuidance) {
        this.baseSystemMessage = normalizeNullable(baseSystemMessage);
        this.isSystemMessageExact = isSystemMessageExact;
        this.toolAvailability = toolAvailability;
        this.visibility = visibility == null ? RequestVisibility.VISIBLE : visibility;
        this.hasProfileInstruction = hasProfileInstruction;
        this.codeHostGuidance = normalizeNullable(codeHostGuidance);
    }

    String getBaseSystemMessage() {
        return baseSystemMessage;
    }

    boolean isSystemMessageExact() {
        return isSystemMessageExact;
    }

    ToolAvailabilityLevel getToolAvailability() {
        return toolAvailability;
    }

    RequestVisibility getVisibility() {
        return visibility;
    }

    boolean hasProfileInstruction() {
        return hasProfileInstruction;
    }

    String getCodeHostGuidance() {
        return codeHostGuidance;
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}

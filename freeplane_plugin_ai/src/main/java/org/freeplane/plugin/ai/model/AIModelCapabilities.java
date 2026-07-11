package org.freeplane.plugin.ai.model;

import java.util.Objects;

public final class AIModelCapabilities {
    public static final AIModelCapabilities UNKNOWN = new AIModelCapabilities(
        CapabilitySupport.UNKNOWN,
        CapabilitySupport.UNKNOWN);

    private final CapabilitySupport textOutput;
    private final CapabilitySupport toolCalling;

    public AIModelCapabilities(CapabilitySupport textOutput, CapabilitySupport toolCalling) {
        this.textOutput = Objects.requireNonNull(textOutput, "textOutput");
        this.toolCalling = Objects.requireNonNull(toolCalling, "toolCalling");
    }

    public CapabilitySupport getTextOutput() {
        return textOutput;
    }

    public CapabilitySupport getToolCalling() {
        return toolCalling;
    }

    public boolean isToolCapableTextModel() {
        return textOutput == CapabilitySupport.SUPPORTED
            && toolCalling == CapabilitySupport.SUPPORTED;
    }

    public boolean hasUnknownCapability() {
        return textOutput == CapabilitySupport.UNKNOWN
            || toolCalling == CapabilitySupport.UNKNOWN;
    }

    public boolean hasUnsupportedCapability() {
        return textOutput == CapabilitySupport.UNSUPPORTED
            || toolCalling == CapabilitySupport.UNSUPPORTED;
    }
}

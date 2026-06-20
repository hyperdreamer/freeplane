package org.freeplane.plugin.ai.chat.request;

import org.freeplane.plugin.ai.tools.MessageBuilder;

public final class SystemInstructionComposer {
    private final MessageBuilder messageBuilder;

    public SystemInstructionComposer() {
        this(new MessageBuilder());
    }

    SystemInstructionComposer(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder == null ? new MessageBuilder() : messageBuilder;
    }

    public String compose(SystemInstructionContext context) {
        if (context == null) {
            return "";
        }
        String baseMessage = messageBuilder.buildForChat(
            context.getBaseSystemMessage(),
            context.getToolAvailability(),
            context.hasProfileInstruction(),
            context.getVisibility() == RequestVisibility.VISIBLE);
        return appendGuidance(baseMessage, context.getCodeHostGuidance());
    }

    private String appendGuidance(String baseMessage, String extraGuidance) {
        String safeBase = baseMessage == null ? "" : baseMessage.trim();
        String safeExtra = extraGuidance == null ? "" : extraGuidance.trim();
        if (safeBase.isEmpty()) {
            return safeExtra;
        }
        if (safeExtra.isEmpty()) {
            return safeBase;
        }
        return safeBase + "\n\n" + safeExtra;
    }
}

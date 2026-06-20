package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;

public class AssistantProfileSwitchMessage extends UserMessage {
    private final String profileId;
    private final String profileName;
    private final String profileMessage;

    public AssistantProfileSwitchMessage(String profileId, String profileName) {
        this(profileId, profileName, MessageBuilder.buildAssistantProfileMarker(profileName));
    }

    public AssistantProfileSwitchMessage(String profileId, String profileName, String profileMessage) {
        super(normalizeProfileMessage(profileName, profileMessage));
        this.profileId = profileId == null ? "" : profileId.trim();
        this.profileName = profileName == null ? "" : profileName.trim();
        this.profileMessage = normalizeProfileMessage(this.profileName, profileMessage);
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getProfileMessage() {
        return profileMessage;
    }

    private static String normalizeProfileMessage(String profileName, String profileMessage) {
        String normalized = profileMessage == null ? "" : profileMessage.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return MessageBuilder.buildAssistantProfileMarker(profileName);
    }
}

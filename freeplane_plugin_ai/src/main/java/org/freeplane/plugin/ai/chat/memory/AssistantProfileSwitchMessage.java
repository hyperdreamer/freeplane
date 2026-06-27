package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.tools.MessageBuilder;

public class AssistantProfileSwitchMessage extends UserMessage {
    private final String profileId;
    private final String profileName;
    private final String profileMessage;
    private final AIModelConfiguration modelConfiguration;

    public AssistantProfileSwitchMessage(String profileId, String profileName) {
        this(profileId, profileName, MessageBuilder.buildAssistantProfileMarker(profileName), null);
    }

    public AssistantProfileSwitchMessage(String profileId, String profileName, String profileMessage) {
        this(profileId, profileName, profileMessage, null);
    }

    public AssistantProfileSwitchMessage(String profileId,
                                         String profileName,
                                         String profileMessage,
                                         AIModelConfiguration modelConfiguration) {
        super(normalizeProfileMessage(profileName, profileMessage));
        this.profileId = profileId == null ? "" : profileId.trim();
        this.profileName = profileName == null ? "" : profileName.trim();
        this.profileMessage = normalizeProfileMessage(this.profileName, profileMessage);
        this.modelConfiguration = normalizeModelConfiguration(modelConfiguration);
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

    public AIModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    private static String normalizeProfileMessage(String profileName, String profileMessage) {
        String normalized = profileMessage == null ? "" : profileMessage.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return MessageBuilder.buildAssistantProfileMarker(profileName);
    }

    private static AIModelConfiguration normalizeModelConfiguration(AIModelConfiguration modelConfiguration) {
        if (modelConfiguration == null) {
            return null;
        }
        if (modelConfiguration.getModelSelection() == null
            && modelConfiguration.getThinkingEffort() == null
            && modelConfiguration.getTemperature() == null) {
            return null;
        }
        return modelConfiguration;
    }
}

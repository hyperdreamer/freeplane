package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;

public class AssistantProfileSwitchMessage extends UserMessage {
    private final String profileId;
    private final String profileName;

    public AssistantProfileSwitchMessage(String profileId, String profileName) {
        super(MessageBuilder.buildAssistantProfileMarker(profileName));
        this.profileId = profileId == null ? "" : profileId.trim();
        this.profileName = profileName == null ? "" : profileName.trim();
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }
}

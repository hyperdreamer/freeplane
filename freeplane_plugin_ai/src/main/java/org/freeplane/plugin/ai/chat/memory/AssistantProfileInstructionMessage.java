package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.UserMessage;
import org.freeplane.plugin.ai.tools.MessageBuilder;

public class AssistantProfileInstructionMessage extends UserMessage {
    private final String profileId;
    private final String profileName;

    public AssistantProfileInstructionMessage(String profileId,
                                              String profileName,
                                              String profileDefinition) {
        super(MessageBuilder.buildAssistantProfileInstruction(profileName, profileDefinition, true));
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

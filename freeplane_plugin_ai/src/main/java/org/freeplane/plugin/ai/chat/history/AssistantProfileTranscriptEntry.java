package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.plugin.ai.model.AIModelConfiguration;

public class AssistantProfileTranscriptEntry extends ChatTranscriptEntry {
    private String profileId;
    private String profileName;
    private String profileMessage;
    private AIModelConfiguration modelConfiguration;
    private boolean containsProfileDefinition;

    public AssistantProfileTranscriptEntry() {
        setRole(ChatTranscriptRole.ASSISTANT_PROFILE_SYSTEM);
    }

    public AssistantProfileTranscriptEntry(String profileId,
                                           String profileName,
                                           boolean containsProfileDefinition) {
        this(profileId, profileName, null, containsProfileDefinition);
    }

    public AssistantProfileTranscriptEntry(String profileId,
                                           String profileName,
                                           String profileMessage,
                                           boolean containsProfileDefinition) {
        this(profileId, profileName, profileMessage, null, containsProfileDefinition);
    }

    public AssistantProfileTranscriptEntry(String profileId,
                                           String profileName,
                                           String profileMessage,
                                           AIModelConfiguration modelConfiguration,
                                           boolean containsProfileDefinition) {
        this();
        this.profileId = profileId;
        this.profileName = profileName;
        this.profileMessage = profileMessage;
        this.modelConfiguration = normalizeModelConfiguration(modelConfiguration);
        this.containsProfileDefinition = containsProfileDefinition;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getProfileMessage() {
        return profileMessage;
    }

    public void setProfileMessage(String profileMessage) {
        this.profileMessage = profileMessage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AIModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    public void setModelConfiguration(AIModelConfiguration modelConfiguration) {
        this.modelConfiguration = normalizeModelConfiguration(modelConfiguration);
    }

    @JsonProperty("containsProfileDefinition")
    public boolean containsProfileDefinition() {
        return containsProfileDefinition;
    }

    @JsonProperty("containsProfileDefinition")
    public void setContainsProfileDefinition(boolean containsProfileDefinition) {
        this.containsProfileDefinition = containsProfileDefinition;
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

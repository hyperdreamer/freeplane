package org.freeplane.plugin.ai.chat.profile;

import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileInstructionMessage;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.session.LiveChatController;
import org.freeplane.plugin.ai.tools.MessageBuilder;

public class AssistantProfileSelectionSync {
    private final AssistantProfileSelectionModel selectionModel;
    private final LiveChatController liveChatController;
    private ChatMemory chatMemory;
    private Consumer<String> profileMessageConsumer;
    private Runnable previewRefreshListener;
    private AssistantProfile pendingProfile;

    public AssistantProfileSelectionSync(AssistantProfileSelectionModel selectionModel, LiveChatController liveChatController) {
        this.selectionModel = selectionModel;
        this.liveChatController = liveChatController;
    }

    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        if (chatMemory instanceof AssistantProfileChatMemory) {
            AssistantProfileChatMemory assistantProfileChatMemory =
                (AssistantProfileChatMemory) chatMemory;
            assistantProfileChatMemory.setProfileInstructionFactory(profileSwitchMessage -> {
                if (profileSwitchMessage == null) {
                    return null;
                }
                return new AssistantProfileInstructionMessage(
                    profileSwitchMessage.getProfileId(),
                    profileSwitchMessage.getProfileName(),
                    profileSwitchMessage.getProfileMessage());
            });
        }
    }

    public void setProfileMessageConsumer(Consumer<String> profileMessageConsumer) {
        this.profileMessageConsumer = profileMessageConsumer;
    }

    public void setPreviewRefreshListener(Runnable previewRefreshListener) {
        this.previewRefreshListener = previewRefreshListener;
    }

    void applyAssistantProfileSelection(AssistantProfile profile) {
        addProfileMessageIfDifferent(toProfileSwitchMessage(profile));
    }

    public boolean addProfileMessageIfDifferent(AssistantProfileSwitchMessage message) {
        if (message == null) {
            return false;
        }
        if (message.getProfileId().isEmpty()
            && message.getProfileName().isEmpty()
            && message.getProfileMessage().trim().isEmpty()) {
            return false;
        }
        if (isSameAsActiveProfileMessage(message)) {
            return false;
        }
        if (chatMemory != null) {
            chatMemory.add(message);
        }
        liveChatController.recordAssistantProfileMessage(message);
        if (profileMessageConsumer != null) {
            profileMessageConsumer.accept(message.getProfileName());
        }
        return true;
    }

    void handleUserSelection(AssistantProfile profile) {
        if (profile == null) {
            return;
        }
        selectionModel.setSelectedProfile(profile, true);
        pendingProfile = profile;
        notifyPreviewRefresh();
    }

    AssistantProfile selectForActivation(boolean fromTranscriptRestore) {
        List<ChatTranscriptEntry> entries = liveChatController.snapshotTranscriptEntries();
        AssistantProfileTranscriptEntry profileEntry = findLastAssistantProfileEntry(entries);
        AssistantProfile selected = selectionModel.getSelectedProfile();
        if (profileEntry == null && !fromTranscriptRestore) {
            pendingProfile = selected;
            return selected;
        }
        String transcriptProfileId = profileEntry == null ? "" : normalize(profileEntry.getProfileId());
        boolean transcriptProfileExists = false;
        if (!transcriptProfileId.isEmpty()) {
            AssistantProfile transcriptProfile = selectionModel.findProfileById(transcriptProfileId);
            if (transcriptProfile != null) {
                selectionModel.setSelectedProfile(transcriptProfile, false);
                selected = transcriptProfile;
                transcriptProfileExists = true;
            }
        }
        if (profileEntry != null && !transcriptProfileId.isEmpty() && !transcriptProfileExists) {
            pendingProfile = selected;
            return selected;
        }
        pendingProfile = shouldInjectSelectedProfile(profileEntry, selected) ? selected : null;
        return selected;
    }

    public void maybeInjectBeforeUserMessage() {
        if (pendingProfile == null) {
            return;
        }
        AssistantProfile profile = pendingProfile;
        pendingProfile = null;
        applyAssistantProfileSelection(profile);
        notifyPreviewRefresh();
    }

    public AssistantProfileSwitchMessage pendingProfileMessageIfDifferent() {
        AssistantProfileSwitchMessage message = toProfileSwitchMessage(pendingProfile);
        if (message == null || isSameAsActiveProfileMessage(message)) {
            return null;
        }
        return message;
    }

    public ProfileRequestResolution resolveRequestProfile(String profileName, String profileMessage) {
        if (profileName == null && profileMessage == null) {
            return ProfileRequestResolution.none();
        }
        String normalizedName = normalize(profileName);
        if (profileMessage == null) {
            if (normalizedName.isEmpty()) {
                return ProfileRequestResolution.configurationError("AI profile name must not be empty.");
            }
            List<AssistantProfile> matches = findProfilesByName(normalizedName);
            if (matches.isEmpty()) {
                return ProfileRequestResolution.configurationError(
                    "AI profile not found: " + normalizedName);
            }
            if (matches.size() > 1) {
                return ProfileRequestResolution.configurationError(
                    "AI profile name is ambiguous: " + normalizedName);
            }
            return ProfileRequestResolution.message(toProfileSwitchMessage(matches.get(0)));
        }
        String normalizedMessage = normalize(profileMessage);
        if (normalizedMessage.isEmpty() && normalizedName.isEmpty()) {
            return ProfileRequestResolution.none();
        }
        String effectiveMessage = normalizedMessage.isEmpty()
            ? MessageBuilder.buildAssistantProfileMarker(normalizedName)
            : normalizedMessage;
        return ProfileRequestResolution.message(
            new AssistantProfileSwitchMessage("", normalizedName, effectiveMessage));
    }

    private boolean shouldInjectSelectedProfile(AssistantProfileTranscriptEntry profileEntry,
                                                AssistantProfile selected) {
        if (selected == null) {
            return false;
        }
        AssistantProfileSwitchMessage selectedMessage = toProfileSwitchMessage(selected);
        if (selectedMessage == null) {
            return false;
        }
        if (profileEntry == null) {
            return true;
        }
        return !sameProfileMessage(
            normalize(profileEntry.getProfileName()),
            transcriptProfileMessage(profileEntry, selectedMessage),
            profileEntry.getModelConfiguration(),
            selectedMessage.getProfileName(),
            selectedMessage.getProfileMessage(),
            selectedMessage.getModelConfiguration());
    }

    private List<AssistantProfile> findProfilesByName(String profileName) {
        List<AssistantProfile> matches = new ArrayList<AssistantProfile>();
        List<AssistantProfile> profiles = selectionModel.getProfiles();
        if (profiles == null) {
            return matches;
        }
        for (AssistantProfile profile : profiles) {
            if (profileName.equals(normalize(profile.getName()))) {
                matches.add(profile);
            }
        }
        return matches;
    }

    private AssistantProfileSwitchMessage toProfileSwitchMessage(AssistantProfile profile) {
        if (profile == null) {
            return null;
        }
        String profileId = normalize(profile.getId());
        String profileName = normalize(profile.getName());
        String profilePrompt = normalize(profile.getPrompt());
        if (profileId.isEmpty() && profileName.isEmpty() && profilePrompt.isEmpty()) {
            return null;
        }
        String profileMessage = configuredProfileMessage(profileName, profilePrompt);
        return new AssistantProfileSwitchMessage(
            profileId,
            profileName,
            profileMessage,
            profile.getModelConfiguration());
    }

    private String configuredProfileMessage(String profileName, String profilePrompt) {
        String normalizedPrompt = normalize(profilePrompt);
        if (normalizedPrompt.isEmpty()) {
            return MessageBuilder.buildAssistantProfileMarker(profileName);
        }
        return MessageBuilder.buildAssistantProfileInstruction(profileName, normalizedPrompt, true);
    }

    private boolean isSameAsActiveProfileMessage(AssistantProfileSwitchMessage message) {
        if (!(chatMemory instanceof AssistantProfileChatMemory)) {
            return false;
        }
        AssistantProfileSwitchMessage activeMessage =
            ((AssistantProfileChatMemory) chatMemory).latestProfileSwitchMessage();
        return activeMessage != null
            && sameProfileMessage(
                activeMessage.getProfileName(),
                activeMessage.getProfileMessage(),
                activeMessage.getModelConfiguration(),
                message.getProfileName(),
                message.getProfileMessage(),
                message.getModelConfiguration());
    }

    private boolean sameProfileMessage(String leftName,
                                       String leftMessage,
                                       org.freeplane.plugin.ai.model.AIModelConfiguration leftModelConfiguration,
                                       String rightName,
                                       String rightMessage,
                                       org.freeplane.plugin.ai.model.AIModelConfiguration rightModelConfiguration) {
        return normalize(leftName).equals(normalize(rightName))
            && normalize(leftMessage).equals(normalize(rightMessage))
            && java.util.Objects.equals(leftModelConfiguration, rightModelConfiguration);
    }

    private String transcriptProfileMessage(AssistantProfileTranscriptEntry profileEntry,
                                            AssistantProfileSwitchMessage selectedMessage) {
        String profileMessage = normalize(profileEntry.getProfileMessage());
        if (!profileMessage.isEmpty()) {
            return profileMessage;
        }
        return selectedMessage == null
            ? MessageBuilder.buildAssistantProfileMarker(profileEntry.getProfileName())
            : selectedMessage.getProfileMessage();
    }

    private AssistantProfileTranscriptEntry findLastAssistantProfileEntry(List<ChatTranscriptEntry> entries) {
        if (entries == null) {
            return null;
        }
        for (int index = entries.size() - 1; index >= 0; index--) {
            ChatTranscriptEntry entry = entries.get(index);
            if (entry instanceof AssistantProfileTranscriptEntry) {
                return (AssistantProfileTranscriptEntry) entry;
            }
        }
        return null;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private void notifyPreviewRefresh() {
        if (previewRefreshListener != null) {
            previewRefreshListener.run();
        }
    }

    public static class ProfileRequestResolution {
        private final AssistantProfileSwitchMessage message;
        private final String configurationErrorDetail;

        private ProfileRequestResolution(AssistantProfileSwitchMessage message, String configurationErrorDetail) {
            this.message = message;
            this.configurationErrorDetail = configurationErrorDetail;
        }

        public static ProfileRequestResolution none() {
            return new ProfileRequestResolution(null, null);
        }

        public static ProfileRequestResolution message(AssistantProfileSwitchMessage message) {
            return new ProfileRequestResolution(message, null);
        }

        public static ProfileRequestResolution configurationError(String configurationErrorDetail) {
            return new ProfileRequestResolution(null, configurationErrorDetail);
        }

        public AssistantProfileSwitchMessage getMessage() {
            return message;
        }

        public String getConfigurationErrorDetail() {
            return configurationErrorDetail;
        }
    }
}

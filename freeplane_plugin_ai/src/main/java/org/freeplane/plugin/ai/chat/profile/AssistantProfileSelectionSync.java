package org.freeplane.plugin.ai.chat.profile;

import dev.langchain4j.memory.ChatMemory;
import java.util.List;
import java.util.function.Consumer;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileInstructionMessage;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.session.LiveChatController;

public class AssistantProfileSelectionSync {
    private final AssistantProfileSelectionModel selectionModel;
    private final LiveChatController liveChatController;
    private ChatMemory chatMemory;
    private Consumer<String> profileMessageConsumer;
    private AssistantProfile pendingProfile;
    private String pendingProfileId;
    private String lastInjectedProfileId;

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
                AssistantProfile selectedProfile = selectionModel.getSelectedProfile();
                AssistantProfile resolvedProfile =
                    selectionModel.findProfileById(profileSwitchMessage.getProfileId());
                if (resolvedProfile == null) {
                    resolvedProfile = selectedProfile == null ? AssistantProfile.defaultProfile() : selectedProfile;
                }
                return new AssistantProfileInstructionMessage(
                    resolvedProfile.getId(),
                    resolvedProfile.getName(),
                    resolvedProfile.getPrompt());
            });
        }
    }

    public void setProfileMessageConsumer(Consumer<String> profileMessageConsumer) {
        this.profileMessageConsumer = profileMessageConsumer;
    }

    void applyAssistantProfileSelection(AssistantProfile profile) {
        if (profile == null) {
            return;
        }
        AssistantProfileSwitchMessage message = new AssistantProfileSwitchMessage(
            profile.getId(),
            profile.getName());
        if (message.getProfileId().isEmpty() && message.getProfileName().isEmpty()) {
            return;
        }
        if (chatMemory != null) {
            chatMemory.add(message);
        }
        liveChatController.recordAssistantProfileMessage(message);
        if (profileMessageConsumer != null) {
            profileMessageConsumer.accept(profile.getName());
        }
        lastInjectedProfileId = profileId(profile);
    }

    void handleUserSelection(AssistantProfile profile) {
        if (profile == null) {
            return;
        }
        selectionModel.setSelectedProfile(profile, true);
        pendingProfile = profile;
        pendingProfileId = profileId(profile);
    }

    AssistantProfile selectForActivation(boolean fromTranscriptRestore) {
        List<ChatTranscriptEntry> entries = liveChatController.snapshotTranscriptEntries();
        AssistantProfileTranscriptEntry profileEntry = findLastAssistantProfileEntry(entries);
        AssistantProfile selected = selectionModel.getSelectedProfile();
        if (profileEntry == null && !fromTranscriptRestore) {
            lastInjectedProfileId = null;
            pendingProfile = selected;
            pendingProfileId = profileId(selected);
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
        if (fromTranscriptRestore) {
            if (profileEntry != null && transcriptProfileExists) {
                lastInjectedProfileId = profileId(selected);
            } else {
                lastInjectedProfileId = null;
            }
            pendingProfile = selected;
            pendingProfileId = profileId(selected);
            return selected;
        }
        if (profileEntry != null && !transcriptProfileId.isEmpty() && !transcriptProfileExists) {
            lastInjectedProfileId = null;
            pendingProfile = selected;
            pendingProfileId = profileId(selected);
            return selected;
        }
        lastInjectedProfileId = profileId(selected);
        pendingProfile = selected;
        pendingProfileId = lastInjectedProfileId;
        return selected;
    }

    public void maybeInjectBeforeUserMessage() {
        if (pendingProfile == null || pendingProfileId == null || pendingProfileId.trim().isEmpty()) {
            return;
        }
        if (pendingProfileId.equals(lastInjectedProfileId)) {
            pendingProfile = null;
            pendingProfileId = null;
            return;
        }
        applyAssistantProfileSelection(pendingProfile);
        pendingProfile = null;
        pendingProfileId = null;
    }

    private AssistantProfileTranscriptEntry findLastAssistantProfileEntry(List<ChatTranscriptEntry> entries) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            ChatTranscriptEntry entry = entries.get(index);
            if (entry instanceof AssistantProfileTranscriptEntry) {
                return (AssistantProfileTranscriptEntry) entry;
            }
        }
        return null;
    }

    private String profileId(AssistantProfile profile) {
        if (profile == null) {
            return "";
        }
        return normalize(profile.getId());
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}

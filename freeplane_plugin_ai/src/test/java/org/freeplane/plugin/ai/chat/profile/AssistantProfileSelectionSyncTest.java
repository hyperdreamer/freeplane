package org.freeplane.plugin.ai.chat.profile;

import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.AssistantProfileTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.chat.session.LiveChatController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AssistantProfileSelectionSyncTest {

    @Test
    public void applyAssistantProfileSelection_emitsOnlyProfilePaneMessage() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);
        List<String> paneMessages = new ArrayList<>();
        uut.setProfileMessageConsumer(paneMessages::add);
        AssistantProfile profile = new AssistantProfile("profile-id", "A sayer", "Start with A");

        uut.applyAssistantProfileSelection(profile);

        verify(chatMemory).add(any(AssistantProfileSwitchMessage.class));
        verify(liveChatController).recordAssistantProfileMessage(
            argThat(message -> "profile-id".equals(message.getProfileId())
                && "A sayer".equals(message.getProfileName())));
        assertThat(paneMessages).containsExactly("A sayer");
    }

    @Test
    public void selectFromTranscript_selectsExistingProfileById() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile transcriptProfile = new AssistantProfile("profile-a", "A", "Prompt A");
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(Arrays.asList(
            new AssistantProfileTranscriptEntry("profile-a", "A", true)));
        when(selectionModel.findProfileById("profile-a")).thenReturn(transcriptProfile);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        AssistantProfile selected = uut.selectForActivation(true);
        uut.maybeInjectBeforeUserMessage();

        assertThat(selected).isEqualTo(transcriptProfile);
        verify(selectionModel).setSelectedProfile(transcriptProfile, false);
        verify(chatMemory, never()).add(any(AssistantProfileSwitchMessage.class));
    }

    @Test
    public void selectFromTranscriptRestore_injectsCurrentSelectionWhenProfileIdMissing() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile current = new AssistantProfile("current", "Current", "Prompt");
        List<ChatTranscriptEntry> entries = Collections.singletonList(
            new AssistantProfileTranscriptEntry("missing", "Missing", true));
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(entries);
        when(selectionModel.findProfileById("missing")).thenReturn(null);
        when(selectionModel.getSelectedProfile()).thenReturn(current);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        AssistantProfile selected = uut.selectForActivation(true);
        uut.maybeInjectBeforeUserMessage();

        assertThat(selected).isEqualTo(current);
        verify(chatMemory).add(any(AssistantProfileSwitchMessage.class));
    }

    @Test
    public void selectForActivation_liveSwitch_skipsInjectionWhenTranscriptProfileExists() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile transcriptProfile = new AssistantProfile("profile-a", "A", "Prompt A");
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(Arrays.asList(
            new AssistantProfileTranscriptEntry("profile-a", "A", true)));
        when(selectionModel.findProfileById("profile-a")).thenReturn(transcriptProfile);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        AssistantProfile selected = uut.selectForActivation(false);
        uut.maybeInjectBeforeUserMessage();

        assertThat(selected).isEqualTo(transcriptProfile);
        verify(chatMemory, never()).add(any(AssistantProfileSwitchMessage.class));
    }

    @Test
    public void selectForActivation_liveSwitch_injectsWhenTranscriptProfileMissing() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile current = new AssistantProfile("current", "Current", "Prompt");
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(Arrays.asList(
            new AssistantProfileTranscriptEntry("missing", "Missing", true)));
        when(selectionModel.findProfileById("missing")).thenReturn(null);
        when(selectionModel.getSelectedProfile()).thenReturn(current);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        AssistantProfile selected = uut.selectForActivation(false);
        uut.maybeInjectBeforeUserMessage();

        assertThat(selected).isEqualTo(current);
        verify(chatMemory).add(any(AssistantProfileSwitchMessage.class));
    }

    @Test
    public void selectForActivation_injectsWhenStoredProfileSnapshotDiffersFromCurrentPrompt() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile current = new AssistantProfile("profile-a", "A", "Prompt B");
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(Arrays.asList(
            new AssistantProfileTranscriptEntry("profile-a", "A", "Now you have the profile A.\nProfile definition: Prompt A", true)));
        when(selectionModel.findProfileById("profile-a")).thenReturn(current);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        uut.selectForActivation(false);
        uut.maybeInjectBeforeUserMessage();

        verify(chatMemory).add((dev.langchain4j.data.message.ChatMessage) argThat(message ->
            message instanceof AssistantProfileSwitchMessage
                && "A".equals(((AssistantProfileSwitchMessage) message).getProfileName())
                && ((AssistantProfileSwitchMessage) message).getProfileMessage().contains("Prompt B")));
    }

    @Test
    public void requestProfileByNameResolvesConfiguredProfileSnapshot() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        when(selectionModel.getProfiles()).thenReturn(Arrays.asList(
            new AssistantProfile("profile-a", "Reviewer", "Check strictly")));
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);

        AssistantProfileSelectionSync.ProfileRequestResolution resolution =
            uut.resolveRequestProfile(" Reviewer ", null);

        assertThat(resolution.getConfigurationErrorDetail()).isNull();
        assertThat(resolution.getMessage().getProfileId()).isEqualTo("profile-a");
        assertThat(resolution.getMessage().getProfileName()).isEqualTo("Reviewer");
        assertThat(resolution.getMessage().getProfileMessage()).contains("Check strictly");
    }

    @Test
    public void requestProfileByNameFailsForAmbiguousName() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        when(selectionModel.getProfiles()).thenReturn(Arrays.asList(
            new AssistantProfile("a", "Reviewer", "A"),
            new AssistantProfile("b", "Reviewer", "B")));
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);

        AssistantProfileSelectionSync.ProfileRequestResolution resolution =
            uut.resolveRequestProfile("Reviewer", null);

        assertThat(resolution.getConfigurationErrorDetail()).contains("ambiguous");
        assertThat(resolution.getMessage()).isNull();
    }

    @Test
    public void explicitRequestProfileDoesNotMutateConfiguredProfiles() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);

        AssistantProfileSelectionSync.ProfileRequestResolution resolution =
            uut.resolveRequestProfile(" Local ", " Be concise ");

        assertThat(resolution.getConfigurationErrorDetail()).isNull();
        assertThat(resolution.getMessage().getProfileName()).isEqualTo("Local");
        assertThat(resolution.getMessage().getProfileMessage()).isEqualTo("Be concise");
        verify(selectionModel, never()).setSelectedProfile(any(AssistantProfile.class), org.mockito.ArgumentMatchers.anyBoolean());
        verify(selectionModel, never()).saveProfiles(any());
    }

    @Test
    public void duplicateRequestProfileIsSkippedWhenNameAndMessageMatchActiveProfile() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        AssistantProfileChatMemory chatMemory = AssistantProfileChatMemory.withMaxTokens(500);
        AssistantProfileSwitchMessage active = new AssistantProfileSwitchMessage(
            "profile-a",
            "Reviewer",
            "Be concise");
        chatMemory.add(active);
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        boolean added = uut.addProfileMessageIfDifferent(new AssistantProfileSwitchMessage(
            "profile-b",
            " Reviewer ",
            " Be concise "));

        assertThat(added).isFalse();
        assertThat(chatMemory.transcriptEntriesForPersistence())
            .filteredOn(entry -> entry instanceof AssistantProfileTranscriptEntry)
            .hasSize(1);
    }

    @Test
    public void duplicateRequestProfileIsAddedWhenModelConfigurationDiffers() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        AssistantProfileChatMemory chatMemory = AssistantProfileChatMemory.withMaxTokens(500);
        AIModelConfiguration activeConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
            AiThinkingEffort.LOW,
            null);
        AIModelConfiguration newConfiguration = AIModelConfiguration.of(
            AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
            AiThinkingEffort.HIGH,
            null);
        chatMemory.add(new AssistantProfileSwitchMessage(
            "profile-a",
            "Reviewer",
            "Be concise",
            activeConfiguration));
        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        boolean added = uut.addProfileMessageIfDifferent(new AssistantProfileSwitchMessage(
            "profile-a",
            "Reviewer",
            "Be concise",
            newConfiguration));

        assertThat(added).isTrue();
        assertThat(chatMemory.latestProfileSwitchMessage().getModelConfiguration()).isEqualTo(newConfiguration);
    }

    @Test
    public void selectForActivation_newChat_injectsSelectedProfile() {
        AssistantProfileSelectionModel selectionModel = mock(AssistantProfileSelectionModel.class);
        LiveChatController liveChatController = mock(LiveChatController.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        AssistantProfile current = new AssistantProfile("current", "Current", "Prompt");
        when(liveChatController.snapshotTranscriptEntries()).thenReturn(Collections.emptyList());
        when(selectionModel.getSelectedProfile()).thenReturn(current);

        AssistantProfileSelectionSync uut = new AssistantProfileSelectionSync(
            selectionModel, liveChatController);
        uut.setChatMemory(chatMemory);

        AssistantProfile selected = uut.selectForActivation(false);
        uut.maybeInjectBeforeUserMessage();

        assertThat(selected).isEqualTo(current);
        verify(chatMemory).add(any(AssistantProfileSwitchMessage.class));
    }
}

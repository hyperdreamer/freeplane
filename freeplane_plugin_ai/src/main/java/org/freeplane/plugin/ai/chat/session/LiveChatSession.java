package org.freeplane.plugin.ai.chat.session;

import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptId;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageState;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

final class LiveChatSession {
    private final LiveChatSessionId id;
    private final ChatMemory chatMemory;
    private final Set<String> mapIds;
    private final List<MapRootShortTextCount> mapRootShortTextCounts;
    private final boolean assistantProfileEnabled;
    private ToolAvailabilityLevel toolAvailabilityOverride;
    private AIModelConfiguration modelConfigurationOverride;
    private List<ChatTranscriptEntry> transcriptEntries;
    private ChatTranscriptId transcriptId;
    private String displayName;
    private boolean nameEdited;
    private boolean userMessageNameApplied;
    private long lastActivityTimestamp;
    private ChatTokenUsageState tokenUsageState;

    LiveChatSession(LiveChatSessionId id, ChatMemory chatMemory, String displayName) {
        this(id, chatMemory, displayName, true, null);
    }

    LiveChatSession(LiveChatSessionId id, ChatMemory chatMemory, String displayName,
                    boolean assistantProfileEnabled,
                    ToolAvailabilityLevel toolAvailabilityOverride) {
        this.id = id;
        this.chatMemory = chatMemory;
        this.displayName = displayName;
        this.assistantProfileEnabled = assistantProfileEnabled;
        this.toolAvailabilityOverride = toolAvailabilityOverride;
        this.mapIds = new LinkedHashSet<String>();
        this.mapRootShortTextCounts = new ArrayList<MapRootShortTextCount>();
        this.transcriptEntries = new ArrayList<ChatTranscriptEntry>();
    }

    LiveChatSessionId getId() {
        return id;
    }

    ChatMemory getChatMemory() {
        return chatMemory;
    }

    List<ChatTranscriptEntry> getTranscriptEntries() {
        return transcriptEntries;
    }

    void setTranscriptEntries(List<ChatTranscriptEntry> transcriptEntries) {
        this.transcriptEntries = transcriptEntries;
    }

    ChatTranscriptId getTranscriptId() {
        return transcriptId;
    }

    void setTranscriptId(ChatTranscriptId transcriptId) {
        this.transcriptId = transcriptId;
    }

    String getDisplayName() {
        return displayName;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    boolean isNameEdited() {
        return nameEdited;
    }

    void setNameEdited(boolean nameEdited) {
        this.nameEdited = nameEdited;
    }

    boolean isUserMessageNameApplied() {
        return userMessageNameApplied;
    }

    void setUserMessageNameApplied(boolean userMessageNameApplied) {
        this.userMessageNameApplied = userMessageNameApplied;
    }

    Set<String> getMapIds() {
        return mapIds;
    }

    List<MapRootShortTextCount> getMapRootShortTextCounts() {
        return mapRootShortTextCounts;
    }

    void setMapRootShortTextCounts(List<MapRootShortTextCount> mapRootShortTextCounts) {
        this.mapRootShortTextCounts.clear();
        if (mapRootShortTextCounts != null) {
            this.mapRootShortTextCounts.addAll(mapRootShortTextCounts);
        }
    }

    long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    void setLastActivityTimestamp(long lastActivityTimestamp) {
        this.lastActivityTimestamp = lastActivityTimestamp;
    }

    ChatTokenUsageState getTokenUsageState() {
        return tokenUsageState;
    }

    void setTokenUsageState(ChatTokenUsageState tokenUsageState) {
        this.tokenUsageState = tokenUsageState;
    }

    boolean isAssistantProfileEnabled() {
        return assistantProfileEnabled;
    }

    ToolAvailabilityLevel getToolAvailabilityOverride() {
        return toolAvailabilityOverride;
    }

    void setToolAvailabilityOverride(ToolAvailabilityLevel toolAvailabilityOverride) {
        this.toolAvailabilityOverride = toolAvailabilityOverride;
    }

    AIModelConfiguration getModelConfigurationOverride() {
        return modelConfigurationOverride;
    }

    void setModelConfigurationOverride(AIModelConfiguration modelConfigurationOverride) {
        this.modelConfigurationOverride = normalizeModelConfiguration(modelConfigurationOverride);
    }

    String getSelectedModelOverride() {
        return selectionValue(modelConfigurationOverride);
    }

    void setSelectedModelOverride(String selectedModelOverride) {
        AIModelSelection modelSelection = AIModelSelection.fromSelectionValue(normalizeOptional(selectedModelOverride));
        AiThinkingEffort thinkingEffort = modelConfigurationOverride == null
            ? null
            : modelConfigurationOverride.getThinkingEffort();
        AiTemperature temperature = modelConfigurationOverride == null ? null : modelConfigurationOverride.getTemperature();
        modelConfigurationOverride = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
    }

    AiThinkingEffort getThinkingEffortOverride() {
        return modelConfigurationOverride == null ? null : modelConfigurationOverride.getThinkingEffort();
    }

    void setThinkingEffortOverride(AiThinkingEffort thinkingEffortOverride) {
        AIModelSelection modelSelection = modelConfigurationOverride == null
            ? null
            : modelConfigurationOverride.getModelSelection();
        AiTemperature temperature = modelConfigurationOverride == null ? null : modelConfigurationOverride.getTemperature();
        modelConfigurationOverride = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffortOverride, temperature));
    }

    AiTemperature getTemperatureOverride() {
        return modelConfigurationOverride == null ? null : modelConfigurationOverride.getTemperature();
    }

    void setTemperatureOverride(AiTemperature temperatureOverride) {
        AIModelSelection modelSelection = modelConfigurationOverride == null
            ? null
            : modelConfigurationOverride.getModelSelection();
        AiThinkingEffort thinkingEffort = modelConfigurationOverride == null
            ? null
            : modelConfigurationOverride.getThinkingEffort();
        modelConfigurationOverride = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperatureOverride));
    }

    private static String selectionValue(AIModelConfiguration modelConfiguration) {
        if (modelConfiguration == null || modelConfiguration.getModelSelection() == null) {
            return null;
        }
        AIModelSelection selection = modelConfiguration.getModelSelection();
        return AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName());
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

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}

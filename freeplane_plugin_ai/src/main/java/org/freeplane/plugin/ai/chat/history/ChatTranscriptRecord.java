package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import org.freeplane.plugin.ai.model.AIModelConfiguration;

public class ChatTranscriptRecord {
    private long timestamp;
    private String displayName;
    private Boolean assistantProfileEnabled;
    private AIModelConfiguration modelConfigurationOverride;
    private String toolAvailabilityOverride;
    private boolean toolAvailabilityOverrideMetadata;
    private List<MapRootShortTextCount> mapRootShortTextCounts = new ArrayList<>();
    private List<ChatTranscriptEntry> entries = new ArrayList<>();

    public ChatTranscriptRecord() {
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getAssistantProfileEnabled() {
        return assistantProfileEnabled;
    }

    public void setAssistantProfileEnabled(Boolean assistantProfileEnabled) {
        this.assistantProfileEnabled = assistantProfileEnabled;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AIModelConfiguration getModelConfigurationOverride() {
        return modelConfigurationOverride;
    }

    public void setModelConfigurationOverride(AIModelConfiguration modelConfigurationOverride) {
        this.modelConfigurationOverride = normalizeModelConfiguration(modelConfigurationOverride);
    }

    @JsonIgnore
    public String getSelectedModelOverride() {
        return selectedModelOverride(modelConfigurationOverride);
    }

    @JsonSetter("selectedModelOverride")
    public void setSelectedModelOverride(String selectedModelOverride) {
        setModelConfigurationOverride(AIModelConfiguration.fromSelectionValue(selectedModelOverride));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getToolAvailabilityOverride() {
        return toolAvailabilityOverride;
    }

    public void setToolAvailabilityOverride(String toolAvailabilityOverride) {
        this.toolAvailabilityOverride = toolAvailabilityOverride;
        this.toolAvailabilityOverrideMetadata = true;
    }

    public boolean isToolAvailabilityOverrideMetadata() {
        return toolAvailabilityOverrideMetadata;
    }

    public void setToolAvailabilityOverrideMetadata(boolean toolAvailabilityOverrideMetadata) {
        this.toolAvailabilityOverrideMetadata = toolAvailabilityOverrideMetadata;
    }

    @JsonIgnore
    public boolean hasToolAvailabilityOverrideMetadata() {
        return toolAvailabilityOverrideMetadata;
    }

    public List<MapRootShortTextCount> getMapRootShortTextCounts() {
        return mapRootShortTextCounts;
    }

    public void setMapRootShortTextCounts(List<MapRootShortTextCount> mapRootShortTextCounts) {
        this.mapRootShortTextCounts = mapRootShortTextCounts == null ? new ArrayList<>() : mapRootShortTextCounts;
    }

    public List<ChatTranscriptEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ChatTranscriptEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : entries;
    }

    private static String selectedModelOverride(AIModelConfiguration modelConfiguration) {
        if (modelConfiguration == null || modelConfiguration.getModelSelection() == null) {
            return null;
        }
        return org.freeplane.plugin.ai.model.AIModelSelection.createSelectionValue(
            modelConfiguration.getModelSelection().getProviderName(),
            modelConfiguration.getModelSelection().getModelName());
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

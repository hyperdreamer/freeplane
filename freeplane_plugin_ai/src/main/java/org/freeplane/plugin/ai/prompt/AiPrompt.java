package org.freeplane.plugin.ai.prompt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;

public class AiPrompt {
    private String name;
    private String prompt;
    private boolean showInChat;
    private String modelSelectionValue;
    private AIModelConfiguration modelConfiguration;
    private String toolAvailabilitySelectionValue;

    public AiPrompt() {
        this("", "", false, "", "");
    }

    public AiPrompt(String name, String prompt, boolean showInChat) {
        this(name, prompt, showInChat, "", "");
    }

    public AiPrompt(String name, String prompt, boolean showInChat, String modelSelectionValue) {
        this(name, prompt, showInChat, modelSelectionValue, "");
    }

    public AiPrompt(String name, String prompt, boolean showInChat,
                    String modelSelectionValue,
                    String toolAvailabilitySelectionValue) {
        this.name = name;
        this.prompt = prompt;
        this.showInChat = showInChat;
        setModelSelectionValue(modelSelectionValue);
        this.toolAvailabilitySelectionValue = toolAvailabilitySelectionValue;
    }

    public AiPrompt copy() {
        AiPrompt copy = new AiPrompt(name, prompt, showInChat, "", toolAvailabilitySelectionValue);
        copy.modelSelectionValue = modelSelectionValue;
        copy.modelConfiguration = modelConfiguration;
        return copy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isShowInChat() {
        return showInChat;
    }

    public void setShowInChat(boolean showInChat) {
        this.showInChat = showInChat;
    }

    @JsonIgnore
    public String getModelSelectionValue() {
        return modelSelectionValue == null ? "" : modelSelectionValue;
    }

    @JsonSetter("modelSelectionValue")
    public void setModelSelectionValue(String modelSelectionValue) {
        String normalizedSelectionValue = normalizeOptional(modelSelectionValue);
        this.modelSelectionValue = normalizedSelectionValue == null ? "" : normalizedSelectionValue;
        setModelSelection(AIModelSelection.fromSelectionValue(normalizedSelectionValue));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AIModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    public void setModelConfiguration(AIModelConfiguration modelConfiguration) {
        this.modelConfiguration = normalizeModelConfiguration(modelConfiguration);
        this.modelSelectionValue = selectionValue(this.modelConfiguration);
    }

    @JsonIgnore
    public AiThinkingEffort getThinkingEffort() {
        return modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
    }

    public void setThinkingEffort(AiThinkingEffort thinkingEffort) {
        AIModelSelection modelSelection = modelConfiguration == null ? null : modelConfiguration.getModelSelection();
        Double temperature = modelConfiguration == null ? null : modelConfiguration.getTemperature();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
        modelSelectionValue = selectionValue(modelConfiguration);
    }

    @JsonIgnore
    public Double getTemperature() {
        return modelConfiguration == null ? null : modelConfiguration.getTemperature();
    }

    public void setTemperature(Double temperature) {
        AIModelSelection modelSelection = modelConfiguration == null ? null : modelConfiguration.getModelSelection();
        AiThinkingEffort thinkingEffort = modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
        modelSelectionValue = selectionValue(modelConfiguration);
    }

    public String getToolAvailabilitySelectionValue() {
        return toolAvailabilitySelectionValue;
    }

    public void setToolAvailabilitySelectionValue(String toolAvailabilitySelectionValue) {
        this.toolAvailabilitySelectionValue = toolAvailabilitySelectionValue;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    private void setModelSelection(AIModelSelection modelSelection) {
        AiThinkingEffort thinkingEffort = modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
        Double temperature = modelConfiguration == null ? null : modelConfiguration.getTemperature();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
    }

    private static String selectionValue(AIModelConfiguration modelConfiguration) {
        if (modelConfiguration == null || modelConfiguration.getModelSelection() == null) {
            return "";
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

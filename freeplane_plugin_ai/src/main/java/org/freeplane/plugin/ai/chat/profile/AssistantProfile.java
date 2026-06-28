package org.freeplane.plugin.ai.chat.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;

class AssistantProfile {
    public static final String DEFAULT_ID = "default";

    private String id;
    private String name;
    private String prompt;
    private AIModelConfiguration modelConfiguration;

    public AssistantProfile() {
    }

    public AssistantProfile(String id, String name, String prompt) {
        this(id, name, prompt, null);
    }

    public AssistantProfile(String id, String name, String prompt, AIModelConfiguration modelConfiguration) {
        this.id = id;
        this.name = name;
        this.prompt = prompt;
        setModelConfiguration(modelConfiguration);
    }

    public static AssistantProfile defaultProfile() {
        return new AssistantProfile(DEFAULT_ID, "Default", "");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AIModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    public void setModelConfiguration(AIModelConfiguration modelConfiguration) {
        this.modelConfiguration = normalizeModelConfiguration(modelConfiguration);
    }

    @JsonIgnore
    public String getModelSelectionValue() {
        if (modelConfiguration == null || modelConfiguration.getModelSelection() == null) {
            return "";
        }
        AIModelSelection selection = modelConfiguration.getModelSelection();
        return AIModelSelection.createSelectionValue(selection.getProviderName(), selection.getModelName());
    }

    public void setModelSelectionValue(String modelSelectionValue) {
        AIModelSelection modelSelection = AIModelSelection.fromSelectionValue(normalizeOptional(modelSelectionValue));
        AiThinkingEffort thinkingEffort = modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
        AiTemperature temperature = modelConfiguration == null ? null : modelConfiguration.getTemperature();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
    }

    @JsonIgnore
    public AiThinkingEffort getThinkingEffort() {
        return modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
    }

    public void setThinkingEffort(AiThinkingEffort thinkingEffort) {
        AIModelSelection modelSelection = modelConfiguration == null ? null : modelConfiguration.getModelSelection();
        AiTemperature temperature = modelConfiguration == null ? null : modelConfiguration.getTemperature();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
    }

    @JsonIgnore
    public AiTemperature getTemperature() {
        return modelConfiguration == null ? null : modelConfiguration.getTemperature();
    }

    public void setTemperature(AiTemperature temperature) {
        AIModelSelection modelSelection = modelConfiguration == null ? null : modelConfiguration.getModelSelection();
        AiThinkingEffort thinkingEffort = modelConfiguration == null ? null : modelConfiguration.getThinkingEffort();
        modelConfiguration = normalizeModelConfiguration(
            AIModelConfiguration.of(modelSelection, thinkingEffort, temperature));
    }

    @JsonIgnore
    public boolean isDefault() {
        return Objects.equals(DEFAULT_ID, id);
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
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

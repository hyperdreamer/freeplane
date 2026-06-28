package org.freeplane.plugin.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;

public class AIModelConfiguration {
    private final AIModelSelection modelSelection;
    private final AiThinkingEffort thinkingEffort;
    private final AiTemperature temperature;

    @JsonCreator
    private AIModelConfiguration(@JsonProperty("modelSelection") AIModelSelection modelSelection,
                                 @JsonProperty("thinkingEffort") AiThinkingEffort thinkingEffort,
                                 @JsonProperty("temperature") Object temperature) {
        this.modelSelection = normalizeModelSelection(modelSelection);
        this.thinkingEffort = thinkingEffort;
        this.temperature = AIModelTemperatureStorage.fromStoredValue(temperature);
    }

    private AIModelConfiguration(AIModelSelection modelSelection,
                                 AiThinkingEffort thinkingEffort,
                                 AiTemperature temperature) {
        this.modelSelection = normalizeModelSelection(modelSelection);
        this.thinkingEffort = thinkingEffort;
        this.temperature = temperature;
    }

    public static AIModelConfiguration of(AIModelSelection modelSelection,
                                          AiThinkingEffort thinkingEffort,
                                          AiTemperature temperature) {
        return new AIModelConfiguration(modelSelection, thinkingEffort, temperature);
    }

    public static AIModelConfiguration withModelSelection(AIModelSelection modelSelection) {
        return modelSelection == null ? null : new AIModelConfiguration(modelSelection, null, null);
    }

    public static AIModelConfiguration fromSelectionValue(String selectionValue) {
        return withModelSelection(AIModelSelection.fromSelectionValue(selectionValue));
    }

    public AIModelConfiguration withFallback(AIModelConfiguration fallback) {
        if (fallback == null) {
            return this;
        }
        return new AIModelConfiguration(
            modelSelection != null ? modelSelection : fallback.modelSelection,
            thinkingEffort != null ? thinkingEffort : fallback.thinkingEffort,
            temperature != null ? temperature : fallback.temperature);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AIModelSelection getModelSelection() {
        return modelSelection;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AiThinkingEffort getThinkingEffort() {
        return thinkingEffort;
    }

    @JsonIgnore
    public AiTemperature getTemperature() {
        return temperature;
    }

    @JsonProperty("temperature")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getStoredTemperature() {
        return AIModelTemperatureStorage.toStoredValue(temperature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelSelection, thinkingEffort, temperature);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AIModelConfiguration)) {
            return false;
        }
        AIModelConfiguration other = (AIModelConfiguration) obj;
        return Objects.equals(modelSelection, other.modelSelection)
            && thinkingEffort == other.thinkingEffort
            && Objects.equals(temperature, other.temperature);
    }

    private static AIModelSelection normalizeModelSelection(AIModelSelection modelSelection) {
        if (modelSelection == null
            || modelSelection.getProviderName() == null
            || modelSelection.getProviderName().trim().isEmpty()
            || modelSelection.getModelName() == null
            || modelSelection.getModelName().trim().isEmpty()) {
            return null;
        }
        return modelSelection;
    }
}

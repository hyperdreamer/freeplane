package org.freeplane.plugin.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.freeplane.api.ai.AiThinkingEffort;

public class AIModelConfiguration {
    private final AIModelSelection modelSelection;
    private final AiThinkingEffort thinkingEffort;
    private final Double temperature;

    @JsonCreator
    private AIModelConfiguration(@JsonProperty("modelSelection") AIModelSelection modelSelection,
                                 @JsonProperty("thinkingEffort") AiThinkingEffort thinkingEffort,
                                 @JsonProperty("temperature") Object temperature) {
        this.modelSelection = normalizeModelSelection(modelSelection);
        this.thinkingEffort = thinkingEffort;
        this.temperature = normalizeTemperature(temperature);
    }

    public static AIModelConfiguration of(AIModelSelection modelSelection,
                                          AiThinkingEffort thinkingEffort,
                                          Double temperature) {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double getTemperature() {
        return temperature;
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

    private static Double normalizeTemperature(Object temperature) {
        if (temperature == null) {
            return null;
        }
        final Double parsedTemperature;
        if (temperature instanceof Number) {
            parsedTemperature = Double.valueOf(((Number) temperature).doubleValue());
        }
        else {
            try {
                parsedTemperature = Double.valueOf(temperature.toString().trim());
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (parsedTemperature.isNaN() || parsedTemperature.isInfinite()) {
            return null;
        }
        return parsedTemperature;
    }
}

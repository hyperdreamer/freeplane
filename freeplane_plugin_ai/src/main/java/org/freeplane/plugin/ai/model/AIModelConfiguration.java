package org.freeplane.plugin.ai.model;

import java.util.Objects;
import org.freeplane.api.ai.AiThinkingEffort;

public class AIModelConfiguration {
    private final AIModelSelection modelSelection;
    private final AiThinkingEffort thinkingEffort;
    private final Double temperature;

    private AIModelConfiguration(AIModelSelection modelSelection,
                                 AiThinkingEffort thinkingEffort,
                                 Double temperature) {
        this.modelSelection = modelSelection;
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

    public AIModelSelection getModelSelection() {
        return modelSelection;
    }

    public AiThinkingEffort getThinkingEffort() {
        return thinkingEffort;
    }

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

    private static Double normalizeTemperature(Double temperature) {
        if (temperature == null) {
            return null;
        }
        if (temperature.isNaN() || temperature.isInfinite()) {
            return null;
        }
        return temperature;
    }
}

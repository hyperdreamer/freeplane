package org.freeplane.api.ai;

import java.util.Objects;

/** Immutable public model configuration for script-facing AI requests.
 * Fields are inherited independently when left unset.
 * @since 1.13.3 */
public class AiModelConfiguration {
    private final AiModelSelection modelSelection;
    private final AiThinkingEffort thinkingEffort;
    private final AiTemperature temperature;

    private AiModelConfiguration(Builder builder) {
        this.modelSelection = builder.modelSelection;
        this.thinkingEffort = builder.thinkingEffort;
        this.temperature = builder.temperature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AiModelSelection getModelSelection() {
        return modelSelection;
    }

    public AiThinkingEffort getThinkingEffort() {
        return thinkingEffort;
    }

    public AiTemperature getTemperature() {
        return temperature;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelSelection, thinkingEffort, temperature);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AiModelConfiguration)) {
            return false;
        }
        AiModelConfiguration other = (AiModelConfiguration) obj;
        return Objects.equals(modelSelection, other.modelSelection)
            && thinkingEffort == other.thinkingEffort
            && Objects.equals(temperature, other.temperature);
    }

    public static class Builder {
        private AiModelSelection modelSelection;
        private AiThinkingEffort thinkingEffort;
        private AiTemperature temperature;

        public Builder modelSelection(AiModelSelection modelSelection) {
            this.modelSelection = modelSelection;
            return this;
        }

        public Builder thinkingEffort(AiThinkingEffort thinkingEffort) {
            this.thinkingEffort = thinkingEffort;
            return this;
        }

        public Builder temperature(AiTemperature temperature) {
            this.temperature = temperature;
            return this;
        }

        public AiModelConfiguration build() {
            return new AiModelConfiguration(this);
        }
    }
}

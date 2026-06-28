package org.freeplane.api.ai;

import java.util.Objects;

/**
 * Immutable public model configuration for script-facing AI requests.
 *
 * <p>The three fields are inherited independently when left unset. For a saved
 * prompt request, unset fields inherit from the saved prompt. For a direct
 * request, unset fields inherit from the current provider/model defaults.</p>
 *
 * @since 1.13.3
 */
public class AiModelConfiguration {
    private final AiModelSelection modelSelection;
    private final AiThinkingEffort thinkingEffort;
    private final AiTemperature temperature;

    private AiModelConfiguration(Builder builder) {
        this.modelSelection = builder.modelSelection;
        this.thinkingEffort = builder.thinkingEffort;
        this.temperature = builder.temperature;
    }

    /**
     * Creates a model-configuration builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the requested model selection, or {@code null} to inherit it.
     *
     * @return model selection, or {@code null}
     */
    public AiModelSelection getModelSelection() {
        return modelSelection;
    }

    /**
     * Returns the requested thinking effort, or {@code null} to inherit it.
     *
     * @return thinking effort, or {@code null}
     */
    public AiThinkingEffort getThinkingEffort() {
        return thinkingEffort;
    }

    /**
     * Returns the requested temperature, or {@code null} to inherit it.
     *
     * @return temperature, or {@code null}
     */
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

    /**
     * Mutable builder for {@link AiModelConfiguration}.
     */
    public static class Builder {
        private AiModelSelection modelSelection;
        private AiThinkingEffort thinkingEffort;
        private AiTemperature temperature;

        /**
         * Sets the model selection.
         *
         * <p>Use {@link AiModelSelection#explicit(String, String)} for a specific
         * provider/model pair. Use {@link AiModelSelection#defaultModel()} to force
         * the execution-context default model, including when a saved prompt has its
         * own explicit model.</p>
         *
         * @param modelSelection requested selection, or {@code null} to inherit
         * @return this builder
         */
        public Builder modelSelection(AiModelSelection modelSelection) {
            this.modelSelection = modelSelection;
            return this;
        }

        /**
         * Sets provider-independent thinking effort.
         *
         * @param thinkingEffort requested effort, or {@code null} to inherit
         * @return this builder
         */
        public Builder thinkingEffort(AiThinkingEffort thinkingEffort) {
            this.thinkingEffort = thinkingEffort;
            return this;
        }

        /**
         * Sets model temperature.
         *
         * <p>Use {@link AiTemperature#modelDefault()} to request the model's own
         * default temperature explicitly.</p>
         *
         * @param temperature requested temperature, or {@code null} to inherit
         * @return this builder
         */
        public Builder temperature(AiTemperature temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Builds immutable model configuration.
         *
         * @return immutable model configuration
         */
        public AiModelConfiguration build() {
            return new AiModelConfiguration(this);
        }
    }
}

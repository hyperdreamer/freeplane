package org.freeplane.api.ai;

import java.util.Objects;

/**
 * Immutable public temperature configuration for script-facing AI requests.
 *
 * <p>{@code null} temperature in {@link AiModelConfiguration} means inherit the
 * surrounding configuration. {@link #modelDefault()} is an explicit value meaning
 * "ask the selected model to use its own default temperature". Numeric values are
 * passed through as requested model temperature values.</p>
 *
 * @since 1.13.3
 */
public class AiTemperature {
    private static final AiTemperature MODEL_DEFAULT = new AiTemperature(true, null);

    private final boolean modelDefault;
    private final Double value;

    private AiTemperature(boolean modelDefault, Double value) {
        this.modelDefault = modelDefault;
        this.value = value;
    }

    /**
     * Requests the selected model's default temperature explicitly.
     *
     * @return shared model-default temperature marker
     */
    public static AiTemperature modelDefault() {
        return MODEL_DEFAULT;
    }

    /**
     * Requests a numeric temperature.
     *
     * @param value finite temperature value
     * @return numeric temperature configuration
     */
    public static AiTemperature of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("temperature must be finite");
        }
        return new AiTemperature(false, Double.valueOf(value));
    }

    /**
     * Returns whether this value requests the model default.
     *
     * @return true for {@link #modelDefault()}
     */
    public boolean isModelDefault() {
        return modelDefault;
    }

    /**
     * Returns whether this value is numeric.
     *
     * @return true when {@link #getValue()} is non-null
     */
    public boolean isNumeric() {
        return !modelDefault;
    }

    /**
     * Returns the numeric temperature value, or {@code null} for {@link #modelDefault()}.
     *
     * @return numeric temperature, or {@code null}
     */
    public Double getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Boolean.valueOf(modelDefault), value);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AiTemperature)) {
            return false;
        }
        AiTemperature other = (AiTemperature) obj;
        return modelDefault == other.modelDefault
            && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        return modelDefault ? "model_default" : String.valueOf(value);
    }
}

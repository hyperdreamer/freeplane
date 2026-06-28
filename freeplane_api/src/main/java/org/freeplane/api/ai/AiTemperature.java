package org.freeplane.api.ai;

import java.util.Objects;

/** Immutable public temperature configuration for script-facing AI requests.
 * Null temperature fields inherit independently. Use {@link #modelDefault()}
 * to request the model default explicitly.
 * @since 1.13.3 */
public class AiTemperature {
    private static final AiTemperature MODEL_DEFAULT = new AiTemperature(true, null);

    private final boolean modelDefault;
    private final Double value;

    private AiTemperature(boolean modelDefault, Double value) {
        this.modelDefault = modelDefault;
        this.value = value;
    }

    public static AiTemperature modelDefault() {
        return MODEL_DEFAULT;
    }

    public static AiTemperature of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("temperature must be finite");
        }
        return new AiTemperature(false, Double.valueOf(value));
    }

    public boolean isModelDefault() {
        return modelDefault;
    }

    public boolean isNumeric() {
        return !modelDefault;
    }

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

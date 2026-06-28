package org.freeplane.api.ai;

import java.util.Objects;

/**
 * Explicit provider/model choice or default-model marker inside an
 * {@link AiModelConfiguration}.
 *
 * @since 1.13.3
 */
public class AiModelSelection {
    private final String providerName;
    private final String modelName;
    private final boolean defaultModel;

    private AiModelSelection(String providerName, String modelName, boolean defaultModel) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.defaultModel = defaultModel;
    }

    /**
     * Selects the default model resolved by the request execution context.
     *
     * <p>This is an explicit override, not the same as leaving model selection
     * unset. In a saved prompt request it bypasses the saved prompt's explicit model
     * selection while still allowing other model-configuration fields to inherit
     * independently.</p>
     *
     * @return default-model marker
     */
    public static AiModelSelection defaultModel() {
        return new AiModelSelection(null, null, true);
    }

    /**
     * Selects a specific provider/model pair.
     *
     * <p>Both names are trimmed and must be non-blank. Availability is checked when
     * the request starts.</p>
     *
     * @param providerName provider identifier
     * @param modelName model identifier within the provider
     * @return explicit model selection
     */
    public static AiModelSelection explicit(String providerName, String modelName) {
        String normalizedProviderName = normalizeRequired(providerName, "providerName");
        String normalizedModelName = normalizeRequired(modelName, "modelName");
        return new AiModelSelection(normalizedProviderName, normalizedModelName, false);
    }

    /**
     * Returns whether this selection explicitly requests the default model.
     *
     * @return true for {@link #defaultModel()}
     */
    public boolean isDefaultModel() {
        return defaultModel;
    }

    /**
     * Returns the explicit provider name, or {@code null} for {@link #defaultModel()}.
     *
     * @return provider name, or {@code null}
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the explicit model name, or {@code null} for {@link #defaultModel()}.
     *
     * @return model name, or {@code null}
     */
    public String getModelName() {
        return modelName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, modelName, defaultModel);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AiModelSelection)) {
            return false;
        }
        AiModelSelection other = (AiModelSelection) obj;
        return defaultModel == other.defaultModel
            && Objects.equals(providerName, other.providerName)
            && Objects.equals(modelName, other.modelName);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}

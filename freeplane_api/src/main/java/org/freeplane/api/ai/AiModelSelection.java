package org.freeplane.api.ai;

import java.util.Objects;

/** Explicit or current model selection for an {@link AiRequest}.
 * @since 1.13.3 */
public class AiModelSelection {
    private final String providerName;
    private final String modelName;
    private final boolean current;

    private AiModelSelection(String providerName, String modelName, boolean current) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.current = current;
    }

    public static AiModelSelection current() {
        return new AiModelSelection(null, null, true);
    }

    public static AiModelSelection explicit(String providerName, String modelName) {
        String normalizedProviderName = normalizeRequired(providerName, "providerName");
        String normalizedModelName = normalizeRequired(modelName, "modelName");
        return new AiModelSelection(normalizedProviderName, normalizedModelName, false);
    }

    public boolean isCurrent() {
        return current;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, modelName, current);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AiModelSelection)) {
            return false;
        }
        AiModelSelection other = (AiModelSelection) obj;
        return current == other.current
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

package org.freeplane.plugin.ai.model;

import java.util.Objects;

public class AIModelSelection {
    static final String SELECTION_SEPARATOR = "|";
    private final String providerName;
    private final String modelName;

    private AIModelSelection(String providerName, String modelName) {
        this.providerName = providerName;
        this.modelName = modelName;
    }

    public static AIModelSelection fromSelectionValue(String selectionValue) {
        if (selectionValue == null || selectionValue.isEmpty()) {
            return null;
        }
        int separatorIndex = selectionValue.indexOf(SELECTION_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex >= selectionValue.length() - 1) {
            return null;
        }
        String providerName = selectionValue.substring(0, separatorIndex);
        String modelName = selectionValue.substring(separatorIndex + 1);
        if (providerName.isEmpty() || modelName.isEmpty()) {
            return null;
        }
        return new AIModelSelection(providerName, modelName);
    }

    public static String createSelectionValue(String providerName, String modelName) {
        return providerName + SELECTION_SEPARATOR + modelName;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, modelName);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AIModelSelection)) {
            return false;
        }
        AIModelSelection other = (AIModelSelection) obj;
        return Objects.equals(providerName, other.providerName)
            && Objects.equals(modelName, other.modelName);
    }
}

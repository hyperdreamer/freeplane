package org.freeplane.plugin.ai.model;

import java.util.Objects;

public final class DiscoveredAIModel {
    private final String providerName;
    private final String modelName;
    private final boolean freeModel;
    private final AIModelCapabilities capabilities;

    public DiscoveredAIModel(String providerName,
                             String modelName,
                             boolean freeModel,
                             AIModelCapabilities capabilities) {
        this.providerName = Objects.requireNonNull(providerName, "providerName");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.freeModel = freeModel;
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public boolean isFreeModel() {
        return freeModel;
    }

    public AIModelCapabilities getCapabilities() {
        return capabilities;
    }

    public DiscoveredAIModel withCapabilities(AIModelCapabilities replacementCapabilities) {
        return new DiscoveredAIModel(providerName, modelName, freeModel, replacementCapabilities);
    }
}

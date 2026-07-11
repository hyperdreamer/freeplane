package org.freeplane.plugin.ai.model;

import java.util.Objects;
import org.freeplane.core.util.TextUtils;

public class AIModelDescriptor {
    private final String providerName;
    private final String modelName;
    private final String displayName;
    private final boolean freeModel;
    private final boolean unavailable;
    private final boolean useCurrentModel;

    public AIModelDescriptor(String providerName, String modelName, String displayName, boolean freeModel) {
        this(providerName, modelName, displayName, freeModel, false, false);
    }

    private AIModelDescriptor(String providerName, String modelName, String displayName,
                              boolean freeModel, boolean unavailable, boolean useCurrentModel) {
        this.providerName = Objects.requireNonNull(providerName, "providerName");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.freeModel = freeModel;
        this.unavailable = unavailable;
        this.useCurrentModel = useCurrentModel;
    }

    public static AIModelDescriptor unavailable(String providerName, String modelName) {
        String availableDisplayName = providerDisplayName(providerName) + ": " + modelName;
        return new AIModelDescriptor(
            providerName,
            modelName,
            TextUtils.format("ai_unavailable_format", availableDisplayName),
            false,
            true,
            false);
    }

    public static AIModelDescriptor useCurrentModelOption(String displayName) {
        return new AIModelDescriptor("", "", displayName, false, false, true);
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isFreeModel() {
        return freeModel;
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    public boolean usesCurrentModel() {
        return useCurrentModel;
    }

    public String getSelectionValue() {
        if (useCurrentModel) {
            return "";
        }
        return AIModelSelection.createSelectionValue(providerName, modelName);
    }

    @Override
    public String toString() {
        return displayName;
    }

    static String providerDisplayName(String providerName) {
        OpenAICompatibleProvider openAICompatibleProvider =
            OpenAICompatibleProvider.fromProviderName(providerName);
        if (openAICompatibleProvider != null) {
            return openAICompatibleProvider.getDisplayName();
        }
        if (AIChatModelFactory.PROVIDER_NAME_GEMINI.equals(providerName)) {
            return "Gemini";
        }
        if (AIChatModelFactory.PROVIDER_NAME_OLLAMA.equals(providerName)) {
            return "Ollama";
        }
        return providerName;
    }
}

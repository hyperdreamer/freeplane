package org.freeplane.plugin.ai.model;

import java.util.Locale;

public enum OpenAICompatibleProvider {
    OPENAI("openai", "OpenAI"),
    OPENROUTER("openrouter", "OpenRouter"),
    REQUESTY("requesty", "Requesty"),
    CUSTOM("custom", "Custom");

    private final String providerName;
    private final String displayName;

    OpenAICompatibleProvider(String providerName, String displayName) {
        this.providerName = providerName;
        this.displayName = displayName;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OpenAICompatibleProvider fromProviderName(String providerName) {
        if (providerName == null) {
            return null;
        }
        String normalizedName = providerName.toLowerCase(Locale.ENGLISH);
        for (OpenAICompatibleProvider provider : values()) {
            if (provider.providerName.equals(normalizedName)) {
                return provider;
            }
        }
        return null;
    }
}

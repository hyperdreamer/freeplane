package org.freeplane.plugin.ai.model;

import java.util.Objects;

public final class OpenAICompatibleProviderConfiguration {
    private final OpenAICompatibleProvider provider;
    private final String serviceAddress;
    private final String modelsAddress;
    private final String apiKey;
    private final AIModelListConfiguration modelListConfiguration;

    public OpenAICompatibleProviderConfiguration(OpenAICompatibleProvider provider,
                                                  String serviceAddress,
                                                  String modelsAddress,
                                                  String apiKey,
                                                  AIModelListConfiguration modelListConfiguration) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.serviceAddress = trimToEmpty(serviceAddress);
        this.modelsAddress = trimToEmpty(modelsAddress);
        this.apiKey = trimToEmpty(apiKey);
        this.modelListConfiguration = Objects.requireNonNull(modelListConfiguration, "modelListConfiguration");
    }

    public OpenAICompatibleProvider getProvider() {
        return provider;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public String getModelsAddress() {
        return modelsAddress;
    }

    public String getApiKey() {
        return apiKey;
    }

    public AIModelListConfiguration getModelListConfiguration() {
        return modelListConfiguration;
    }

    public boolean isConfigured() {
        if (provider == OpenAICompatibleProvider.CUSTOM) {
            return !serviceAddress.isEmpty();
        }
        return !apiKey.isEmpty();
    }

    public String getProviderName() {
        return provider.getProviderName();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

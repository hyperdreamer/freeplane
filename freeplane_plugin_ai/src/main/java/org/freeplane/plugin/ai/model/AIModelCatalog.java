package org.freeplane.plugin.ai.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AIModelCatalog {
    private final AIProviderConfiguration configuration;
    private final AIModelCatalogState state;
    private final OpenAICompatibleModelDiscovery openAICompatibleDiscovery;
    private final OpenRouterModelMetadataCatalog metadataCatalog;
    private final OpenRouterModelMetadataInterpreter metadataInterpreter;
    private final GeminiModelDiscovery geminiDiscovery;
    private final OllamaModelDiscovery ollamaDiscovery;

    public AIModelCatalog(AIProviderConfiguration configuration) {
        this(configuration, AIModelCatalogState.shared());
    }

    AIModelCatalog(AIProviderConfiguration configuration, AIModelCatalogState state) {
        this(
            configuration,
            state,
            new OpenAICompatibleModelDiscovery(),
            new OpenRouterModelMetadataCatalog(state, OpenRouterModelMetadataCatalog.MODELS_ADDRESS));
    }

    AIModelCatalog(AIProviderConfiguration configuration,
                   AIModelCatalogState state,
                   OpenAICompatibleModelDiscovery openAICompatibleDiscovery,
                   OpenRouterModelMetadataCatalog metadataCatalog) {
        this(
            configuration,
            state,
            openAICompatibleDiscovery,
            metadataCatalog,
            new GeminiModelDiscovery(metadataCatalog),
            new OllamaModelDiscovery(metadataCatalog));
    }

    AIModelCatalog(AIProviderConfiguration configuration,
                   AIModelCatalogState state,
                   OpenAICompatibleModelDiscovery openAICompatibleDiscovery,
                   OpenRouterModelMetadataCatalog metadataCatalog,
                   GeminiModelDiscovery geminiDiscovery,
                   OllamaModelDiscovery ollamaDiscovery) {
        this.configuration = configuration;
        this.state = state;
        this.openAICompatibleDiscovery = openAICompatibleDiscovery;
        this.metadataCatalog = metadataCatalog;
        this.metadataInterpreter = new OpenRouterModelMetadataInterpreter();
        this.geminiDiscovery = geminiDiscovery;
        this.ollamaDiscovery = ollamaDiscovery;
    }

    public List<AIModelDescriptor> getAvailableModels(boolean allowsRefresh) {
        List<AIModelDescriptor> descriptors = new ArrayList<>();
        for (OpenAICompatibleProviderConfiguration providerConfiguration
            : configuration.getOpenAICompatibleConfigurations()) {
            if (providerConfiguration.isConfigured()) {
                addOpenAICompatibleModels(descriptors, providerConfiguration, allowsRefresh);
            }
        }
        if (configuration.isGeminiConfigured()) {
            addGeminiModels(descriptors, allowsRefresh);
        }
        if (configuration.isOllamaConfigured()) {
            addOllamaModels(descriptors, allowsRefresh);
        }
        descriptors.sort(Comparator.comparing(AIModelDescriptor::getDisplayName));
        return descriptors;
    }

    private void addOpenAICompatibleModels(List<AIModelDescriptor> descriptors,
                                           OpenAICompatibleProviderConfiguration providerConfiguration,
                                           boolean allowsRefresh) {
        AIModelListConfiguration modelList = providerConfiguration.getModelListConfiguration();
        if (modelList.isExplicit()) {
            addExplicitModels(descriptors, providerConfiguration.getProviderName(), modelList);
            return;
        }
        AIModelCatalogCacheKey cacheKey = new AIModelCatalogCacheKey(
            providerConfiguration.getProviderName(),
            providerConfiguration.getModelsAddress(),
            OpenRouterModelMetadataCatalog.MODELS_ADDRESS,
            providerConfiguration.getApiKey());
        AIModelDiscoveryResult result = state.getFresh(cacheKey);
        if (result == null && allowsRefresh) {
            result = discoverOpenAICompatibleModels(providerConfiguration);
            record(cacheKey, result);
        }
        addAutomaticModels(descriptors, result, modelList);
    }

    private AIModelDiscoveryResult discoverOpenAICompatibleModels(
        OpenAICompatibleProviderConfiguration providerConfiguration) {
        AIModelDiscoveryResult directResult = openAICompatibleDiscovery.discover(providerConfiguration);
        if (!directResult.isSuccessful()) {
            return directResult;
        }
        List<DiscoveredAIModel> suitableModels = new ArrayList<>();
        for (DiscoveredAIModel model : directResult.getModels()) {
            AIModelCapabilities capabilities = model.getCapabilities();
            if (!capabilities.isToolCapableTextModel()
                && capabilities.hasUnknownCapability()
                && !capabilities.hasUnsupportedCapability()) {
                String metadataId = metadataId(providerConfiguration.getProvider(), model.getModelName());
                capabilities = metadataInterpreter.interpret(metadataCatalog.find(metadataId));
            }
            if (capabilities.isToolCapableTextModel()) {
                suitableModels.add(model.withCapabilities(capabilities));
            }
        }
        return AIModelDiscoveryResult.success(suitableModels);
    }

    private void addGeminiModels(List<AIModelDescriptor> descriptors, boolean allowsRefresh) {
        AIModelListConfiguration modelList = configuration.getGeminiModelListConfiguration();
        if (modelList.isExplicit()) {
            addExplicitModels(descriptors, AIChatModelFactory.PROVIDER_NAME_GEMINI, modelList);
            return;
        }
        String modelsAddress = appendPath(configuration.getGeminiServiceAddress(), "models");
        AIModelCatalogCacheKey cacheKey = new AIModelCatalogCacheKey(
            AIChatModelFactory.PROVIDER_NAME_GEMINI,
            modelsAddress,
            OpenRouterModelMetadataCatalog.MODELS_ADDRESS,
            configuration.getGeminiKey());
        AIModelDiscoveryResult result = state.getFresh(cacheKey);
        if (result == null && allowsRefresh) {
            result = geminiDiscovery.discover(configuration);
            record(cacheKey, result);
        }
        addAutomaticModels(descriptors, result, modelList);
    }

    private void addOllamaModels(List<AIModelDescriptor> descriptors, boolean allowsRefresh) {
        AIModelListConfiguration modelList = configuration.getOllamaModelListConfiguration();
        if (modelList.isExplicit()) {
            addExplicitModels(descriptors, AIChatModelFactory.PROVIDER_NAME_OLLAMA, modelList);
            return;
        }
        String tagsAddress = appendPath(configuration.getOllamaServiceAddress(), "api/tags");
        String showAddress = appendPath(configuration.getOllamaServiceAddress(), "api/show");
        AIModelCatalogCacheKey cacheKey = new AIModelCatalogCacheKey(
            AIChatModelFactory.PROVIDER_NAME_OLLAMA,
            tagsAddress,
            showAddress + "|" + OpenRouterModelMetadataCatalog.MODELS_ADDRESS,
            configuration.getOllamaApiKey());
        AIModelDiscoveryResult result = state.getFresh(cacheKey);
        if (result == null && allowsRefresh) {
            result = ollamaDiscovery.discover(configuration);
            record(cacheKey, result);
        }
        addAutomaticModels(descriptors, result, modelList);
    }

    private void addExplicitModels(List<AIModelDescriptor> descriptors,
                                   String providerName,
                                   AIModelListConfiguration modelList) {
        for (String modelName : modelList.getLiteralModelNames()) {
            descriptors.add(createDescriptor(providerName, modelName, false));
        }
    }

    private void addAutomaticModels(List<AIModelDescriptor> descriptors,
                                    AIModelDiscoveryResult result,
                                    AIModelListConfiguration modelList) {
        if (result == null || !result.isSuccessful()) {
            return;
        }
        for (DiscoveredAIModel model : result.getModels()) {
            if (model.getCapabilities().isToolCapableTextModel()
                && modelList.accepts(model.getModelName())) {
                descriptors.add(createDescriptor(
                    model.getProviderName(),
                    model.getModelName(),
                    model.isFreeModel()));
            }
        }
    }

    private void record(AIModelCatalogCacheKey cacheKey, AIModelDiscoveryResult result) {
        if (result.isSuccessful()) {
            state.recordSuccess(cacheKey, result.getModels());
        }
        else {
            state.recordFailure(cacheKey);
        }
    }

    private String metadataId(OpenAICompatibleProvider provider, String modelName) {
        if (provider == OpenAICompatibleProvider.OPENAI) {
            return "openai/" + modelName;
        }
        return modelName;
    }

    private AIModelDescriptor createDescriptor(String providerName,
                                               String modelName,
                                               boolean freeModel) {
        String displayName = AIModelDescriptor.providerDisplayName(providerName) + ": " + modelName;
        if (freeModel) {
            displayName += " (free)";
        }
        return new AIModelDescriptor(providerName, modelName, displayName, freeModel);
    }

    private String appendPath(String baseAddress, String path) {
        if (baseAddress == null || baseAddress.isEmpty()) {
            return "";
        }
        return baseAddress.endsWith("/") ? baseAddress + path : baseAddress + "/" + path;
    }
}

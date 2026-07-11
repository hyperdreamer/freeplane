package org.freeplane.plugin.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenRouterModelMetadataCatalog {
    public static final String MODELS_ADDRESS = "https://openrouter.ai/api/v1/models";

    private final ObjectMapper objectMapper;
    private final AIModelCatalogState state;
    private final String modelsAddress;
    private final AIModelCatalogCacheKey cacheKey;

    public OpenRouterModelMetadataCatalog() {
        this(AIModelCatalogState.shared(), MODELS_ADDRESS);
    }

    OpenRouterModelMetadataCatalog(AIModelCatalogState state, String modelsAddress) {
        this.objectMapper = new ObjectMapper();
        this.state = state;
        this.modelsAddress = modelsAddress;
        this.cacheKey = new AIModelCatalogCacheKey(
            "openrouter-metadata", modelsAddress, modelsAddress, "");
    }

    public OpenAIModelItem find(String providerQualifiedModelId) {
        if (providerQualifiedModelId == null) {
            return null;
        }
        Map<String, OpenAIModelItem> models = getFreshModels();
        if (models == null) {
            MetadataFetchResult fetchResult = fetchModels();
            if (!fetchResult.successful) {
                state.recordFailure(cacheKey);
                state.recordValue(cacheKey, Collections.<String, OpenAIModelItem>emptyMap());
                return null;
            }
            models = fetchResult.models;
            state.recordValue(cacheKey, models);
        }
        return models.get(providerQualifiedModelId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, OpenAIModelItem> getFreshModels() {
        return state.getFreshValue(cacheKey, Map.class);
    }

    MetadataFetchResult fetchModels() {
        try {
            HttpURLConnection connection = openConnection(new URL(modelsAddress));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("HTTP-Referer", "https://github.com/freeplane/freeplane");
            connection.setRequestProperty("X-Title", "Freeplane");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return MetadataFetchResult.failed();
            }
            try (InputStream inputStream = connection.getInputStream();
                 Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return MetadataFetchResult.success(parseResponse(reader));
            }
        }
        catch (IOException | RuntimeException exception) {
            return MetadataFetchResult.failed();
        }
    }

    Map<String, OpenAIModelItem> parseResponse(Reader reader) throws IOException {
        ModelsResponse response = objectMapper.readValue(reader, ModelsResponse.class);
        if (response == null || response.models == null) {
            throw new IOException("OpenRouter metadata response has no data array");
        }
        Map<String, OpenAIModelItem> modelsById = new LinkedHashMap<>();
        for (OpenAIModelItem model : response.models) {
            if (model != null && model.getId() != null && !model.getId().isEmpty()) {
                modelsById.put(model.getId(), model);
            }
        }
        return Collections.unmodifiableMap(modelsById);
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ModelsResponse {
        @JsonProperty("data")
        private List<OpenAIModelItem> models;
    }

    static class MetadataFetchResult {
        private final boolean successful;
        private final Map<String, OpenAIModelItem> models;

        private MetadataFetchResult(boolean successful, Map<String, OpenAIModelItem> models) {
            this.successful = successful;
            this.models = models;
        }

        static MetadataFetchResult success(Map<String, OpenAIModelItem> models) {
            return new MetadataFetchResult(true, models);
        }

        static MetadataFetchResult failed() {
            return new MetadataFetchResult(false, Collections.<String, OpenAIModelItem>emptyMap());
        }
    }
}

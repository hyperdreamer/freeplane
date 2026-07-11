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
import java.util.ArrayList;
import java.util.List;

public class OpenAICompatibleModelDiscovery {
    private final ObjectMapper objectMapper;
    private final OpenRouterModelMetadataInterpreter openRouterInterpreter;
    private final RequestyModelMetadataInterpreter requestyInterpreter;

    public OpenAICompatibleModelDiscovery() {
        objectMapper = new ObjectMapper();
        openRouterInterpreter = new OpenRouterModelMetadataInterpreter();
        requestyInterpreter = new RequestyModelMetadataInterpreter();
    }

    public AIModelDiscoveryResult discover(OpenAICompatibleProviderConfiguration configuration) {
        if (configuration == null || !configuration.isConfigured()
            || configuration.getModelsAddress().isEmpty()) {
            return AIModelDiscoveryResult.failed();
        }
        try {
            HttpURLConnection connection = openConnection(new URL(configuration.getModelsAddress()));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            if (!configuration.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + configuration.getApiKey());
            }
            if (configuration.getProvider() == OpenAICompatibleProvider.OPENROUTER) {
                connection.setRequestProperty("HTTP-Referer", "https://github.com/freeplane/freeplane");
                connection.setRequestProperty("X-Title", "Freeplane");
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return AIModelDiscoveryResult.failed();
            }
            try (InputStream inputStream = connection.getInputStream();
                 Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return AIModelDiscoveryResult.success(parseResponse(reader, configuration.getProvider()));
            }
        }
        catch (IOException | RuntimeException exception) {
            return AIModelDiscoveryResult.failed();
        }
    }

    List<DiscoveredAIModel> parseResponse(Reader reader,
                                          OpenAICompatibleProvider provider) throws IOException {
        ModelsResponse response = objectMapper.readValue(reader, ModelsResponse.class);
        if (response == null || response.models == null) {
            throw new IOException("Model response has no data array");
        }
        List<DiscoveredAIModel> models = new ArrayList<>();
        for (OpenAIModelItem modelItem : response.models) {
            if (modelItem == null || modelItem.getId() == null || modelItem.getId().trim().isEmpty()) {
                continue;
            }
            models.add(new DiscoveredAIModel(
                provider.getProviderName(),
                modelItem.getId().trim(),
                modelItem.isFreeModel(),
                nativeCapabilities(provider, modelItem)));
        }
        return models;
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private AIModelCapabilities nativeCapabilities(OpenAICompatibleProvider provider,
                                                    OpenAIModelItem modelItem) {
        switch (provider) {
            case OPENROUTER:
                return openRouterInterpreter.interpret(modelItem);
            case REQUESTY:
                return requestyInterpreter.interpret(modelItem);
            case CUSTOM:
                if (modelItem.getSupportedParameters() != null
                    || modelItem.getOutputModalities() != null) {
                    return openRouterInterpreter.interpret(modelItem);
                }
                if (modelItem.getApi() != null || modelItem.getSupportsToolCalling() != null) {
                    return requestyInterpreter.interpret(modelItem);
                }
                return AIModelCapabilities.UNKNOWN;
            case OPENAI:
            default:
                return AIModelCapabilities.UNKNOWN;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ModelsResponse {
        @JsonProperty("data")
        private List<OpenAIModelItem> models;
    }
}

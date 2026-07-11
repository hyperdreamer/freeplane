package org.freeplane.plugin.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OllamaModelDiscovery {
    private final ObjectMapper objectMapper;
    private final OpenRouterModelMetadataCatalog metadataCatalog;
    private final OpenRouterModelMetadataInterpreter metadataInterpreter;

    public OllamaModelDiscovery(OpenRouterModelMetadataCatalog metadataCatalog) {
        this.objectMapper = new ObjectMapper();
        this.metadataCatalog = metadataCatalog;
        this.metadataInterpreter = new OpenRouterModelMetadataInterpreter();
    }

    public AIModelDiscoveryResult discover(AIProviderConfiguration configuration) {
        if (configuration == null || !configuration.isOllamaConfigured()) {
            return AIModelDiscoveryResult.failed();
        }
        TagsFetchResult tagsResult = fetchTags(configuration);
        if (!tagsResult.successful) {
            return AIModelDiscoveryResult.failed();
        }
        List<DiscoveredAIModel> discoveredModels = new ArrayList<>();
        for (String modelName : tagsResult.modelNames) {
            ShowFetchResult showResult = fetchShow(configuration, modelName);
            AIModelCapabilities capabilities = capabilitiesFromShow(showResult);
            if (capabilities == null) {
                capabilities = metadataInterpreter.interpret(metadataCatalog.find(modelName));
            }
            if (capabilities.isToolCapableTextModel()) {
                discoveredModels.add(new DiscoveredAIModel(
                    AIChatModelFactory.PROVIDER_NAME_OLLAMA,
                    modelName,
                    false,
                    capabilities));
            }
        }
        return AIModelDiscoveryResult.success(discoveredModels);
    }

    TagsFetchResult fetchTags(AIProviderConfiguration configuration) {
        String endpoint = appendPath(configuration.getOllamaServiceAddress(), "api/tags");
        try {
            HttpURLConnection connection = openConnection(new URL(endpoint));
            configureConnection(connection, "GET", configuration.getOllamaRequestHeaders());
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return TagsFetchResult.failed();
            }
            try (InputStream inputStream = connection.getInputStream();
                 Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return TagsFetchResult.success(parseTags(reader));
            }
        }
        catch (IOException | RuntimeException exception) {
            return TagsFetchResult.failed();
        }
    }

    ShowFetchResult fetchShow(AIProviderConfiguration configuration, String modelName) {
        String endpoint = appendPath(configuration.getOllamaServiceAddress(), "api/show");
        try {
            HttpURLConnection connection = openConnection(new URL(endpoint));
            configureConnection(connection, "POST", configuration.getOllamaRequestHeaders());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] requestBody = objectMapper.writeValueAsBytes(
                Collections.singletonMap("model", modelName));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return ShowFetchResult.failed();
            }
            try (InputStream inputStream = connection.getInputStream();
                 Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return ShowFetchResult.success(parseShow(reader));
            }
        }
        catch (IOException | RuntimeException exception) {
            return ShowFetchResult.failed();
        }
    }

    List<String> parseTags(Reader reader) throws IOException {
        TagsResponse response = objectMapper.readValue(reader, TagsResponse.class);
        if (response == null || response.models == null) {
            throw new IOException("Ollama tags response has no models array");
        }
        List<String> modelNames = new ArrayList<>();
        for (TagsModelItem model : response.models) {
            if (model != null && model.name != null && !model.name.trim().isEmpty()) {
                modelNames.add(model.name.trim());
            }
        }
        return modelNames;
    }

    List<String> parseShow(Reader reader) throws IOException {
        ShowResponse response = objectMapper.readValue(reader, ShowResponse.class);
        return response == null ? null : response.capabilities;
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private AIModelCapabilities capabilitiesFromShow(ShowFetchResult showResult) {
        if (!showResult.successful || showResult.capabilities == null
            || showResult.capabilities.isEmpty()) {
            return null;
        }
        return new AIModelCapabilities(
            supportFor(showResult.capabilities, "completion"),
            supportFor(showResult.capabilities, "tools"));
    }

    private CapabilitySupport supportFor(List<String> capabilities, String requiredCapability) {
        for (String capability : capabilities) {
            if (requiredCapability.equalsIgnoreCase(capability)) {
                return CapabilitySupport.SUPPORTED;
            }
        }
        return CapabilitySupport.UNSUPPORTED;
    }

    private void configureConnection(HttpURLConnection connection,
                                     String requestMethod,
                                     Map<String, String> headers) throws IOException {
        connection.setRequestMethod(requestMethod);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
    }

    private String appendPath(String baseAddress, String path) {
        return baseAddress.endsWith("/") ? baseAddress + path : baseAddress + "/" + path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TagsResponse {
        @JsonProperty("models")
        private List<TagsModelItem> models;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TagsModelItem {
        @JsonProperty("name")
        private String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ShowResponse {
        @JsonProperty("capabilities")
        private List<String> capabilities;
    }

    static class TagsFetchResult {
        private final boolean successful;
        private final List<String> modelNames;

        private TagsFetchResult(boolean successful, List<String> modelNames) {
            this.successful = successful;
            this.modelNames = modelNames;
        }

        static TagsFetchResult success(List<String> modelNames) {
            return new TagsFetchResult(true, modelNames);
        }

        static TagsFetchResult failed() {
            return new TagsFetchResult(false, Collections.<String>emptyList());
        }
    }

    static class ShowFetchResult {
        private final boolean successful;
        private final List<String> capabilities;

        private ShowFetchResult(boolean successful, List<String> capabilities) {
            this.successful = successful;
            this.capabilities = capabilities;
        }

        static ShowFetchResult success(List<String> capabilities) {
            return new ShowFetchResult(true, capabilities);
        }

        static ShowFetchResult failed() {
            return new ShowFetchResult(false, null);
        }
    }
}

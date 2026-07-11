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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeminiModelDiscovery {
    private final ObjectMapper objectMapper;
    private final OpenRouterModelMetadataCatalog metadataCatalog;
    private final OpenRouterModelMetadataInterpreter metadataInterpreter;

    public GeminiModelDiscovery(OpenRouterModelMetadataCatalog metadataCatalog) {
        this.objectMapper = new ObjectMapper();
        this.metadataCatalog = metadataCatalog;
        this.metadataInterpreter = new OpenRouterModelMetadataInterpreter();
    }

    public AIModelDiscoveryResult discover(AIProviderConfiguration configuration) {
        if (configuration == null || !configuration.isGeminiConfigured()) {
            return AIModelDiscoveryResult.failed();
        }
        List<GeminiModelItem> candidates = new ArrayList<>();
        String pageToken = null;
        do {
            PageFetchResult page = fetchPage(configuration, pageToken);
            if (!page.successful) {
                return AIModelDiscoveryResult.failed();
            }
            candidates.addAll(page.models);
            pageToken = trimToNull(page.nextPageToken);
        } while (pageToken != null);

        List<DiscoveredAIModel> discoveredModels = new ArrayList<>();
        for (GeminiModelItem candidate : candidates) {
            String modelName = normalizedCandidateName(candidate);
            if (modelName == null) {
                continue;
            }
            OpenAIModelItem metadata = metadataCatalog.find("google/" + modelName);
            AIModelCapabilities capabilities = metadataInterpreter.interpret(metadata);
            if (capabilities.isToolCapableTextModel()) {
                discoveredModels.add(new DiscoveredAIModel(
                    AIChatModelFactory.PROVIDER_NAME_GEMINI,
                    modelName,
                    false,
                    capabilities));
            }
        }
        return AIModelDiscoveryResult.success(discoveredModels);
    }

    PageFetchResult fetchPage(AIProviderConfiguration configuration, String pageToken) {
        String endpoint = appendPath(configuration.getGeminiServiceAddress(), "models")
            + "?pageSize=1000";
        if (pageToken != null && !pageToken.isEmpty()) {
            try {
                endpoint += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8");
            }
            catch (IOException exception) {
                return PageFetchResult.failed();
            }
        }
        try {
            HttpURLConnection connection = openConnection(new URL(endpoint));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("x-goog-api-key", configuration.getGeminiKey());
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return PageFetchResult.failed();
            }
            try (InputStream inputStream = connection.getInputStream();
                 Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return parsePage(reader);
            }
        }
        catch (IOException | RuntimeException exception) {
            return PageFetchResult.failed();
        }
    }

    PageFetchResult parsePage(Reader reader) throws IOException {
        GeminiModelsResponse response = objectMapper.readValue(reader, GeminiModelsResponse.class);
        if (response == null || response.models == null) {
            throw new IOException("Gemini model response has no models array");
        }
        return PageFetchResult.success(response.models, response.nextPageToken);
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private String normalizedCandidateName(GeminiModelItem model) {
        if (model == null || model.name == null || !supportsGenerateContent(model)) {
            return null;
        }
        String modelName = model.name.startsWith("models/")
            ? model.name.substring("models/".length())
            : model.name;
        return trimToNull(modelName);
    }

    private boolean supportsGenerateContent(GeminiModelItem model) {
        if (model.supportedGenerationMethods == null) {
            return false;
        }
        for (String method : model.supportedGenerationMethods) {
            if ("generateContent".equals(method)) {
                return true;
            }
        }
        return false;
    }

    private String appendPath(String baseAddress, String path) {
        return baseAddress.endsWith("/") ? baseAddress + path : baseAddress + "/" + path;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiModelsResponse {
        @JsonProperty("models")
        private List<GeminiModelItem> models;
        @JsonProperty("nextPageToken")
        private String nextPageToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiModelItem {
        @JsonProperty("name")
        private String name;
        @JsonProperty("supportedGenerationMethods")
        private List<String> supportedGenerationMethods;
    }

    static class PageFetchResult {
        private final boolean successful;
        private final List<GeminiModelItem> models;
        private final String nextPageToken;

        private PageFetchResult(boolean successful,
                                List<GeminiModelItem> models,
                                String nextPageToken) {
            this.successful = successful;
            this.models = models;
            this.nextPageToken = nextPageToken;
        }

        static PageFetchResult success(List<GeminiModelItem> models, String nextPageToken) {
            return new PageFetchResult(true, models, nextPageToken);
        }

        static PageFetchResult failed() {
            return new PageFetchResult(false, Collections.<GeminiModelItem>emptyList(), null);
        }
    }
}

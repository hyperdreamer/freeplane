package org.freeplane.plugin.ai.model;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class OllamaModelDiscoveryTest {
    @Test
    public void retainsCompletionModelsWithTools() {
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        RecordingOllamaDiscovery discovery = new RecordingOllamaDiscovery(metadata);
        discovery.addResponse(200, "{\"models\":[{\"name\":\"capable\"},{\"name\":\"no-tools\"},{\"name\":\"no-text\"}]}");
        discovery.addResponse(200, "{\"capabilities\":[\"completion\",\"tools\"]}");
        discovery.addResponse(200, "{\"capabilities\":[\"completion\"]}");
        discovery.addResponse(200, "{\"capabilities\":[\"tools\"]}");

        AIModelDiscoveryResult result = discovery.discover(configuration(""));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getModels()).extracting(DiscoveredAIModel::getModelName)
            .containsExactly("capable");
        verifyNoInteractions(metadata);
    }

    @Test
    public void failedTagsRequestFailsDiscovery() {
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        RecordingOllamaDiscovery discovery = new RecordingOllamaDiscovery(metadata);
        discovery.addResponse(500, "");

        AIModelDiscoveryResult result = discovery.discover(configuration(""));

        assertThat(result.isSuccessful()).isFalse();
        verifyNoInteractions(metadata);
    }

    @Test
    public void showFailureUsesExactOpenRouterFallback() {
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        when(metadata.find("qualified/model")).thenReturn(toolCapable("qualified/model"));
        RecordingOllamaDiscovery discovery = new RecordingOllamaDiscovery(metadata);
        discovery.addResponse(200, "{\"models\":[{\"name\":\"qualified/model\"},{\"name\":\"unmatched\"}]}");
        discovery.addResponse(500, "");
        discovery.addResponse(500, "");

        AIModelDiscoveryResult result = discovery.discover(configuration(""));

        assertThat(result.getModels()).extracting(DiscoveredAIModel::getModelName)
            .containsExactly("qualified/model");
    }

    @Test
    public void appliesConfiguredBearerKeyToTagsAndShow() {
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        RecordingOllamaDiscovery discovery = new RecordingOllamaDiscovery(metadata);
        discovery.addResponse(200, "{\"models\":[{\"name\":\"capable\"}]}");
        discovery.addResponse(200, "{\"capabilities\":[\"completion\",\"tools\"]}");

        discovery.discover(configuration("ollama-key"));

        assertThat(discovery.connections).hasSize(2);
        assertThat(discovery.connections).extracting(
            connection -> connection.getRequestHeader("Authorization"))
            .containsOnly("Bearer ollama-key");
        assertThat(discovery.connections.get(1).getRequestBody()).contains("\"model\":\"capable\"");
    }

    private AIProviderConfiguration configuration(String apiKey) {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.isOllamaConfigured()).thenReturn(true);
        when(configuration.getOllamaServiceAddress()).thenReturn("https://ollama.example");
        when(configuration.getOllamaApiKey()).thenReturn(apiKey);
        when(configuration.getOllamaRequestHeaders()).thenReturn(apiKey.isEmpty()
            ? Collections.<String, String>emptyMap()
            : Collections.singletonMap("Authorization", "Bearer " + apiKey));
        return configuration;
    }

    private OpenAIModelItem toolCapable(String id) {
        return OpenAIModelItem.create(
            id,
            Collections.singletonList("tools"),
            Collections.singletonList("text"),
            null,
            null);
    }

    private static class RecordingOllamaDiscovery extends OllamaModelDiscovery {
        private final Deque<Response> responses = new ArrayDeque<>();
        private final List<StubHttpURLConnection> connections = new ArrayList<>();

        private RecordingOllamaDiscovery(OpenRouterModelMetadataCatalog metadataCatalog) {
            super(metadataCatalog);
        }

        private void addResponse(int status, String body) {
            responses.addLast(new Response(status, body));
        }

        @Override
        protected HttpURLConnection openConnection(URL url) throws IOException {
            Response response = responses.removeFirst();
            StubHttpURLConnection connection = new StubHttpURLConnection(url, response.status, response.body);
            connections.add(connection);
            return connection;
        }
    }

    private static class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}

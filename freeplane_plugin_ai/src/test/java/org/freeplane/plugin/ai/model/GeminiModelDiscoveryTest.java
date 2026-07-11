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

public class GeminiModelDiscoveryTest {
    @Test
    public void discoversAllPagesAndUsesExactGoogleMetadata() {
        AIProviderConfiguration configuration = configuration();
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        when(metadata.find("google/gemini-first")).thenReturn(toolCapable("google/gemini-first"));
        when(metadata.find("google/gemini-second")).thenReturn(toolCapable("google/gemini-second"));
        RecordingGeminiDiscovery discovery = new RecordingGeminiDiscovery(metadata);
        discovery.addResponse(200, "{\"models\":["
            + "{\"name\":\"models/gemini-first\",\"supportedGenerationMethods\":[\"generateContent\"]},"
            + "{\"name\":\"models/not-generation\",\"supportedGenerationMethods\":[\"embedContent\"]}],"
            + "\"nextPageToken\":\"next token\"}");
        discovery.addResponse(200, "{\"models\":["
            + "{\"name\":\"gemini-second\",\"supportedGenerationMethods\":[\"generateContent\"]},"
            + "{\"name\":\"models/no-metadata\",\"supportedGenerationMethods\":[\"generateContent\"]}]}");

        AIModelDiscoveryResult result = discovery.discover(configuration);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getModels()).extracting(DiscoveredAIModel::getModelName)
            .containsExactly("gemini-first", "gemini-second");
        assertThat(discovery.requestUrls).hasSize(2);
        assertThat(discovery.requestUrls.get(1).toString()).contains("pageToken=next+token");
        assertThat(discovery.connections).extracting(
            connection -> connection.getRequestHeader("x-goog-api-key"))
            .containsOnly("gemini-key");
    }

    @Test
    public void failedListRequestFailsDiscovery() {
        AIProviderConfiguration configuration = configuration();
        OpenRouterModelMetadataCatalog metadata = mock(OpenRouterModelMetadataCatalog.class);
        RecordingGeminiDiscovery discovery = new RecordingGeminiDiscovery(metadata);
        discovery.addResponse(500, "");

        AIModelDiscoveryResult result = discovery.discover(configuration);

        assertThat(result.isSuccessful()).isFalse();
        verifyNoInteractions(metadata);
    }

    private AIProviderConfiguration configuration() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.isGeminiConfigured()).thenReturn(true);
        when(configuration.getGeminiServiceAddress()).thenReturn("https://gemini.example/v1beta");
        when(configuration.getGeminiKey()).thenReturn("gemini-key");
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

    private static class RecordingGeminiDiscovery extends GeminiModelDiscovery {
        private final Deque<Response> responses = new ArrayDeque<>();
        private final List<URL> requestUrls = new ArrayList<>();
        private final List<StubHttpURLConnection> connections = new ArrayList<>();

        private RecordingGeminiDiscovery(OpenRouterModelMetadataCatalog metadataCatalog) {
            super(metadataCatalog);
        }

        private void addResponse(int status, String body) {
            responses.addLast(new Response(status, body));
        }

        @Override
        protected HttpURLConnection openConnection(URL url) throws IOException {
            Response response = responses.removeFirst();
            StubHttpURLConnection connection = new StubHttpURLConnection(url, response.status, response.body);
            requestUrls.add(url);
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

package org.freeplane.plugin.ai.model;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AIModelCatalogTest {
    @After
    public void resetCatalogCaches() {
        AIModelCatalog.resetOllamaCacheForTests();
        AIModelCatalog.resetOpenrouterCacheForTests();
    }

    @Test
    public void parseOpenrouterModelsResponse_returnsModels() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        String responsePayload = "{\"data\":["
            + "{\"id\":\"openai/gpt-5\",\"pricing\":{\"prompt\":\"0\",\"completion\":\"0\"}},"
            + "{\"id\":\"unknown/model\",\"pricing\":{\"prompt\":\"0.01\",\"completion\":\"0.02\"}},"
            + "{\"id\":null},"
            + "null"
            + "]}";

        List<AIModelDescriptor> modelDescriptors = uut.parseOpenrouterModelsResponse(new StringReader(responsePayload));

        assertThat(modelDescriptors).hasSize(2);
        AIModelDescriptor firstDescriptor = modelDescriptors.get(0);
        assertThat(firstDescriptor.getProviderName()).isEqualTo(AIChatModelFactory.PROVIDER_NAME_OPENROUTER);
        assertThat(firstDescriptor.getModelName()).isEqualTo("openai/gpt-5");
        assertThat(firstDescriptor.isFreeModel()).isTrue();
        AIModelDescriptor secondDescriptor = modelDescriptors.get(1);
        assertThat(secondDescriptor.getModelName()).isEqualTo("unknown/model");
        assertThat(secondDescriptor.isFreeModel()).isFalse();
    }

    @Test
    public void parseOllamaModelsResponse_returnsModelNames() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        String responsePayload = "{\"models\":["
            + "{\"name\":\"llama3\"},"
            + "{\"name\":\"mistral\"},"
            + "{\"name\":\"\"},"
            + "{}"
            + "]}";

        List<AIModelDescriptor> modelDescriptors = uut.parseOllamaModelsResponse(new StringReader(responsePayload));

        assertThat(modelDescriptors).extracting(AIModelDescriptor::getModelName)
            .containsExactly("llama3", "mistral");
    }

    @Test
    public void parseGeminiModelList_parsesLiteralEntries() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        String modelListValue = "gemini-3-pro-preview\n"
            + "gemini-3-flash-preview\n"
            + "placeholder-model";

        List<AIModelDescriptor> modelDescriptors = uut.parseGeminiModelList(modelListValue);

        assertThat(modelDescriptors).hasSize(3);
        AIModelDescriptor descriptor = modelDescriptors.get(0);
        assertThat(descriptor.getProviderName()).isEqualTo(AIChatModelFactory.PROVIDER_NAME_GEMINI);
        assertThat(descriptor.getModelName()).isEqualTo("gemini-3-pro-preview");
    }

    @Test
    public void parseLiteralProviderModelList_ignoresWildcardEntries() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);

        List<AIModelDescriptor> modelDescriptors = uut.parseLiteralProviderModelList(
            AIChatModelFactory.PROVIDER_NAME_OPENROUTER,
            "openai/gpt-5,\nopenai/*,\n?\nclaude-sonnet-4"
        );

        assertThat(modelDescriptors).extracting(AIModelDescriptor::getModelName)
            .containsExactly("openai/gpt-5", "claude-sonnet-4");
    }

    @Test
    public void filterModelDescriptors_appliesWildcardAllowlist() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        List<AIModelDescriptor> modelDescriptors = Arrays.asList(
            new AIModelDescriptor("openrouter", "openai/gpt-5", "OpenRouter: openai/gpt-5", false),
            new AIModelDescriptor("gemini", "gemini-3-flash-preview", "Gemini: gemini-3-flash-preview", false),
            new AIModelDescriptor("ollama", "llama3", "Ollama: llama3", false)
        );

        List<AIModelDescriptor> filteredDescriptors = uut.filterModelDescriptors(
            modelDescriptors,
            "openai/*, *-preview"
        );

        assertThat(filteredDescriptors).extracting(AIModelDescriptor::getModelName)
            .containsExactly("openai/gpt-5", "gemini-3-flash-preview");
    }

    @Test
    public void applyRequestHeaders_setsAllConfiguredHeaders() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        RecordingHttpURLConnection connection = new RecordingHttpURLConnection();
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Authorization", "Bearer token-123");
        requestHeaders.put("X-Test", "value");

        uut.applyRequestHeaders(connection, requestHeaders);

        assertThat(connection.getHeader("Authorization")).isEqualTo("Bearer token-123");
        assertThat(connection.getHeader("X-Test")).isEqualTo("value");
    }

    @Test
    public void applyRequestHeaders_doesNothingForEmptyHeaders() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog uut = new AIModelCatalog(configuration);
        RecordingHttpURLConnection connection = new RecordingHttpURLConnection();

        uut.applyRequestHeaders(connection, Collections.<String, String>emptyMap());

        assertThat(connection.headerCount()).isZero();
    }

    @Test
    public void getAvailableModels_retriesAfterFailedOllamaFetchWithoutCachingFailure() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.hasOllamaServiceAddress()).thenReturn(true);
        when(configuration.getOllamaServiceAddress()).thenReturn("https://ollama.example");
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());
        TestableAIModelCatalog uut = new TestableAIModelCatalog(configuration,
            AIModelCatalog.OllamaModelsFetchResult.failed(),
            AIModelCatalog.OllamaModelsFetchResult.success(Collections.singletonList(
                new AIModelDescriptor("ollama", "llama3", "Ollama: llama3", false))));

        List<AIModelDescriptor> firstResponse = uut.getAvailableModels(true);
        List<AIModelDescriptor> secondResponse = uut.getAvailableModels(true);

        assertThat(firstResponse).isEmpty();
        assertThat(secondResponse).extracting(AIModelDescriptor::getModelName).containsExactly("llama3");
        assertThat(uut.fetchCount()).isEqualTo(2);
    }

    @Test
    public void getAvailableModels_refreshesImmediatelyWhenOllamaServiceAddressChanges() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AtomicReference<String> serviceAddress = new AtomicReference<>("https://ollama.one");
        when(configuration.hasOllamaServiceAddress()).thenReturn(true);
        when(configuration.getOllamaServiceAddress()).thenAnswer(invocation -> serviceAddress.get());
        when(configuration.getOllamaRequestHeaders()).thenReturn(Collections.emptyMap());
        TestableAIModelCatalog uut = new TestableAIModelCatalog(configuration,
            AIModelCatalog.OllamaModelsFetchResult.success(Collections.singletonList(
                new AIModelDescriptor("ollama", "first-model", "Ollama: first-model", false))),
            AIModelCatalog.OllamaModelsFetchResult.success(Collections.singletonList(
                new AIModelDescriptor("ollama", "second-model", "Ollama: second-model", false))));

        List<AIModelDescriptor> firstResponse = uut.getAvailableModels(true);
        serviceAddress.set("https://ollama.two");
        List<AIModelDescriptor> secondResponse = uut.getAvailableModels(true);

        assertThat(firstResponse).extracting(AIModelDescriptor::getModelName).containsExactly("first-model");
        assertThat(secondResponse).extracting(AIModelDescriptor::getModelName).containsExactly("second-model");
        assertThat(uut.fetchCount()).isEqualTo(2);
    }

    @Test
    public void getAvailableModels_refreshesImmediatelyWhenOllamaTokenHeadersChange() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AtomicReference<Map<String, String>> requestHeaders =
            new AtomicReference<>(Collections.singletonMap("Authorization", "Bearer token-a"));
        when(configuration.hasOllamaServiceAddress()).thenReturn(true);
        when(configuration.getOllamaServiceAddress()).thenReturn("https://ollama.example");
        when(configuration.getOllamaRequestHeaders()).thenAnswer(invocation -> requestHeaders.get());
        TestableAIModelCatalog uut = new TestableAIModelCatalog(configuration,
            AIModelCatalog.OllamaModelsFetchResult.success(Collections.singletonList(
                new AIModelDescriptor("ollama", "token-a-model", "Ollama: token-a-model", false))),
            AIModelCatalog.OllamaModelsFetchResult.success(Collections.singletonList(
                new AIModelDescriptor("ollama", "token-b-model", "Ollama: token-b-model", false))));

        List<AIModelDescriptor> firstResponse = uut.getAvailableModels(true);
        requestHeaders.set(Collections.singletonMap("Authorization", "Bearer token-b"));
        List<AIModelDescriptor> secondResponse = uut.getAvailableModels(true);

        assertThat(firstResponse).extracting(AIModelDescriptor::getModelName).containsExactly("token-a-model");
        assertThat(secondResponse).extracting(AIModelDescriptor::getModelName).containsExactly("token-b-model");
        assertThat(uut.fetchCount()).isEqualTo(2);
    }

    @Test
    public void getAvailableModels_usesOpenrouterAllowlistLiteralsWhenDiscoveryFails() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterModelAllowlistValue()).thenReturn("openai/gpt-5,\nopenai/*,\nanthropic/claude-sonnet-4");
        TestableAIModelCatalog uut = new TestableAIModelCatalog(configuration);
        uut.setOpenrouterModels(Collections.<AIModelDescriptor>emptyList());

        List<AIModelDescriptor> modelDescriptors = uut.getAvailableModels(true);

        assertThat(modelDescriptors).extracting(AIModelDescriptor::getModelName)
            .containsExactly("openai/gpt-5", "anthropic/claude-sonnet-4");
    }

    @Test
    public void getAvailableModels_prefersOpenrouterDiscoveryResultsOverAllowlistFallback() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("test-key");
        when(configuration.getOpenrouterModelAllowlistValue()).thenReturn("");
        TestableAIModelCatalog uut = new TestableAIModelCatalog(configuration);
        uut.setOpenrouterModels(Collections.singletonList(
            new AIModelDescriptor("openrouter", "remote-model", "OpenRouter: remote-model", false)));

        List<AIModelDescriptor> modelDescriptors = uut.getAvailableModels(true);

        assertThat(modelDescriptors).extracting(AIModelDescriptor::getModelName).containsExactly("remote-model");
    }

    private static class TestableAIModelCatalog extends AIModelCatalog {
        private final List<OllamaModelsFetchResult> fetchResults;
        private int fetchIndex;
        private int fetchCount;
        private List<AIModelDescriptor> openrouterModels = Collections.emptyList();

        private TestableAIModelCatalog(AIProviderConfiguration configuration,
                                       OllamaModelsFetchResult... fetchResults) {
            super(configuration);
            this.fetchResults = Arrays.asList(fetchResults);
        }

        @Override
        List<AIModelDescriptor> fetchOpenrouterModels() {
            return openrouterModels;
        }

        @Override
        OllamaModelsFetchResult fetchOllamaModels() {
            fetchCount++;
            int selectedIndex = Math.min(fetchIndex, fetchResults.size() - 1);
            OllamaModelsFetchResult fetchResult = fetchResults.get(selectedIndex);
            fetchIndex++;
            return fetchResult;
        }

        private int fetchCount() {
            return fetchCount;
        }

        private void setOpenrouterModels(List<AIModelDescriptor> openrouterModels) {
            this.openrouterModels = openrouterModels;
        }
    }

    private static class RecordingHttpURLConnection extends HttpURLConnection {
        private final Map<String, String> headers = new HashMap<>();

        private RecordingHttpURLConnection() {
            super(createUnusedUrl());
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        private String getHeader(String key) {
            return headers.get(key);
        }

        private int headerCount() {
            return headers.size();
        }

        private static URL createUnusedUrl() {
            try {
                return new URL("http://localhost");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}

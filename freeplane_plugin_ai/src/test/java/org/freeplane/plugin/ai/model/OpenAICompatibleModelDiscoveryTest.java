package org.freeplane.plugin.ai.model;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAICompatibleModelDiscoveryTest {
    @Test
    public void parsesStandardModelResponseForEveryProviderIdentity() throws Exception {
        OpenAICompatibleModelDiscovery discovery = new OpenAICompatibleModelDiscovery();
        String payload = "{\"data\":[{\"id\":\"model-a\",\"unknown\":true},null,{\"id\":null}]}";

        for (OpenAICompatibleProvider provider : OpenAICompatibleProvider.values()) {
            List<DiscoveredAIModel> models = discovery.parseResponse(new StringReader(payload), provider);

            assertThat(models).hasSize(1);
            assertThat(models.get(0).getProviderName()).isEqualTo(provider.getProviderName());
            assertThat(models.get(0).getModelName()).isEqualTo("model-a");
        }
    }

    @Test
    public void usesProviderEndpointAndBearerCredential() {
        for (OpenAICompatibleProvider provider : OpenAICompatibleProvider.values()) {
            String key = provider == OpenAICompatibleProvider.CUSTOM ? "" : provider.name() + "-key";
            RecordingDiscovery discovery = new RecordingDiscovery(200, "{\"data\":[]}");
            OpenAICompatibleProviderConfiguration configuration = configuration(provider, key);

            AIModelDiscoveryResult result = discovery.discover(configuration);

            assertThat(result.isSuccessful()).isTrue();
            assertThat(discovery.requestUrl.toString()).isEqualTo(configuration.getModelsAddress());
            if (key.isEmpty()) {
                assertThat(discovery.connection.getRequestHeader("Authorization")).isNull();
            }
            else {
                assertThat(discovery.connection.getRequestHeader("Authorization"))
                    .isEqualTo("Bearer " + key);
            }
        }
        RecordingDiscovery authenticatedCustom = new RecordingDiscovery(200, "{\"data\":[]}");

        authenticatedCustom.discover(configuration(OpenAICompatibleProvider.CUSTOM, "custom-key"));

        assertThat(authenticatedCustom.connection.getRequestHeader("Authorization"))
            .isEqualTo("Bearer custom-key");
    }

    @Test
    public void distinguishesSuccessfulEmptyResponseFromFailure() {
        OpenAICompatibleProviderConfiguration configuration =
            configuration(OpenAICompatibleProvider.OPENAI, "key");

        assertThat(new RecordingDiscovery(200, "{\"data\":[]}").discover(configuration).isSuccessful())
            .isTrue();
        assertThat(new RecordingDiscovery(500, "").discover(configuration).isSuccessful())
            .isFalse();
        assertThat(new RecordingDiscovery(200, "not-json").discover(configuration).isSuccessful())
            .isFalse();
        assertThat(new RecordingDiscovery(200, "{}").discover(configuration).isSuccessful())
            .isFalse();
    }

    private OpenAICompatibleProviderConfiguration configuration(OpenAICompatibleProvider provider,
                                                                 String key) {
        String serviceAddress = "https://" + provider.getProviderName() + ".example/v1";
        return new OpenAICompatibleProviderConfiguration(
            provider,
            serviceAddress,
            serviceAddress + "/models",
            key,
            AIModelListConfiguration.parse(""));
    }

    private static class RecordingDiscovery extends OpenAICompatibleModelDiscovery {
        private final int responseCode;
        private final String responseBody;
        private URL requestUrl;
        private StubHttpURLConnection connection;

        private RecordingDiscovery(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        protected HttpURLConnection openConnection(URL url) throws IOException {
            requestUrl = url;
            connection = new StubHttpURLConnection(url, responseCode, responseBody);
            return connection;
        }
    }
}

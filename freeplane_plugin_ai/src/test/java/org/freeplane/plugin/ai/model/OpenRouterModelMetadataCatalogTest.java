package org.freeplane.plugin.ai.model;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenRouterModelMetadataCatalogTest {
    @Test
    public void matchesOnlyExactProviderQualifiedIds() {
        MutableMetadataCatalog catalog = catalog(new AtomicLong(0));
        catalog.respond(200, metadataPayload());

        assertThat(catalog.find("openai/gpt-5")).isNotNull();
        assertThat(catalog.find("google/gemini-3-flash")).isNotNull();
        assertThat(catalog.find("gpt-5")).isNull();
        assertThat(catalog.find("openai/gpt-5-mini")).isNull();
        assertThat(catalog.find("google/gemini-3")).isNull();
    }

    @Test
    public void failedRefreshMakesMetadataUnavailable() {
        AtomicLong now = new AtomicLong(0);
        MutableMetadataCatalog catalog = catalog(now);
        catalog.respond(200, metadataPayload());
        assertThat(catalog.find("openai/gpt-5")).isNotNull();

        now.set(101);
        catalog.respond(500, "");

        assertThat(catalog.find("openai/gpt-5")).isNull();
    }

    private MutableMetadataCatalog catalog(AtomicLong now) {
        AIModelCatalogState state = new AIModelCatalogState(now::get, 100);
        return new MutableMetadataCatalog(state);
    }

    private String metadataPayload() {
        return "{\"data\":["
            + "{\"id\":\"openai/gpt-5\",\"supported_parameters\":[\"tools\"],"
            + "\"architecture\":{\"output_modalities\":[\"text\"]}},"
            + "{\"id\":\"google/gemini-3-flash\",\"supported_parameters\":[\"tools\"],"
            + "\"architecture\":{\"output_modalities\":[\"text\"]}}]}";
    }

    private static class MutableMetadataCatalog extends OpenRouterModelMetadataCatalog {
        private int responseCode;
        private String responseBody;

        private MutableMetadataCatalog(AIModelCatalogState state) {
            super(state, "https://metadata.example/models");
        }

        private void respond(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        protected HttpURLConnection openConnection(URL url) throws IOException {
            return new StubHttpURLConnection(url, responseCode, responseBody);
        }
    }
}

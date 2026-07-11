package org.freeplane.plugin.ai.model;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AIModelCatalogTest {
    @Test
    public void combinesEveryConfiguredProvider() {
        List<OpenAICompatibleProviderConfiguration> providers = Arrays.asList(
            automatic(OpenAICompatibleProvider.OPENAI, "https://openai.example", "a"),
            automatic(OpenAICompatibleProvider.OPENROUTER, "https://openrouter.example", "b"),
            automatic(OpenAICompatibleProvider.REQUESTY, "https://requesty.example", "c"),
            automatic(OpenAICompatibleProvider.CUSTOM, "https://custom.example", ""));
        AIProviderConfiguration configuration = configuration(providers);
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(any(OpenAICompatibleProviderConfiguration.class)))
            .thenAnswer(invocation -> {
                OpenAICompatibleProviderConfiguration provider = invocation.getArgument(0);
                return AIModelDiscoveryResult.success(Collections.singletonList(
                    discovered(provider.getProviderName(), "same-model", supported())));
            });

        List<AIModelDescriptor> models = catalog(configuration, discovery, metadata(), state()).getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getProviderName)
            .containsExactly("custom", "openai", "openrouter", "requesty");
        assertThat(models).extracting(AIModelDescriptor::getDisplayName)
            .containsExactly(
                "Custom: same-model",
                "OpenAI: same-model",
                "OpenRouter: same-model",
                "Requesty: same-model");
    }

    @Test
    public void explicitProviderSkipsDiscoveryAndMetadata() {
        OpenAICompatibleProviderConfiguration provider = new OpenAICompatibleProviderConfiguration(
            OpenAICompatibleProvider.OPENAI,
            "https://openai.example/v1",
            "https://openai.example/v1/models",
            "key",
            AIModelListConfiguration.parse("gpt-explicit, gpt-*, second"));
        AIProviderConfiguration configuration = configuration(Collections.singletonList(provider));
        when(configuration.isGeminiConfigured()).thenReturn(true);
        when(configuration.getGeminiModelListConfiguration())
            .thenReturn(AIModelListConfiguration.parse("gemini-explicit"));
        when(configuration.isOllamaConfigured()).thenReturn(true);
        when(configuration.getOllamaModelListConfiguration())
            .thenReturn(AIModelListConfiguration.parse("ollama-explicit"));
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        OpenRouterModelMetadataCatalog metadata = metadata();
        GeminiModelDiscovery geminiDiscovery = mock(GeminiModelDiscovery.class);
        OllamaModelDiscovery ollamaDiscovery = mock(OllamaModelDiscovery.class);
        AIModelCatalog catalog = new AIModelCatalog(
            configuration,
            state(),
            discovery,
            metadata,
            geminiDiscovery,
            ollamaDiscovery);

        List<AIModelDescriptor> models = catalog.getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getModelName)
            .containsExactly("gemini-explicit", "ollama-explicit", "gpt-explicit", "second");
        verifyNoInteractions(discovery, metadata, geminiDiscovery, ollamaDiscovery);
    }

    @Test
    public void automaticOpenAIUsesExactDirectAndMetadataIntersection() {
        OpenAICompatibleProviderConfiguration provider =
            automatic(OpenAICompatibleProvider.OPENAI, "https://openai.example", "key");
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(provider)).thenReturn(AIModelDiscoveryResult.success(Arrays.asList(
            discovered("openai", "gpt-visible", AIModelCapabilities.UNKNOWN),
            discovered("openai", "gpt-without-metadata", AIModelCapabilities.UNKNOWN))));
        OpenRouterModelMetadataCatalog metadata = metadata();
        when(metadata.find("openai/gpt-visible")).thenReturn(toolCapableMetadata("openai/gpt-visible"));

        List<AIModelDescriptor> models = catalog(
            configuration(Collections.singletonList(provider)), discovery, metadata, state())
            .getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getModelName)
            .containsExactly("gpt-visible");
        verify(metadata, never()).find("gpt-visible");
    }

    @Test
    public void automaticOpenRouterAndRequestyUseNativeMetadata() throws Exception {
        OpenAICompatibleProviderConfiguration openRouter =
            automatic(OpenAICompatibleProvider.OPENROUTER, "https://openrouter.example", "router-key");
        OpenAICompatibleProviderConfiguration requesty =
            automatic(OpenAICompatibleProvider.REQUESTY, "https://requesty.example", "requesty-key");
        OpenAICompatibleModelDiscovery parser = new OpenAICompatibleModelDiscovery();
        List<DiscoveredAIModel> openRouterModels = parser.parseResponse(new StringReader(
            "{\"data\":["
                + "{\"id\":\"provider/tool\",\"supported_parameters\":[\"tools\"],"
                + "\"architecture\":{\"output_modalities\":[\"text\"]}},"
                + "{\"id\":\"provider/no-tool\",\"supported_parameters\":[],"
                + "\"architecture\":{\"output_modalities\":[\"text\"]}}]}"),
            OpenAICompatibleProvider.OPENROUTER);
        List<DiscoveredAIModel> requestyModels = parser.parseResponse(new StringReader(
            "{\"data\":["
                + "{\"id\":\"provider/chat-tool\",\"api\":\"chat\","
                + "\"supports_tool_calling\":true},"
                + "{\"id\":\"provider/image\",\"api\":\"image\","
                + "\"supports_tool_calling\":true}]}"),
            OpenAICompatibleProvider.REQUESTY);
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(openRouter)).thenReturn(AIModelDiscoveryResult.success(openRouterModels));
        when(discovery.discover(requesty)).thenReturn(AIModelDiscoveryResult.success(requestyModels));
        OpenRouterModelMetadataCatalog metadata = metadata();

        List<AIModelDescriptor> models = catalog(
            configuration(Arrays.asList(openRouter, requesty)), discovery, metadata, state())
            .getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getModelName)
            .containsExactly("provider/tool", "provider/chat-tool");
        verifyNoInteractions(metadata);
    }

    @Test
    public void automaticCustomPrefersNativeMetadataThenOpenRouter() throws Exception {
        OpenAICompatibleProviderConfiguration custom =
            automatic(OpenAICompatibleProvider.CUSTOM, "https://custom.example", "");
        OpenAICompatibleModelDiscovery parser = new OpenAICompatibleModelDiscovery();
        List<DiscoveredAIModel> customModels = parser.parseResponse(new StringReader(
            "{\"data\":["
                + "{\"id\":\"native\",\"supported_parameters\":[\"tools\"],"
                + "\"architecture\":{\"output_modalities\":[\"text\"]}},"
                + "{\"id\":\"fallback\"},"
                + "{\"id\":\"explicitly-unsupported\",\"supported_parameters\":[],"
                + "\"architecture\":{\"output_modalities\":[\"text\"]}}]}"),
            OpenAICompatibleProvider.CUSTOM);
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(custom)).thenReturn(AIModelDiscoveryResult.success(customModels));
        OpenRouterModelMetadataCatalog metadata = metadata();
        when(metadata.find("fallback")).thenReturn(toolCapableMetadata("fallback"));

        List<AIModelDescriptor> models = catalog(
            configuration(Collections.singletonList(custom)), discovery, metadata, state())
            .getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getModelName)
            .containsExactly("fallback", "native");
        verify(metadata, never()).find("explicitly-unsupported");
    }

    @Test
    public void providerFailureRemovesOnlyThatProvider() {
        OpenAICompatibleProviderConfiguration openAI =
            automatic(OpenAICompatibleProvider.OPENAI, "https://openai.example", "openai-key");
        OpenAICompatibleProviderConfiguration requesty =
            automatic(OpenAICompatibleProvider.REQUESTY, "https://requesty.example", "requesty-key");
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(openAI)).thenReturn(AIModelDiscoveryResult.failed());
        when(discovery.discover(requesty)).thenReturn(AIModelDiscoveryResult.success(
            Collections.singletonList(discovered("requesty", "working", supported()))));

        List<AIModelDescriptor> models = catalog(
            configuration(Arrays.asList(openAI, requesty)), discovery, metadata(), state())
            .getAvailableModels(true);

        assertThat(models).extracting(AIModelDescriptor::getProviderName)
            .containsExactly("requesty");
    }

    @Test
    public void freshCacheAvoidsRequestsAndFailedRefreshClearsSource() {
        AtomicLong now = new AtomicLong(0);
        AIModelCatalogState state = new AIModelCatalogState(now::get, 100);
        OpenAICompatibleProviderConfiguration firstConfiguration =
            automatic(OpenAICompatibleProvider.REQUESTY, "https://requesty.one", "key");
        OpenAICompatibleProviderConfiguration changedConfiguration =
            automatic(OpenAICompatibleProvider.REQUESTY, "https://requesty.two", "key");
        OpenAICompatibleModelDiscovery discovery = mock(OpenAICompatibleModelDiscovery.class);
        when(discovery.discover(firstConfiguration)).thenReturn(AIModelDiscoveryResult.success(
            Collections.singletonList(discovered("requesty", "first", supported()))));
        when(discovery.discover(changedConfiguration))
            .thenReturn(AIModelDiscoveryResult.success(
                Collections.singletonList(discovered("requesty", "second", supported()))))
            .thenReturn(AIModelDiscoveryResult.failed());

        AIModelCatalog firstCatalog = catalog(
            configuration(Collections.singletonList(firstConfiguration)), discovery, metadata(), state);
        assertThat(firstCatalog.getAvailableModels(true)).extracting(AIModelDescriptor::getModelName)
            .containsExactly("first");
        assertThat(firstCatalog.getAvailableModels(true)).extracting(AIModelDescriptor::getModelName)
            .containsExactly("first");
        verify(discovery, times(1)).discover(firstConfiguration);

        AIModelCatalog changedCatalog = catalog(
            configuration(Collections.singletonList(changedConfiguration)), discovery, metadata(), state);
        assertThat(changedCatalog.getAvailableModels(true)).extracting(AIModelDescriptor::getModelName)
            .containsExactly("second");
        now.set(101);
        assertThat(changedCatalog.getAvailableModels(true)).isEmpty();
        assertThat(changedCatalog.getAvailableModels(false)).isEmpty();
        verify(discovery, times(2)).discover(changedConfiguration);
    }

    private AIModelCatalog catalog(AIProviderConfiguration configuration,
                                   OpenAICompatibleModelDiscovery discovery,
                                   OpenRouterModelMetadataCatalog metadata,
                                   AIModelCatalogState state) {
        return new AIModelCatalog(configuration, state, discovery, metadata);
    }

    private AIProviderConfiguration configuration(
        List<OpenAICompatibleProviderConfiguration> providerConfigurations) {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenAICompatibleConfigurations()).thenReturn(providerConfigurations);
        return configuration;
    }

    private OpenAICompatibleProviderConfiguration automatic(OpenAICompatibleProvider provider,
                                                              String serviceAddress,
                                                              String key) {
        return new OpenAICompatibleProviderConfiguration(
            provider,
            serviceAddress,
            serviceAddress + "/models",
            key,
            AIModelListConfiguration.parse(""));
    }

    private DiscoveredAIModel discovered(String provider,
                                         String model,
                                         AIModelCapabilities capabilities) {
        return new DiscoveredAIModel(provider, model, false, capabilities);
    }

    private AIModelCapabilities supported() {
        return new AIModelCapabilities(CapabilitySupport.SUPPORTED, CapabilitySupport.SUPPORTED);
    }

    private OpenRouterModelMetadataCatalog metadata() {
        return mock(OpenRouterModelMetadataCatalog.class);
    }

    private OpenAIModelItem toolCapableMetadata(String id) {
        return OpenAIModelItem.create(
            id,
            Collections.singletonList("tools"),
            Collections.singletonList("text"),
            null,
            null);
    }

    private AIModelCatalogState state() {
        return new AIModelCatalogState(() -> 0L, 100L);
    }
}

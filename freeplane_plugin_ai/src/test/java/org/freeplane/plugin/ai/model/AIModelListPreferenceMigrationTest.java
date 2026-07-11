package org.freeplane.plugin.ai.model;

import java.util.Properties;
import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AIModelListPreferenceMigrationTest {
    @Test
    public void discardsEveryKnownHistoricalDefault() {
        assertHistoricalDefaultIsDiscarded(
            "ai_openrouter_model_allowlist",
            "ai_openrouter_models",
            "deepseek/deepseek-r1-0528:free,meta-llama/llama-3.3-70b-instruct:free,"
                + "z-ai/glm-4.5-air:free,qwen/qwen-2.5-72b-instruct:free,"
                + "deepseek/deepseek-r1-0528,anthropic/claude-3.5-sonnet,"
                + "anthropic/claude-3.5-haiku,openai/gpt-5,openai/gpt-5-mini,"
                + "openai/gpt-5-nano,openai/gpt-4o,openai/gpt-4o-mini,"
                + "meta-llama/llama-3.3-70b-instruct,google/gemini-2.5-pro,"
                + "google/gemini-3-flash-preview");
        assertHistoricalDefaultIsDiscarded(
            "ai_openrouter_model_allowlist",
            "ai_openrouter_models",
            " google/gemini-3-flash-preview, openai/gpt-5.2,"
                + "qwen/qwen3-next-80b-a3b-instruct:free,deepseek/deepseek-r1-0528:free,"
                + "meta-llama/llama-3.3-70b-instruct:free,z-ai/glm-4.5-air:free,"
                + "deepseek/deepseek-v3.2,anthropic/claude-sonnet-4.5,"
                + "anthropic/claude-haiku-4.5,anthropic/claude-opus-4.6,"
                + "openai/gpt-5,openai/gpt-5-mini,openai/gpt-5-nano,"
                + "openai/gpt-5.2-chat,meta-llama/llama-4-maverick,"
                + "google/gemini-2.5-pro");
        assertHistoricalDefaultIsDiscarded(
            "ai_openrouter_model_allowlist",
            "ai_openrouter_models",
            "deepseek/deepseek-v3.2,anthropic/claude-sonnet-4.6,"
                + "anthropic/claude-haiku-4.5,anthropic/claude-opus-4.6,"
                + "openai/gpt-5,openai/gpt-5-mini,openai/gpt-5-nano,"
                + "openai/gpt-5.2-chat,openai/gpt-5.2,meta-llama/llama-4-maverick,"
                + "google/gemini-2.5-pro,google/gemini-3-flash-preview");
        assertHistoricalDefaultIsDiscarded(
            "ai_gemini_model_list",
            "ai_gemini_models",
            "gemini-2.5-flash, gemini-3-pro-preview, gemini-2.5-pro, gemini-3-flash-preview");
        assertHistoricalDefaultIsDiscarded(
            "ai_ollama_model_allowlist",
            "ai_ollama_models",
            "");
    }

    @Test
    public void copiesDifferentLegacyOverrideAndRemovesOldKey() {
        ResourceController resources = mock(ResourceController.class);
        Properties userProperties = new Properties();
        userProperties.setProperty("ai_openrouter_model_allowlist", "private/model, openai/gpt-next");
        when(resources.getUnsecuredProperties()).thenReturn(userProperties);
        when(resources.isPropertySetByUser("ai_openrouter_model_allowlist")).thenReturn(true);
        when(resources.getProperty("ai_openrouter_model_allowlist"))
            .thenReturn("private/model, openai/gpt-next");

        AIModelListPreferenceMigration.migrate(resources);

        verify(resources).setProperty("ai_openrouter_models", "private/model, openai/gpt-next");
        assertThat(userProperties).doesNotContainKey("ai_openrouter_model_allowlist");
    }

    @Test
    public void preservesExistingNewValueAndRemovesOldKey() {
        ResourceController resources = mock(ResourceController.class);
        Properties userProperties = new Properties();
        userProperties.setProperty("ai_gemini_model_list", "old-model");
        userProperties.setProperty("ai_gemini_models", "new-model");
        when(resources.getUnsecuredProperties()).thenReturn(userProperties);
        when(resources.isPropertySetByUser("ai_gemini_model_list")).thenReturn(true);
        when(resources.isPropertySetByUser("ai_gemini_models")).thenReturn(true);
        when(resources.getProperty("ai_gemini_model_list")).thenReturn("old-model");

        AIModelListPreferenceMigration.migrate(resources);

        verify(resources, never()).setProperty(eq("ai_gemini_models"), anyString());
        assertThat(userProperties.getProperty("ai_gemini_models")).isEqualTo("new-model");
        assertThat(userProperties).doesNotContainKey("ai_gemini_model_list");
    }

    private void assertHistoricalDefaultIsDiscarded(String oldProperty,
                                                      String newProperty,
                                                      String oldValue) {
        ResourceController resources = mock(ResourceController.class);
        Properties userProperties = new Properties();
        userProperties.setProperty(oldProperty, oldValue);
        when(resources.getUnsecuredProperties()).thenReturn(userProperties);
        when(resources.isPropertySetByUser(oldProperty)).thenReturn(true);
        when(resources.getProperty(oldProperty)).thenReturn(oldValue);

        AIModelListPreferenceMigration.migrate(resources);

        verify(resources, never()).setProperty(eq(newProperty), anyString());
        assertThat(userProperties).doesNotContainKey(oldProperty);
    }
}

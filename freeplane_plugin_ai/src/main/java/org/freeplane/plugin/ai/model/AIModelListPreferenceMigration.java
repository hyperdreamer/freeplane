package org.freeplane.plugin.ai.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.core.resources.ResourceController;

public final class AIModelListPreferenceMigration {
    private static final Set<Set<String>> OPENROUTER_HISTORICAL_DEFAULTS = historicalDefaults(
        "deepseek/deepseek-r1-0528:free,meta-llama/llama-3.3-70b-instruct:free,"
            + "z-ai/glm-4.5-air:free,qwen/qwen-2.5-72b-instruct:free,"
            + "deepseek/deepseek-r1-0528,anthropic/claude-3.5-sonnet,"
            + "anthropic/claude-3.5-haiku,openai/gpt-5,openai/gpt-5-mini,"
            + "openai/gpt-5-nano,openai/gpt-4o,openai/gpt-4o-mini,"
            + "meta-llama/llama-3.3-70b-instruct,google/gemini-2.5-pro,"
            + "google/gemini-3-flash-preview",
        "deepseek/deepseek-r1-0528:free,meta-llama/llama-3.3-70b-instruct:free,"
            + "z-ai/glm-4.5-air:free,qwen/qwen3-next-80b-a3b-instruct:free,"
            + "deepseek/deepseek-v3.2,anthropic/claude-sonnet-4.5,"
            + "anthropic/claude-haiku-4.5,anthropic/claude-opus-4.6,"
            + "openai/gpt-5,openai/gpt-5-mini,openai/gpt-5-nano,"
            + "openai/gpt-5.2-chat,openai/gpt-5.2,meta-llama/llama-4-maverick,"
            + "google/gemini-2.5-pro,google/gemini-3-flash-preview",
        "deepseek/deepseek-v3.2,anthropic/claude-sonnet-4.6,"
            + "anthropic/claude-haiku-4.5,anthropic/claude-opus-4.6,"
            + "openai/gpt-5,openai/gpt-5-mini,openai/gpt-5-nano,"
            + "openai/gpt-5.2-chat,openai/gpt-5.2,meta-llama/llama-4-maverick,"
            + "google/gemini-2.5-pro,google/gemini-3-flash-preview");
    private static final Set<Set<String>> GEMINI_HISTORICAL_DEFAULTS = historicalDefaults(
        "gemini-3-pro-preview,gemini-3-flash-preview,gemini-2.5-pro,gemini-2.5-flash");
    private static final Set<Set<String>> OLLAMA_HISTORICAL_DEFAULTS = historicalDefaults("");

    private AIModelListPreferenceMigration() {
    }

    public static void migrate(ResourceController resourceController) {
        migrateProperty(
            resourceController,
            "ai_openrouter_model_allowlist",
            "ai_openrouter_models",
            OPENROUTER_HISTORICAL_DEFAULTS);
        migrateProperty(
            resourceController,
            "ai_gemini_model_list",
            "ai_gemini_models",
            GEMINI_HISTORICAL_DEFAULTS);
        migrateProperty(
            resourceController,
            "ai_ollama_model_allowlist",
            "ai_ollama_models",
            OLLAMA_HISTORICAL_DEFAULTS);
    }

    private static void migrateProperty(ResourceController resourceController,
                                        String oldProperty,
                                        String newProperty,
                                        Set<Set<String>> historicalDefaults) {
        if (!resourceController.isPropertySetByUser(oldProperty)) {
            return;
        }
        String oldValue = resourceController.getProperty(oldProperty);
        if (!resourceController.isPropertySetByUser(newProperty)
            && !historicalDefaults.contains(normalizedModelSet(oldValue))) {
            resourceController.setProperty(newProperty, oldValue == null ? "" : oldValue);
        }
        resourceController.getUnsecuredProperties().remove(oldProperty);
    }

    private static Set<Set<String>> historicalDefaults(String... defaults) {
        Set<Set<String>> normalizedDefaults = new LinkedHashSet<>();
        for (String value : defaults) {
            normalizedDefaults.add(normalizedModelSet(value));
        }
        return Collections.unmodifiableSet(normalizedDefaults);
    }

    private static Set<String> normalizedModelSet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> models = new LinkedHashSet<>();
        for (String model : value.split("[,\\r\\n]+")) {
            String normalizedModel = model.trim();
            if (!normalizedModel.isEmpty()) {
                models.add(normalizedModel);
            }
        }
        return Collections.unmodifiableSet(models);
    }
}

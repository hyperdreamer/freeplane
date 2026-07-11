package org.freeplane.plugin.ai.model;

public interface OpenAIModelMetadataInterpreter {
    AIModelCapabilities interpret(OpenAIModelItem modelItem);
}

package org.freeplane.plugin.ai.model;

import java.util.List;

public class OpenRouterModelMetadataInterpreter implements OpenAIModelMetadataInterpreter {
    @Override
    public AIModelCapabilities interpret(OpenAIModelItem modelItem) {
        if (modelItem == null) {
            return AIModelCapabilities.UNKNOWN;
        }
        return new AIModelCapabilities(
            supportFor(modelItem.getOutputModalities(), "text"),
            supportFor(modelItem.getSupportedParameters(), "tools"));
    }

    private CapabilitySupport supportFor(List<String> values, String requiredValue) {
        if (values == null) {
            return CapabilitySupport.UNKNOWN;
        }
        for (String value : values) {
            if (requiredValue.equalsIgnoreCase(value)) {
                return CapabilitySupport.SUPPORTED;
            }
        }
        return CapabilitySupport.UNSUPPORTED;
    }
}

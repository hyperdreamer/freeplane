package org.freeplane.plugin.ai.model;

public class RequestyModelMetadataInterpreter implements OpenAIModelMetadataInterpreter {
    @Override
    public AIModelCapabilities interpret(OpenAIModelItem modelItem) {
        if (modelItem == null) {
            return AIModelCapabilities.UNKNOWN;
        }
        CapabilitySupport textOutput = modelItem.getApi() == null
            ? CapabilitySupport.UNKNOWN
            : support(modelItem.getApi().equalsIgnoreCase("chat"));
        CapabilitySupport toolCalling = modelItem.getSupportsToolCalling() == null
            ? CapabilitySupport.UNKNOWN
            : support(modelItem.getSupportsToolCalling().booleanValue());
        return new AIModelCapabilities(textOutput, toolCalling);
    }

    private CapabilitySupport support(boolean supported) {
        return supported ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED;
    }
}

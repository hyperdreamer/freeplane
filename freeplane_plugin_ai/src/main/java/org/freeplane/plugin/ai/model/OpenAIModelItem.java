package org.freeplane.plugin.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAIModelItem {
    @JsonProperty("id")
    private String id;
    @JsonProperty("supported_parameters")
    private List<String> supportedParameters;
    @JsonProperty("architecture")
    private Architecture architecture;
    @JsonProperty("api")
    private String api;
    @JsonProperty("supports_tool_calling")
    private Boolean supportsToolCalling;
    @JsonProperty("pricing")
    private Pricing pricing;

    public String getId() {
        return id;
    }

    public List<String> getSupportedParameters() {
        return supportedParameters;
    }

    public List<String> getOutputModalities() {
        return architecture == null ? null : architecture.outputModalities;
    }

    public String getApi() {
        return api;
    }

    public Boolean getSupportsToolCalling() {
        return supportsToolCalling;
    }

    public boolean isFreeModel() {
        return pricing != null
            && isZero(pricing.prompt)
            && isZero(pricing.completion);
    }

    private boolean isZero(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return Double.parseDouble(value) == 0.0;
        }
        catch (NumberFormatException exception) {
            return false;
        }
    }

    static OpenAIModelItem create(String id,
                                  List<String> supportedParameters,
                                  List<String> outputModalities,
                                  String api,
                                  Boolean supportsToolCalling) {
        OpenAIModelItem item = new OpenAIModelItem();
        item.id = id;
        item.supportedParameters = supportedParameters;
        if (outputModalities != null) {
            item.architecture = new Architecture();
            item.architecture.outputModalities = outputModalities;
        }
        item.api = api;
        item.supportsToolCalling = supportsToolCalling;
        return item;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Architecture {
        @JsonProperty("output_modalities")
        private List<String> outputModalities = Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Pricing {
        @JsonProperty("prompt")
        private String prompt;
        @JsonProperty("completion")
        private String completion;
    }
}

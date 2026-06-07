package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdateApplyRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Preview ID returned by previewFormulaUpdates.")
    private final String previewId;

    @JsonCreator
    public FormulaUpdateApplyRequest(@JsonProperty(value = "mapIdentifier", required = true) String mapIdentifier,
                                     @JsonProperty(value = "previewId", required = true) String previewId) {
        this.mapIdentifier = mapIdentifier;
        this.previewId = previewId;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getPreviewId() {
        return previewId;
    }
}

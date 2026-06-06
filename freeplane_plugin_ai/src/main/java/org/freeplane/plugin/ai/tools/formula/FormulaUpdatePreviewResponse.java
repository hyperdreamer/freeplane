package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdatePreviewResponse {
    private final String mapIdentifier;
    private final String previewId;
    private final List<FormulaUpdatePreviewResultItem> items;

    @JsonCreator
    public FormulaUpdatePreviewResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                        @JsonProperty("previewId") String previewId,
                                        @JsonProperty("items") List<FormulaUpdatePreviewResultItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.previewId = previewId;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getPreviewId() {
        return previewId;
    }

    public List<FormulaUpdatePreviewResultItem> getItems() {
        return items;
    }
}

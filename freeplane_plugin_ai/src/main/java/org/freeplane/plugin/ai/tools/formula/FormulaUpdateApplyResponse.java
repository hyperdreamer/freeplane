package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdateApplyResponse {
    private final String mapIdentifier;
    private final List<FormulaUpdateApplyResultItem> items;

    @JsonCreator
    public FormulaUpdateApplyResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                      @JsonProperty("items") List<FormulaUpdateApplyResultItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<FormulaUpdateApplyResultItem> getItems() {
        return items;
    }
}

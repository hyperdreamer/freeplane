package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdatePreviewRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Short summary for confirmations.")
    private final String userSummary;
    @Description("Ordered formula update instructions (non-empty).")
    private final List<FormulaUpdateItem> items;

    @JsonCreator
    public FormulaUpdatePreviewRequest(@JsonProperty(value = "mapIdentifier", required = true) String mapIdentifier,
                                       @JsonProperty("userSummary") String userSummary,
                                       @JsonProperty(value = "items", required = true) List<FormulaUpdateItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public List<FormulaUpdateItem> getItems() {
        return items;
    }
}

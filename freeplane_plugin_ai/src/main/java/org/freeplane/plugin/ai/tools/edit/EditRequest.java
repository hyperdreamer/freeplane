package org.freeplane.plugin.ai.tools.edit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Short summary for confirmations.")
    private final String userSummary;
    @Description("Request-level compatibility handling. Default: SKIP_INCOMPATIBLE_FIELDS.")
    private final EditCompatibilityPolicy compatibilityPolicy;
    @Description("Edit instructions (non-empty). Each instruction targets nodeIdentifiers.")
    private final List<NodeContentEditItem> items;

    @JsonCreator
    public EditRequest(@JsonProperty(value = "mapIdentifier", required = true) String mapIdentifier,
                       @JsonProperty("userSummary") String userSummary,
                       @JsonProperty("compatibilityPolicy") EditCompatibilityPolicy compatibilityPolicy,
                       @JsonProperty(value = "items", required = true) List<NodeContentEditItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.compatibilityPolicy = compatibilityPolicy;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public EditCompatibilityPolicy getCompatibilityPolicy() {
        return compatibilityPolicy;
    }

    public EditCompatibilityPolicy getResolvedCompatibilityPolicy() {
        if (compatibilityPolicy == null) {
            return EditCompatibilityPolicy.SKIP_INCOMPATIBLE_FIELDS;
        }
        return compatibilityPolicy;
    }

    public List<NodeContentEditItem> getItems() {
        return items;
    }
}

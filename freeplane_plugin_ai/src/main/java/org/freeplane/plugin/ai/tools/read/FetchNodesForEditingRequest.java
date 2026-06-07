package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.EditableContentField;

public class FetchNodesForEditingRequest {
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @JsonProperty(required = false)
    @Description("Node IDs to fetch (default: root).")
    private final List<String> nodeIdentifiers;
    @JsonProperty(required = true)
    @Description("Editable content fields to include.")
    private final List<EditableContentField> editableContentFields;

    @JsonCreator
    public FetchNodesForEditingRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                       @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers,
                                       @JsonProperty(value = "editableContentFields", required = true) List<EditableContentField> editableContentFields) {
        this.mapIdentifier = mapIdentifier;
        this.nodeIdentifiers = nodeIdentifiers;
        this.editableContentFields = editableContentFields;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }

    public List<EditableContentField> getEditableContentFields() {
        return editableContentFields;
    }
}

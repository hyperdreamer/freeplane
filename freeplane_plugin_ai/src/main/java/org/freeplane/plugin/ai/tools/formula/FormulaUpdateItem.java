package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.edit.EditOperation;
import org.freeplane.plugin.ai.tools.edit.EditedElement;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdateItem {
    @Description("Target node IDs to update (non-empty).")
    private final List<String> nodeIdentifiers;
    @Description("Edited element: TEXT, DETAILS, NOTE, or ATTRIBUTES.")
    private final EditedElement editedElement;
    @JsonProperty(required = false)
    @Description("From fetchNodesForEditing. Required for TEXT, DETAILS, and NOTE.")
    private final ContentType originalContentType;
    @JsonProperty(required = false)
    @Description("From fetchNodesForEditing. Required for TEXT, DETAILS, and NOTE. Optional for ATTRIBUTES REPLACE.")
    private final Boolean originalIsFormula;
    @Description("Target formula state. true = formula, false = non-formula.")
    private final Boolean targetIsFormula;
    @JsonProperty(required = false)
    @Description("Replacement value. Required for all updates in this flow.")
    private final String value;
    @JsonProperty(required = false)
    @Description("List index for ATTRIBUTES. For ADD, inserts at this index when provided. For REPLACE, index is preferred over targetKey.")
    private final Integer index;
    @JsonProperty(required = false)
    @Description("Optional operation. TEXT, DETAILS, and NOTE allow REPLACE only. ATTRIBUTES allow ADD or REPLACE.")
    private final EditOperation operation;
    @JsonProperty(required = false)
    @Description("For ATTRIBUTES ADD, attribute name. For ATTRIBUTES REPLACE, fallback selector when index is absent.")
    private final String targetKey;

    @JsonCreator
    public FormulaUpdateItem(@JsonProperty(value = "nodeIdentifiers", required = true) List<String> nodeIdentifiers,
                             @JsonProperty(value = "editedElement", required = true) EditedElement editedElement,
                             @JsonProperty("originalContentType") ContentType originalContentType,
                             @JsonProperty("originalIsFormula") Boolean originalIsFormula,
                             @JsonProperty(value = "targetIsFormula", required = true) Boolean targetIsFormula,
                             @JsonProperty("value") String value,
                             @JsonProperty("index") Integer index,
                             @JsonProperty("operation") EditOperation operation,
                             @JsonProperty("targetKey") String targetKey) {
        this.nodeIdentifiers = nodeIdentifiers;
        this.editedElement = editedElement;
        this.originalContentType = originalContentType;
        this.originalIsFormula = originalIsFormula;
        this.targetIsFormula = targetIsFormula;
        this.value = value;
        this.index = index;
        this.operation = operation == null ? EditOperation.REPLACE : operation;
        this.targetKey = targetKey;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }

    public EditedElement getEditedElement() {
        return editedElement;
    }

    public ContentType getOriginalContentType() {
        return originalContentType;
    }

    public Boolean getOriginalIsFormula() {
        return originalIsFormula;
    }

    public Boolean getTargetIsFormula() {
        return targetIsFormula;
    }

    public String getValue() {
        return value;
    }

    public Integer getIndex() {
        return index;
    }

    public EditOperation getOperation() {
        return operation;
    }

    public String getTargetKey() {
        return targetKey;
    }
}

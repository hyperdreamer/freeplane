package org.freeplane.plugin.ai.tools.edit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditResultItem {
    @Description("Zero-based index of the source instruction in request.items.")
    private final Integer itemIndex;
    @Description("Target node ID from the instruction's nodeIdentifiers.")
    private final String nodeIdentifier;
    @Description("Edited element from the source instruction.")
    private final EditedElement editedElement;
    @Description("APPLIED when written, SKIPPED when incompatible under skip policy, REJECTED when strict validation rejects before writes, FAILED on write/runtime failure.")
    private final EditTargetStatus status;
    @Description("Field-level incompatibility reasons when status is SKIPPED or REJECTED.")
    private final List<String> incompatibleFieldReasons;
    @Description("Error details when status is FAILED.")
    private final String errorMessage;
    @Description("Updated node content for APPLIED results. Null for SKIPPED, REJECTED, and FAILED results.")
    private final NodeContentItem content;

    @JsonCreator
    public EditResultItem(@JsonProperty("itemIndex") Integer itemIndex,
                          @JsonProperty("nodeIdentifier") String nodeIdentifier,
                          @JsonProperty("editedElement") EditedElement editedElement,
                          @JsonProperty("status") EditTargetStatus status,
                          @JsonProperty("incompatibleFieldReasons") List<String> incompatibleFieldReasons,
                          @JsonProperty("errorMessage") String errorMessage,
                          @JsonProperty("content") NodeContentItem content) {
        this.itemIndex = itemIndex;
        this.nodeIdentifier = nodeIdentifier;
        this.editedElement = editedElement;
        this.status = status;
        this.incompatibleFieldReasons = incompatibleFieldReasons;
        this.errorMessage = errorMessage;
        this.content = content;
    }

    public Integer getItemIndex() {
        return itemIndex;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public EditedElement getEditedElement() {
        return editedElement;
    }

    public EditTargetStatus getStatus() {
        return status;
    }

    public List<String> getIncompatibleFieldReasons() {
        return incompatibleFieldReasons;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public NodeContentItem getContent() {
        return content;
    }
}

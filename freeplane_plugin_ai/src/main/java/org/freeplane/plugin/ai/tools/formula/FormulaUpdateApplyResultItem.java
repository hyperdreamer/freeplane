package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.edit.EditedElement;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdateApplyResultItem {
    private final Integer itemIndex;
    private final String nodeIdentifier;
    private final EditedElement editedElement;
    private final FormulaUpdateApplyStatus status;
    private final String errorMessage;
    private final NodeContentItem updatedContent;

    @JsonCreator
    public FormulaUpdateApplyResultItem(@JsonProperty("itemIndex") Integer itemIndex,
                                        @JsonProperty("nodeIdentifier") String nodeIdentifier,
                                        @JsonProperty("editedElement") EditedElement editedElement,
                                        @JsonProperty("status") FormulaUpdateApplyStatus status,
                                        @JsonProperty("errorMessage") String errorMessage,
                                        @JsonProperty("updatedContent") NodeContentItem updatedContent) {
        this.itemIndex = itemIndex;
        this.nodeIdentifier = nodeIdentifier;
        this.editedElement = editedElement;
        this.status = status;
        this.errorMessage = errorMessage;
        this.updatedContent = updatedContent;
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

    public FormulaUpdateApplyStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public NodeContentItem getUpdatedContent() {
        return updatedContent;
    }
}

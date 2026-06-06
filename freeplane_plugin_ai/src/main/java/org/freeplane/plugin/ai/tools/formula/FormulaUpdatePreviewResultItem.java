package org.freeplane.plugin.ai.tools.formula;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.edit.EditedElement;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulaUpdatePreviewResultItem {
    private final Integer itemIndex;
    private final String nodeIdentifier;
    private final EditedElement editedElement;
    private final FormulaUpdatePreviewStatus status;
    private final String candidateValue;
    private final String evaluationResult;
    private final List<String> compilerDiagnostics;
    private final String errorMessage;
    private final Integer lineNumber;

    @JsonCreator
    public FormulaUpdatePreviewResultItem(@JsonProperty("itemIndex") Integer itemIndex,
                                          @JsonProperty("nodeIdentifier") String nodeIdentifier,
                                          @JsonProperty("editedElement") EditedElement editedElement,
                                          @JsonProperty("status") FormulaUpdatePreviewStatus status,
                                          @JsonProperty("candidateValue") String candidateValue,
                                          @JsonProperty("evaluationResult") String evaluationResult,
                                          @JsonProperty("compilerDiagnostics") List<String> compilerDiagnostics,
                                          @JsonProperty("errorMessage") String errorMessage,
                                          @JsonProperty("lineNumber") Integer lineNumber) {
        this.itemIndex = itemIndex;
        this.nodeIdentifier = nodeIdentifier;
        this.editedElement = editedElement;
        this.status = status;
        this.candidateValue = candidateValue;
        this.evaluationResult = evaluationResult;
        this.compilerDiagnostics = compilerDiagnostics;
        this.errorMessage = errorMessage;
        this.lineNumber = lineNumber;
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

    public FormulaUpdatePreviewStatus getStatus() {
        return status;
    }

    public String getCandidateValue() {
        return candidateValue;
    }

    public String getEvaluationResult() {
        return evaluationResult;
    }

    public List<String> getCompilerDiagnostics() {
        return compilerDiagnostics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }
}

package org.freeplane.features.ai.code;

import org.freeplane.features.map.NodeModel;

public class EvaluateFormulaRequest {
    private NodeModel targetNode;
    private String formulaText;

    public EvaluateFormulaRequest() {
    }

    public EvaluateFormulaRequest(NodeModel targetNode, String formulaText) {
        this.targetNode = targetNode;
        this.formulaText = formulaText;
    }

    public NodeModel getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(NodeModel targetNode) {
        this.targetNode = targetNode;
    }

    public String getFormulaText() {
        return formulaText;
    }

    public void setFormulaText(String formulaText) {
        this.formulaText = formulaText;
    }
}

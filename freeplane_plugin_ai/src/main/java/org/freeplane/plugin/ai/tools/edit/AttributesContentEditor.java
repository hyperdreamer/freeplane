package org.freeplane.plugin.ai.tools.edit;

import java.util.List;
import java.util.Objects;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;

public class AttributesContentEditor {
    private final MAttributeController attributeController;
    private final TextController textController;

    public AttributesContentEditor(MAttributeController attributeController) {
        this(attributeController, safeTextController());
    }

    public AttributesContentEditor(MAttributeController attributeController, TextController textController) {
        this.attributeController = Objects.requireNonNull(attributeController, "attributeController");
        this.textController = textController;
    }

    public void setInitialContent(NodeModel nodeModel, AttributesContent attributesContent) {
        if (nodeModel == null || attributesContent == null) {
            return;
        }
        List<AttributeEntry> attributes = attributesContent.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        for (AttributeEntry attributeEntry : attributes) {
            if (attributeEntry == null || attributeEntry.getName() == null) {
                continue;
            }
            String value = attributeEntry.getValue();
            ensureCreatePathValueIsNotFormula(value);
            Attribute attribute = new Attribute(attributeEntry.getName(), value == null ? "" : value);
            attributeTableModel.silentlyAddRowNoUndo(nodeModel, attribute);
        }
    }

    public String prepareFormulaCandidate(NodeModel nodeModel, EditOperation operation, String targetKey,
                                          Integer index, String value, Boolean originalIsFormula,
                                          boolean targetIsFormula) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        NodeAttributeTableModel model = NodeAttributeTableModel.getModel(nodeModel);
        String candidateValue = value == null ? "" : value;
        switch (resolvedOperation) {
            case ADD:
                requireAttributeName(targetKey);
                validateTargetFormulaState(candidateValue, targetIsFormula);
                return candidateValue;
            case REPLACE:
                int targetIndex = findAttributeIndex(model, targetKey, index);
                if (targetIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for replace.");
                }
                if (originalIsFormula != null) {
                    Object currentValue = model.getValue(targetIndex);
                    if (isFormula(currentValue) != originalIsFormula.booleanValue()) {
                        throw new IllegalArgumentException("Formula state has changed; read editable content again.");
                    }
                }
                validateTargetFormulaState(candidateValue, targetIsFormula);
                return candidateValue;
            default:
                throw new IllegalArgumentException(
                    "Formula updates support only ADD and REPLACE for attributes.");
        }
    }

    public void applyFormulaValue(NodeModel nodeModel, EditOperation operation, String targetKey,
                                  Integer index, String candidateValue) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        NodeAttributeTableModel model = NodeAttributeTableModel.getModel(nodeModel);
        switch (resolvedOperation) {
            case ADD:
                String name = requireAttributeName(targetKey);
                Attribute attribute = new Attribute(name, candidateValue == null ? "" : candidateValue);
                if (index == null) {
                    attributeController.addAttribute(nodeModel, attribute);
                } else {
                    int boundedIndex = Math.max(0, Math.min(index, model.getRowCount()));
                    attributeController.insertAttribute(nodeModel, boundedIndex, attribute);
                }
                return;
            case REPLACE:
                int targetIndex = findAttributeIndex(model, targetKey, index);
                if (targetIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for replace.");
                }
                String attributeName = targetKey;
                if (attributeName == null) {
                    Attribute existing = model.getAttribute(targetIndex);
                    attributeName = existing == null ? null : existing.getName();
                }
                attributeController.setAttribute(nodeModel, targetIndex,
                    new Attribute(attributeName, candidateValue == null ? "" : candidateValue));
                return;
            default:
                throw new IllegalArgumentException(
                    "Formula updates support only ADD and REPLACE for attributes.");
        }
    }

    public void editExistingAttributesContent(NodeModel nodeModel, EditOperation operation, String targetKey,
                                              Integer index, String value) {
        processExistingAttributesContent(nodeModel, operation, targetKey, index, value, false);
    }

    public void validateExistingAttributesContent(NodeModel nodeModel, EditOperation operation, String targetKey,
                                                  Integer index, String value) {
        processExistingAttributesContent(nodeModel, operation, targetKey, index, value, true);
    }

    private void processExistingAttributesContent(NodeModel nodeModel, EditOperation operation, String targetKey,
                                                  Integer index, String value, boolean dryRun) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        NodeAttributeTableModel model = NodeAttributeTableModel.getModel(nodeModel);
        switch (resolvedOperation) {
            case ADD:
                String name = requireAttributeName(targetKey);
                String addValue = value == null ? "" : value;
                ensureFormulaIsNotUsed(null, addValue);
                Attribute attribute = new Attribute(name, addValue);
                if (!dryRun) {
                    if (index == null) {
                        attributeController.addAttribute(nodeModel, attribute);
                    } else {
                        int boundedIndex = Math.max(0, Math.min(index, model.getRowCount()));
                        attributeController.insertAttribute(nodeModel, boundedIndex, attribute);
                    }
                }
                break;
            case REPLACE:
                int targetIndex = findAttributeIndex(model, targetKey, index);
                if (targetIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for replace.");
                }
                Attribute existing = model.getAttribute(targetIndex);
                ensureFormulaIsNotUsed(existing == null ? null : existing.getValue(), value == null ? "" : value);
                String attributeName = targetKey;
                if (attributeName == null) {
                    attributeName = existing == null ? null : existing.getName();
                }
                if (!dryRun) {
                    attributeController.setAttribute(nodeModel, targetIndex,
                        new Attribute(attributeName, value == null ? "" : value));
                }
                break;
            case DELETE:
                int deleteIndex = findAttributeIndex(model, targetKey, index);
                if (deleteIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for delete.");
                }
                Attribute toDelete = model.getAttribute(deleteIndex);
                ensureFormulaIsNotUsed(toDelete == null ? null : toDelete.getValue(), null);
                if (!dryRun) {
                    attributeController.performRemoveAttribute(nodeModel, deleteIndex);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported attribute operation: " + resolvedOperation);
        }
    }

    private void ensureCreatePathValueIsNotFormula(String value) {
        if (isFormula(value)) {
            throw new IllegalArgumentException(
                "Formula values are not allowed in createNodes or createSummary; create the node first, then use previewFormulaUpdates and applyFormulaUpdates.");
        }
    }

    private void validateTargetFormulaState(String candidateValue, boolean targetIsFormula) {
        boolean candidateIsFormula = isFormula(candidateValue);
        if (candidateIsFormula != targetIsFormula) {
            throw new IllegalArgumentException(targetIsFormula
                ? "targetIsFormula=true requires a formula value."
                : "targetIsFormula=false requires a non-formula value.");
        }
    }

    private void ensureFormulaIsNotUsed(Object currentValue, String newValue) {
        if (isFormula(currentValue)) {
            throw new IllegalArgumentException(
                "Cannot edit formula-backed attributes with edit(...); use previewFormulaUpdates and applyFormulaUpdates.");
        }
        if (isFormula(newValue)) {
            throw new IllegalArgumentException(
                "Formula attribute edits are not allowed in edit(...); use previewFormulaUpdates and applyFormulaUpdates.");
        }
    }

    private static TextController safeTextController() {
        try {
            return TextController.getController();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isFormula(Object value) {
        return textController != null && textController.isFormula(value);
    }

    private int findAttributeIndex(NodeAttributeTableModel model, String targetKey, Integer index) {
        if (index != null && index >= 0 && index < model.getRowCount()) {
            return index;
        }
        if (targetKey != null) {
            for (int row = 0; row < model.getRowCount(); row++) {
                Attribute attribute = model.getAttribute(row);
                if (attribute != null && targetKey.equals(attribute.getName())) {
                    return row;
                }
            }
        }
        return -1;
    }

    private String requireAttributeName(String targetKey) {
        if (targetKey == null || targetKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing attribute name.");
        }
        return targetKey;
    }
}

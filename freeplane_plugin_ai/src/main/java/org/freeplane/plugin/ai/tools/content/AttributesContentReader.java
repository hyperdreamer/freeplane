package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public class AttributesContentReader {
    private final TextController textController;

    public AttributesContentReader(AttributeController attributeController, TextController textController) {
        Objects.requireNonNull(attributeController, "attributeController");
        this.textController = Objects.requireNonNull(textController, "textController");
    }

    public AttributesContent readAttributesContent(NodeModel nodeModel, NodeContentPreset preset) {
        if (nodeModel == null || preset == NodeContentPreset.BRIEF) {
            return null;
        }
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        int rowCount = attributeTableModel.getRowCount();
        if (rowCount == 0) {
            return null;
        }
        List<AttributeEntry> attributes = new ArrayList<>(rowCount);
        for (Attribute attribute : attributeTableModel.getAttributes()) {
            Object transformedValue = textController.getTransformedObjectNoFormattingNoThrow(
                nodeModel, attributeTableModel, attribute.getValue());
            String value = transformedValue == null ? null : String.valueOf(transformedValue);
            attributes.add(new AttributeEntry(attribute.getName(), value));
        }
        return new AttributesContent(attributes);
    }

    public AttributesContent readAttributesContent(NodeModel nodeModel, AttributesContentRequest request) {
        if (nodeModel == null || request == null || !request.includesAttributes()) {
            return null;
        }
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        int rowCount = attributeTableModel.getRowCount();
        if (rowCount == 0) {
            return null;
        }
        List<AttributeEntry> attributes = new ArrayList<>(rowCount);
        for (Attribute attribute : attributeTableModel.getAttributes()) {
            Object transformedValue = textController.getTransformedObjectNoFormattingNoThrow(
                nodeModel, attributeTableModel, attribute.getValue());
            String value = transformedValue == null ? null : String.valueOf(transformedValue);
            attributes.add(new AttributeEntry(attribute.getName(), value));
        }
        return new AttributesContent(attributes);
    }

    public boolean matches(NodeModel nodeModel, AttributesContentRequest request, NodeContentValueMatcher valueMatcher) {
        if (nodeModel == null || request == null || !request.includesAttributes() || valueMatcher == null) {
            return false;
        }
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        for (Attribute attribute : attributeTableModel.getAttributes()) {
            if (valueMatcher.matchesValue(attribute.getName())) {
                return true;
            }
            Object transformedValue = textController.getTransformedObjectNoFormattingNoThrow(
                nodeModel, attributeTableModel, attribute.getValue());
            String value = transformedValue == null ? null : String.valueOf(transformedValue);
            if (valueMatcher.matchesValue(value)) {
                return true;
            }
        }
        return false;
    }
}

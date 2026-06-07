package org.freeplane.plugin.ai.tools.content;

import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AttributesContentReaderTest {
    @Test
    public void readAttributesContent_returnsTransformedValuesForFullPreset() {
        TextController textController = mock(TextController.class);
        AttributeController attributeController = mock(AttributeController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeAttributeTableModel tableModel = new NodeAttributeTableModel();
        tableModel.getAttributes().add(new Attribute("Priority", "High"));
        tableModel.getAttributes().add(new Attribute("Owner", "Alice"));
        when(nodeModel.getExtension(NodeAttributeTableModel.class)).thenReturn(tableModel);
        when(textController.getTransformedObjectNoFormattingNoThrow(nodeModel, tableModel, "High"))
            .thenReturn("High");
        when(textController.getTransformedObjectNoFormattingNoThrow(nodeModel, tableModel, "Alice"))
            .thenReturn("Alice");
        AttributesContentReader uut = new AttributesContentReader(attributeController, textController);

        AttributesContent content = uut.readAttributesContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getAttributes())
            .extracting(AttributeEntry::getName, AttributeEntry::getValue)
            .containsExactly(tuple("Priority", "High"), tuple("Owner", "Alice"));
    }

    @Test
    public void readAttributesContent_keepsNullValues() {
        TextController textController = mock(TextController.class);
        AttributeController attributeController = mock(AttributeController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeAttributeTableModel tableModel = new NodeAttributeTableModel();
        tableModel.getAttributes().add(new Attribute("Missing", "Original"));
        when(nodeModel.getExtension(NodeAttributeTableModel.class)).thenReturn(tableModel);
        when(textController.getTransformedObjectNoFormattingNoThrow(nodeModel, tableModel, "Original"))
            .thenReturn(null);
        AttributesContentReader uut = new AttributesContentReader(attributeController, textController);

        AttributesContent content = uut.readAttributesContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getAttributes())
            .extracting(AttributeEntry::getName, AttributeEntry::getValue)
            .containsExactly(tuple("Missing", null));
    }
}

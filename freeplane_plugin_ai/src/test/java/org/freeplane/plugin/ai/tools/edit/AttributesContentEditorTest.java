package org.freeplane.plugin.ai.tools.edit;

import java.util.Collections;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AttributesContentEditorTest {
    @Test
    public void setInitialContent_addsAttributes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        AttributesContent attributesContent = new AttributesContent(
            Collections.singletonList(new AttributeEntry("key", "value")));
        AttributesContentEditor uut = new AttributesContentEditor(mock(MAttributeController.class));

        uut.setInitialContent(nodeModel, attributesContent);

        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        assertThat(attributeTableModel.getRowCount()).isEqualTo(1);
        assertThat(attributeTableModel.getName(0)).isEqualTo("key");
        assertThat(attributeTableModel.getValue(0)).isEqualTo("value");
    }

    @Test
    public void editExistingAttributesContent_addsAttributesThroughController() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        MAttributeController attributeController = mock(MAttributeController.class);
        AttributesContentEditor uut = new AttributesContentEditor(attributeController);

        uut.editExistingAttributesContent(nodeModel, EditOperation.ADD, "key", null, "value");

        ArgumentCaptor<Attribute> attributeCaptor = ArgumentCaptor.forClass(Attribute.class);
        verify(attributeController).addAttribute(eq(nodeModel), attributeCaptor.capture());
        Attribute addedAttribute = attributeCaptor.getValue();
        assertThat(addedAttribute.getName()).isEqualTo("key");
        assertThat(addedAttribute.getValue()).isEqualTo("value");
    }

    @Test
    public void editExistingAttributesContent_replacesAttributesByKey() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        attributeTableModel.silentlyAddRowNoUndo(nodeModel, new Attribute("key", "value"));
        MAttributeController attributeController = mock(MAttributeController.class);
        AttributesContentEditor uut = new AttributesContentEditor(attributeController);

        uut.editExistingAttributesContent(nodeModel, EditOperation.REPLACE, "key", null, "updated");

        ArgumentCaptor<Attribute> attributeCaptor = ArgumentCaptor.forClass(Attribute.class);
        verify(attributeController).setAttribute(eq(nodeModel), eq(0), attributeCaptor.capture());
        Attribute updatedAttribute = attributeCaptor.getValue();
        assertThat(updatedAttribute.getName()).isEqualTo("key");
        assertThat(updatedAttribute.getValue()).isEqualTo("updated");
    }

    @Test
    public void editExistingAttributesContent_deletesAttributesByIndex() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        attributeTableModel.silentlyAddRowNoUndo(nodeModel, new Attribute("key", "value"));
        MAttributeController attributeController = mock(MAttributeController.class);
        AttributesContentEditor uut = new AttributesContentEditor(attributeController);

        uut.editExistingAttributesContent(nodeModel, EditOperation.DELETE, null, 0, null);

        verify(attributeController).performRemoveAttribute(nodeModel, 0);
    }
}

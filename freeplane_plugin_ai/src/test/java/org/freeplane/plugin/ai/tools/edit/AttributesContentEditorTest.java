package org.freeplane.plugin.ai.tools.edit;

import java.util.Collections;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AttributesContentEditorTest {
    @Test
    public void setInitialContent_rejectsFormulaValue() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(true);
        AttributesContentEditor uut = new AttributesContentEditor(mock(MAttributeController.class), textController);

        assertThatThrownBy(() -> uut.setInitialContent(nodeModel,
            new AttributesContent(Collections.singletonList(new AttributeEntry("key", "=1+1")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Formula values are not allowed in createNodes or createSummary");
    }

    @Test
    public void editExistingAttributesContent_rejectsFormulaValue() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return "=1+1".equals(value);
        });
        AttributesContentEditor uut = new AttributesContentEditor(mock(MAttributeController.class), textController);

        assertThatThrownBy(() -> uut.validateExistingAttributesContent(
            nodeModel, EditOperation.ADD, "key", null, "=1+1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("previewFormulaUpdates and applyFormulaUpdates");
    }

    @Test
    public void editExistingAttributesContent_rejectsFormulaBackedAttribute() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeAttributeTableModel.getModel(nodeModel).silentlyAddRowNoUndo(nodeModel, new Attribute("key", "=1+1"));
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return "=1+1".equals(value);
        });
        AttributesContentEditor uut = new AttributesContentEditor(mock(MAttributeController.class), textController);

        assertThatThrownBy(() -> uut.validateExistingAttributesContent(
            nodeModel, EditOperation.REPLACE, "key", null, "plain"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("formula-backed attributes");
    }
}

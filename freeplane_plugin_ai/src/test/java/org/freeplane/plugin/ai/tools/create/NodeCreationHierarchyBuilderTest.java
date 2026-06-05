package org.freeplane.plugin.ai.tools.create;

import java.util.Arrays;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.NodeContentApplier;
import org.freeplane.plugin.ai.tools.content.NodeContentWriteRequest;
import org.freeplane.plugin.ai.tools.edit.NodeStyleContentEditor;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NodeCreationHierarchyBuilderTest {
    @Test
    public void buildHierarchy_preservesSiblingOrderFromInputList() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        NodeContentApplier nodeContentApplier = mock(NodeContentApplier.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        doAnswer(invocation -> {
            NodeModel nodeModel = invocation.getArgument(0);
            NodeContentWriteRequest content = invocation.getArgument(1);
            if (content != null && content.getText() != null) {
                nodeModel.setText(content.getText());
            }
            return null;
        }).when(nodeContentApplier).apply(any(NodeModel.class), any(NodeContentWriteRequest.class));
        NodeCreationHierarchyBuilder builder = new NodeCreationHierarchyBuilder(nodeModelCreator, nodeContentApplier,
            nodeStyleContentEditor);

        NodeCreationItem root = new NodeCreationItem(0, -1, new NodeContentWriteRequest(
            "root", null, null, null, null, null, null, null, null, null), null, null);
        NodeCreationItem childTwo = new NodeCreationItem(2, 0, new NodeContentWriteRequest(
            "childTwo", null, null, null, null, null, null, null, null, null), null, null);
        NodeCreationItem childOne = new NodeCreationItem(1, 0, new NodeContentWriteRequest(
            "childOne", null, null, null, null, null, null, null, null, null), null, null);

        NodeCreationHierarchy hierarchy = builder.buildHierarchy(Arrays.asList(root, childTwo, childOne), mapModel);

        assertThat(hierarchy.getRootNodes()).hasSize(1);
        NodeModel rootNode = hierarchy.getRootNodes().get(0);
        assertThat(rootNode.getChildCount()).isEqualTo(2);
        assertThat(rootNode.getChildAt(0).getText()).isEqualTo("childTwo");
        assertThat(rootNode.getChildAt(1).getText()).isEqualTo("childOne");
    }

    @Test
    public void buildHierarchy_rejectsUnknownParentIndex() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        NodeCreationHierarchyBuilder builder = new NodeCreationHierarchyBuilder(nodeModelCreator,
            mock(NodeContentApplier.class), mock(NodeStyleContentEditor.class));
        NodeCreationItem root = new NodeCreationItem(0, -1, null, null, null);
        NodeCreationItem orphan = new NodeCreationItem(1, 99, null, null, null);

        assertThatThrownBy(() -> builder.buildHierarchy(Arrays.asList(root, orphan), mapModel))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown parentIndex: 99");
    }

    @Test
    public void buildHierarchy_assignsFoldingStateToNonLeafNodes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        NodeContentApplier nodeContentApplier = mock(NodeContentApplier.class);
        NodeCreationHierarchyBuilder builder = new NodeCreationHierarchyBuilder(nodeModelCreator, nodeContentApplier,
            mock(NodeStyleContentEditor.class));
        NodeCreationItem parent = new NodeCreationItem(0, -1, null, NodeFoldingState.FOLD, null);
        NodeCreationItem child = new NodeCreationItem(1, 0, null, null, null);

        NodeCreationHierarchy hierarchy = builder.buildHierarchy(Arrays.asList(parent, child), mapModel);

        assertThat(hierarchy.getFoldingStates()).hasSize(1);
        NodeModel parentNode = hierarchy.getRootNodes().get(0);
        assertThat(hierarchy.getFoldingStates().get(parentNode)).isEqualTo(NodeFoldingState.FOLD);
    }

    @Test
    public void buildHierarchy_appliesMainStyleFromItem() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        NodeContentApplier nodeContentApplier = mock(NodeContentApplier.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        NodeCreationHierarchyBuilder builder = new NodeCreationHierarchyBuilder(
            nodeModelCreator, nodeContentApplier, nodeStyleContentEditor);
        NodeCreationItem item = new NodeCreationItem(0, -1, null, null, "default");

        builder.buildHierarchy(Arrays.asList(item), mapModel);

        verify(nodeStyleContentEditor).setInitialMainStyle(any(NodeModel.class), eq("default"));
    }
}

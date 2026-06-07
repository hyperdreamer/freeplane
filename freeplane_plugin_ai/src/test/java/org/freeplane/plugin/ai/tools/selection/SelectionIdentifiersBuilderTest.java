package org.freeplane.plugin.ai.tools.selection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SelectionIdentifiersBuilderTest {
    @Test
    public void buildSelectionIdentifiersResponse_defaultsToOrderedSelectionAndIncludesCounts() {
        TextController textController = mock(TextController.class);
        NodeModel firstNode = mock(NodeModel.class);
        NodeModel secondNode = mock(NodeModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        IMapSelection selection = mock(IMapSelection.class);
        MapModel mapModel = mock(MapModel.class);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn("root-1");
        when(firstNode.getID()).thenReturn("node-1");
        when(secondNode.getID()).thenReturn("node-2");
        when(selection.getSelected()).thenReturn(firstNode);
        when(selection.getOrderedSelection()).thenReturn(Arrays.asList(firstNode, secondNode));
        when(selection.getSortedSelection(true)).thenReturn(Collections.singletonList(firstNode));
        when(textController.getShortPlainText(firstNode, 20, " ...")).thenReturn("First");
        when(textController.getShortPlainText(secondNode, 20, " ...")).thenReturn("Second");
        SelectionIdentifiersBuilder uut = new SelectionIdentifiersBuilder(textController);

        SelectionIdentifiersResponse response = uut.buildSelectionIdentifiersResponse("map-1", mapModel, selection, null);

        assertThat(response.getSelectedNodeCount()).isEqualTo(2);
        assertThat(response.getSelectedUniqueSubtreeCount()).isEqualTo(1);
        List<SelectedNodeSummary> selectedNodes = response.getSelectedNodes();
        assertThat(selectedNodes).hasSize(2);
        assertThat(selectedNodes.get(0).getNodeIdentifier()).isEqualTo("node-1");
        assertThat(selectedNodes.get(0).getShortText()).isEqualTo("First");
        assertThat(selectedNodes.get(1).getNodeIdentifier()).isEqualTo("node-2");
        assertThat(selectedNodes.get(1).getShortText()).isEqualTo("Second");
    }

    @Test
    public void buildSelectionIdentifiersResponse_honorsSingleSelectionMode() {
        TextController textController = mock(TextController.class);
        NodeModel selectedNode = mock(NodeModel.class);
        IMapSelection selection = mock(IMapSelection.class);
        when(selection.getSelected()).thenReturn(selectedNode);
        when(selectedNode.getID()).thenReturn("node-1");
        when(textController.getShortPlainText(selectedNode, 20, " ...")).thenReturn("Selected");
        SelectionIdentifiersBuilder uut = new SelectionIdentifiersBuilder(textController);

        SelectionIdentifiersResponse response = uut.buildSelectionIdentifiersResponse(
            "map-1", null, selection, SelectionCollectionMode.SINGLE);

        assertThat(response.getSelectedNodes()).hasSize(1);
        assertThat(response.getSelectedNodes().get(0).getNodeIdentifier()).isEqualTo("node-1");
        assertThat(response.getSelectedNodes().get(0).getShortText()).isEqualTo("Selected");
    }
}

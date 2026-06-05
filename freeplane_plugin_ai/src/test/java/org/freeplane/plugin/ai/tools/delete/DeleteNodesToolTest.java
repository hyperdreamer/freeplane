package org.freeplane.plugin.ai.tools.delete;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummary;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteNodesToolTest {
    @Test
    public void deleteNodes_filtersToUniqueSubtreeRootsAndCountsNodes() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MMapController mapController = mock(MMapController.class);
        TextController textController = mock(TextController.class);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        MapModel mapModel = mock(MapModel.class);
        NodeModel parentNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        NodeModel childOfChildNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("parent")).thenReturn(parentNode);
        when(mapModel.getNodeForID("child")).thenReturn(childNode);
        when(childNode.getParentNode()).thenReturn(parentNode);
        when(parentNode.getParentNode()).thenReturn(mock(NodeModel.class));
        when(parentNode.getChildCount()).thenReturn(1);
        when(parentNode.getChildAt(0)).thenReturn(childNode);
        when(childNode.getChildCount()).thenReturn(1);
        when(childNode.getChildAt(0)).thenReturn(childOfChildNode);
        when(childOfChildNode.getChildCount()).thenReturn(0);
        when(parentNode.getID()).thenReturn("parent");
        when(parentNode.createID()).thenReturn("parent");
        when(textController.getShortPlainText(parentNode, 20, " ...")).thenReturn("Parent");
        DeleteNodesTool uut = new DeleteNodesTool(availableMaps, null, mapController, modifiedNodeSummaryBuilder);

        DeleteNodesResponse response = uut.deleteNodes(
            new DeleteNodesRequest(mapIdentifier.toString(), Arrays.asList("child", "parent"), "Delete nodes"));

        assertThat(response.getDeletedSubtreeRootCount()).isEqualTo(1);
        assertThat(response.getDeletedNodeCount()).isEqualTo(3);
        List<ModifiedNodeSummary> deletedNodes = response.getDeletedNodes();
        assertThat(deletedNodes).hasSize(1);
        assertThat(deletedNodes.get(0).getNodeIdentifier()).isEqualTo("parent");
        assertThat(deletedNodes.get(0).getShortText()).isEqualTo("Parent");
        verify(mapController).deleteNodes(eq(Collections.singletonList(parentNode)));
    }

    @Test
    public void deleteNodes_rejectsRootNodeDeletion() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MMapController mapController = mock(MMapController.class);
        TextController textController = mock(TextController.class);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("root")).thenReturn(rootNode);
        when(rootNode.getParentNode()).thenReturn(null);
        DeleteNodesTool uut = new DeleteNodesTool(availableMaps, null, mapController, modifiedNodeSummaryBuilder);

        assertThatThrownBy(() -> uut.deleteNodes(
            new DeleteNodesRequest(mapIdentifier.toString(), Collections.singletonList("root"), "Delete root")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Root node deletion is not supported.");
    }

    @Test
    public void deleteNodes_deletesNodesInMapOrder() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MMapController mapController = mock(MMapController.class);
        TextController textController = mock(TextController.class);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        MapModel mapModel = mock(MapModel.class);
        NodeModel firstNode = mock(NodeModel.class);
        NodeModel secondNode = mock(NodeModel.class);
        NodeModel parentNode = mock(NodeModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("first")).thenReturn(firstNode);
        when(mapModel.getNodeForID("second")).thenReturn(secondNode);
        when(firstNode.getMap()).thenReturn(mapModel);
        when(secondNode.getMap()).thenReturn(mapModel);
        when(firstNode.getParentNode()).thenReturn(parentNode);
        when(secondNode.getParentNode()).thenReturn(parentNode);
        when(parentNode.getParentNode()).thenReturn(rootNode);
        when(rootNode.getPathToRoot()).thenReturn(new NodeModel[] { rootNode });
        when(parentNode.getPathToRoot()).thenReturn(new NodeModel[] { rootNode, parentNode });
        when(firstNode.getPathToRoot()).thenReturn(new NodeModel[] { rootNode, parentNode, firstNode });
        when(secondNode.getPathToRoot()).thenReturn(new NodeModel[] { rootNode, parentNode, secondNode });
        when(parentNode.getIndex(firstNode)).thenReturn(0);
        when(parentNode.getIndex(secondNode)).thenReturn(1);
        when(firstNode.getChildCount()).thenReturn(0);
        when(secondNode.getChildCount()).thenReturn(0);
        when(firstNode.getID()).thenReturn("first");
        when(secondNode.getID()).thenReturn("second");
        when(textController.getShortPlainText(firstNode, 20, " ...")).thenReturn("First");
        when(textController.getShortPlainText(secondNode, 20, " ...")).thenReturn("Second");
        DeleteNodesTool uut = new DeleteNodesTool(availableMaps, null, mapController, modifiedNodeSummaryBuilder);

        uut.deleteNodes(new DeleteNodesRequest(mapIdentifier.toString(),
            Arrays.asList("second", "first"), "Delete nodes"));

        verify(mapController).deleteNodes(eq(Arrays.asList(firstNode, secondNode)));
    }
}

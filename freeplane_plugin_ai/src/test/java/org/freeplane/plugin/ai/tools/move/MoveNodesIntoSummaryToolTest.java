package org.freeplane.plugin.ai.tools.move;

import java.util.Arrays;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.OperationErrorHandler;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MoveNodesIntoSummaryToolTest {
    @Test
    public void moveNodesIntoSummary_createsSummaryNodeAndMovesNodes() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MMapController mapController = mock(MMapController.class);
        SummaryNodeCreator summaryNodeCreator = mock(SummaryNodeCreator.class);
        MoveNodesIntoSummaryTool unitUnderTest = new MoveNodesIntoSummaryTool(availableMaps, null, mapController,
            summaryNodeCreator);
        UUID mapIdentifier = UUID.fromString("28f31fd6-7c67-402e-9bb2-9c756498ba7f");
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel rootNode = new NodeModel("root", mapModel);
        mapModel.setRoot(rootNode);
        NodeModel firstNode = new NodeModel("first", mapModel);
        NodeModel lastNode = new NodeModel("last", mapModel);
        firstNode.setID("ID_first");
        lastNode.setID("ID_last");
        NodeModel firstMovedNode = new NodeModel("moved-1", mapModel);
        NodeModel secondMovedNode = new NodeModel("moved-2", mapModel);
        firstMovedNode.setID("ID_moved_1");
        secondMovedNode.setID("ID_moved_2");
        NodeModel summaryNode = new NodeModel("summary", mapModel);
        summaryNode.setID("ID_summary");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(summaryNodeCreator.createSummaryNode(rootNode, firstNode, lastNode)).thenReturn(summaryNode);
        MoveNodesIntoSummaryRequest request = new MoveNodesIntoSummaryRequest(
            mapIdentifier.toString(),
            "Move into summary",
            new SummaryAnchorPlacement("ID_first", "ID_last"),
            Arrays.asList("ID_moved_1", "ID_moved_2"));

        MoveNodesIntoSummaryResponse response = unitUnderTest.moveNodesIntoSummary(request);

        assertThat(response.getSummaryNodeIdentifier()).isEqualTo("ID_summary");
        assertThat(response.getParentNodeIdentifier()).isEqualTo("ID_summary");
        assertThat(response.getInsertionIndex()).isEqualTo(0);
        verify(mapController).moveNodes(eq(Arrays.asList(firstMovedNode, secondMovedNode)), eq(summaryNode), eq(0),
            any(OperationErrorHandler.class));
    }
}

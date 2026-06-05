package org.freeplane.plugin.ai.tools.move;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.OperationErrorHandler;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementMode;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchy;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchyBuilder;
import org.freeplane.plugin.ai.tools.create.NodeCreationItem;
import org.freeplane.plugin.ai.tools.create.NodeInserter;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreateSummaryToolTest {
    @Test
    public void createSummary_returnsSummaryNodeIdentifier() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder = mock(NodeCreationHierarchyBuilder.class);
        NodeInserter nodeInserter = mock(NodeInserter.class);
        SummaryNodeCreator summaryNodeCreator = mock(SummaryNodeCreator.class);
        TextController textController = mock(TextController.class);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        CreateSummaryTool unitUnderTest = new CreateSummaryTool(availableMaps, null, nodeCreationHierarchyBuilder,
            nodeInserter, summaryNodeCreator, modifiedNodeSummaryBuilder);
        UUID mapIdentifier = UUID.fromString("2d872386-bb55-4c6c-9da0-d600b7400b10");
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel rootNode = new NodeModel("root", mapModel);
        mapModel.setRoot(rootNode);
        NodeModel firstNode = new NodeModel("first", mapModel);
        NodeModel lastNode = new NodeModel("last", mapModel);
        firstNode.setID("ID_first");
        lastNode.setID("ID_last");
        NodeModel summaryNode = new NodeModel("summary", mapModel);
        summaryNode.setID("ID_summary");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(summaryNodeCreator.createSummaryNode(rootNode, firstNode, lastNode)).thenReturn(summaryNode);
        NodeCreationItem childItem = new NodeCreationItem(0, -1, null, null, null);
        NodeModel childNodeModel = new NodeModel("child", mapModel);
        childNodeModel.setID("ID_child");
        when(nodeCreationHierarchyBuilder.buildHierarchy(Collections.singletonList(childItem), mapModel))
            .thenReturn(new NodeCreationHierarchy(Collections.singletonList(childNodeModel),
                Collections.singletonList(childNodeModel)));
        when(nodeInserter.insertNodes(anyList(), eq(summaryNode), eq(AnchorPlacementMode.LAST_CHILD),
            any(OperationErrorHandler.class)))
            .thenReturn(Collections.singletonList(childNodeModel));
        when(textController.getShortPlainText(summaryNode, 20, " ...")).thenReturn("Summary");
        when(textController.getShortPlainText(childNodeModel, 20, " ...")).thenReturn("Child");
        CreateSummaryRequest request = new CreateSummaryRequest(
            mapIdentifier.toString(),
            "Create summary",
            new SummaryAnchorPlacement("ID_first", "ID_last"),
            Arrays.asList(childItem));

        CreateSummaryResponse response = unitUnderTest.createSummary(request);

        assertThat(response.getSummaryNodeIdentifier()).isEqualTo("ID_summary");
        assertThat(response.getModifiedNodes()).hasSize(2);
        verify(nodeInserter).insertNodes(anyList(), eq(summaryNode), eq(AnchorPlacementMode.LAST_CHILD),
            any(OperationErrorHandler.class));
        verify(nodeCreationHierarchyBuilder).buildHierarchy(Collections.singletonList(childItem), mapModel);
    }
}

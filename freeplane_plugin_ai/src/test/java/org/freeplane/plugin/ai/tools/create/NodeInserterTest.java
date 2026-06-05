package org.freeplane.plugin.ai.tools.create;

import java.util.Arrays;
import java.util.Collections;
import org.freeplane.features.layout.LayoutController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeModel.Side;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.OperationErrorHandler;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.junit.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NodeInserterTest {
    @Test
    public void insertNodes_insertsInOrder() {
        MMapController mapController = mock(MMapController.class);
        when(mapController.isWriteable(any(NodeModel.class))).thenReturn(true);
        AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
        NodeInserter unitUnderTest = new NodeInserter(mapController, anchorPlacementCalculator);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        NodeModel firstNode = new NodeModel("first", mapModel);
        NodeModel secondNode = new NodeModel("second", mapModel);

        OperationErrorHandler handler = (description, involvedNodes) -> {};
        unitUnderTest.insertNodes(Arrays.asList(firstNode, secondNode), anchorNode, AnchorPlacementMode.FIRST_CHILD,
            handler);

        InOrder inOrder = inOrder(mapController);
        inOrder.verify(mapController).insertNode(firstNode, anchorNode, 0, handler);
        inOrder.verify(mapController).insertNode(secondNode, anchorNode, 1, handler);
    }

    @Test
    public void insertNodes_assignsSiblingSideToAnchorSide() {
        MMapController mapController = mock(MMapController.class);
        when(mapController.isWriteable(any(NodeModel.class))).thenReturn(true);
        AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
        NodeInserter unitUnderTest = new NodeInserter(mapController, anchorPlacementCalculator);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel parentNode = new NodeModel("parent", mapModel);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        parentNode.insert(anchorNode, 0);
        anchorNode.setSide(Side.TOP_OR_LEFT);
        NodeModel newNode = new NodeModel("new", mapModel);

        OperationErrorHandler handler = (description, involvedNodes) -> {};
        unitUnderTest.insertNodes(Collections.singletonList(newNode), anchorNode, AnchorPlacementMode.SIBLING_AFTER,
            handler);

        assertThat(newNode.getSide()).isEqualTo(Side.TOP_OR_LEFT);
    }

    @Test
    public void insertNodes_assignsRootChildSideFromMapController() {
        Controller originalController = Controller.getCurrentController();
        Controller mockController = mock(Controller.class);
        ModeController modeController = mock(ModeController.class);
        LayoutController layoutController = mock(LayoutController.class);
        when(mockController.getModeController()).thenReturn(modeController);
        when(mockController.getSelection()).thenReturn(null);
        when(modeController.getExtension(LayoutController.class)).thenReturn(layoutController);
        Controller.setCurrentController(mockController);
        try {
            MMapController mapController = mock(MMapController.class);
            when(mapController.isWriteable(any(NodeModel.class))).thenReturn(true);
            AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
            NodeInserter unitUnderTest = new NodeInserter(mapController, anchorPlacementCalculator);
            MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
            NodeModel rootNode = new NodeModel("root", mapModel);
            mapModel.setRoot(rootNode);
            NodeModel newNode = new NodeModel("new", mapModel);
            when(layoutController.suggestNewChildSide(rootNode, rootNode)).thenReturn(Side.TOP_OR_LEFT);
            OperationErrorHandler handler = (description, involvedNodes) -> {};

            unitUnderTest.insertNodes(Collections.singletonList(newNode), rootNode, AnchorPlacementMode.LAST_CHILD,
                handler);

            assertThat(newNode.getSide()).isEqualTo(Side.TOP_OR_LEFT);
        } finally {
            Controller.setCurrentController(originalController);
        }
    }

    @Test
    public void insertNodes_rejectsWriteProtectedNodes() {
        MMapController mapController = mock(MMapController.class);
        when(mapController.isWriteable(any(NodeModel.class))).thenReturn(false);
        AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
        NodeInserter unitUnderTest = new NodeInserter(mapController, anchorPlacementCalculator);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        NodeModel newNode = new NodeModel("new", mapModel);

        OperationErrorHandler handler = (description, involvedNodes) -> {};
        assertThatThrownBy(() -> unitUnderTest.insertNodes(Collections.singletonList(newNode), anchorNode,
            AnchorPlacementMode.FIRST_CHILD, handler))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Target node is write protected.");
    }
}

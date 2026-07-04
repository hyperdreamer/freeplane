package org.freeplane.view.swing.map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Color;

import org.freeplane.api.ChildNodesLayout;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.layout.LayoutController;
import org.freeplane.features.map.MapFake;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeModel.Side;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.styles.MapViewLayout;
import org.junit.Test;
import org.mockito.MockedStatic;

public class NodeViewChildNodeLayoutTest {
    @Test
    public void keepsDefaultSummaryChildOnSummarySide() {
        MapFake mapFake = new MapFake();
        NodeModel root = mapFake.getRoot();
        NodeModel summary = mapFake.createSummaryNode();
        summary.setSide(Side.TOP_OR_LEFT);
        root.insert(summary);
        NodeModel summaryContent = mapFake.createNode("summary");
        summary.insert(summaryContent);

        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(anyString())).thenReturn("false");
        when(resourceController.getBooleanProperty(anyString())).thenReturn(false);
        when(resourceController.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(resourceController.getColorProperty(anyString())).thenReturn(Color.BLACK);
        when(resourceController.getLengthProperty(anyString())).thenReturn(0);

        try (MockedStatic<ResourceController> resourceControllers = mockStatic(ResourceController.class)) {
            resourceControllers.when(ResourceController::getResourceController).thenReturn(resourceController);
            MapView map = mock(MapView.class);
            ModeController modeController = mock(ModeController.class);
            LayoutController layoutController = mock(LayoutController.class);
            when(map.isOutlineLayoutSet()).thenReturn(false);
            when(map.getLayoutType()).thenReturn(MapViewLayout.MAP);
            when(map.getModeController()).thenReturn(modeController);
            when(modeController.getExtension(LayoutController.class)).thenReturn(layoutController);
            when(layoutController.getEffectiveChildNodesLayout(any())).thenAnswer(invocation -> {
                NodeModel node = invocation.getArgument(0);
                return node == root ? ChildNodesLayout.TOPTOBOTTOM_BOTHSIDES_FLOW : ChildNodesLayout.AUTO;
            });

            NodeView.ChildNodeViewLayout layout = new NodeView.ChildNodeViewLayout(summaryContent, map, null);

            assertThat(layout.isTopOrLeft(), equalTo(true));
        }
    }
}

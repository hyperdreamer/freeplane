package org.freeplane.plugin.ai.chat.request;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.freeplane.api.MindMap;
import org.freeplane.api.Node;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.maps.MapModelProvider;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiSelectionOverrideResolverTest {

    @Test
    public void resolvesPublicMapAndNodeIdsToInjectedSelectionStructure() {
        MapModelProvider mapModelProvider = mock(MapModelProvider.class);
        AvailableMaps availableMaps = new AvailableMaps(mapModelProvider);
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel firstNode = mock(NodeModel.class);
        NodeModel secondNode = mock(NodeModel.class);
        when(mapModelProvider.getOpenMapModels()).thenReturn(Collections.singletonList(mapModel));
        when(mapModel.getFile()).thenReturn(new File("/tmp/map.mm"));
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn("ID_ROOT");
        when(mapModel.getNodeForID("ID_1")).thenReturn(firstNode);
        when(mapModel.getNodeForID("ID_2")).thenReturn(secondNode);
        when(firstNode.getID()).thenReturn("ID_1");
        when(secondNode.getID()).thenReturn("ID_2");
        when(secondNode.isDescendantOf(firstNode)).thenReturn(true);
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(firstNode, 40, " ...")).thenReturn("Alpha");
        when(textController.getShortPlainText(secondNode, 40, " ...")).thenReturn("Beta");
        MindMap mindMap = mock(MindMap.class);
        Node rootNodeProxy = mock(Node.class);
        when(mindMap.getFile()).thenReturn(new File("/tmp/map.mm"));
        when(mindMap.getRoot()).thenReturn(rootNodeProxy);
        when(rootNodeProxy.getId()).thenReturn("ID_ROOT");

        AiSelectionOverrideResolver uut = new AiSelectionOverrideResolver(availableMaps, new NodeTextPreviewFormatter(textController));

        SelectionIdentifiersResponse result = uut.resolve(
            new AiSelectionOverride(mindMap, Arrays.asList("ID_1", "ID_2")));

        assertThat(result.getMapIdentifier()).isEqualTo(availableMaps.getOrCreateMapIdentifier(mapModel).toString());
        assertThat(result.getNodeIdentifier()).isEqualTo("ID_1");
        assertThat(result.getRootNodeIdentifier()).isEqualTo("ID_ROOT");
        assertThat(result.getSelectedNodeCount()).isEqualTo(2);
        assertThat(result.getSelectedUniqueSubtreeCount()).isEqualTo(1);
        assertThat(result.getSelectedNodes())
            .extracting(summary -> summary.getNodeIdentifier() + ":" + summary.getShortText())
            .containsExactly("ID_1:Alpha", "ID_2:Beta");
    }
}

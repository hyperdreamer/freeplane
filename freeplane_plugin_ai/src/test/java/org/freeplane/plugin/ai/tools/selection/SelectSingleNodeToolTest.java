package org.freeplane.plugin.ai.tools.selection;

import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SelectSingleNodeToolTest {
    @Test
    public void selectSingleNode_displaysAndSelectsNode() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MMapController mapController = mock(MMapController.class, RETURNS_DEEP_STUBS);
        SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool = mock(SelectedMapAndNodeIdentifiersTool.class);
        MapModel mapModel = mock(MapModel.class);
        NodeModel nodeModel = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("node-1")).thenReturn(nodeModel);
        SelectionIdentifiersResponse response = mock(SelectionIdentifiersResponse.class);
        when(selectedMapAndNodeIdentifiersTool.getSelectedMapAndNodeIdentifiers(null)).thenReturn(response);
        SelectSingleNodeTool uut = new SelectSingleNodeTool(availableMaps, null, mapController,
            selectedMapAndNodeIdentifiersTool);

        SelectionIdentifiersResponse result = uut.selectSingleNode(
            new SelectSingleNodeRequest(mapIdentifier.toString(), "node-1"));

        assertThat(result).isSameAs(response);
        verify(mapController).displayNode(nodeModel);
        verify(mapController.getModeController().getController().getSelection()).selectAsTheOnlyOneSelected(nodeModel);
        verify(selectedMapAndNodeIdentifiersTool).getSelectedMapAndNodeIdentifiers(null);
    }
}

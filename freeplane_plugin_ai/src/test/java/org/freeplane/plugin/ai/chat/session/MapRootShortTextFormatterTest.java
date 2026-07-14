package org.freeplane.plugin.ai.chat.session;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapRootShortTextFormatterTest {
    @Test
    public void buildCounts_usesShortTextForMapRoots() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        UUID mapIdentifier = UUID.fromString("7f637e20-c8db-45c4-9f5c-0a423747a91c");
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        TextController textController = mock(TextController.class);
        when(availableMaps.findMapModel(mapIdentifier)).thenReturn(mapModel);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(textController.getShortPlainText(rootNode, 40, " ...")).thenReturn("Map root");
        MapRootShortTextFormatter uut = new MapRootShortTextFormatter(availableMaps,
            new NodeTextPreviewFormatter(textController));

        List<MapRootShortTextCount> counts = uut.buildCounts(Collections.singletonList(mapIdentifier.toString()));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getText()).isEqualTo("Map root");
        assertThat(counts.get(0).getCount()).isEqualTo(1);
    }
}

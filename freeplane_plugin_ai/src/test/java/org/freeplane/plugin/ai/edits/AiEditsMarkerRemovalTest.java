package org.freeplane.plugin.ai.edits;

import java.util.Collections;
import java.util.Map;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AiEditsMarkerRemovalTest {
    @Test
    public void createUndoableRemoval_removesAndRestoresMarkers() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel rootNode = new NodeModel("root", mapModel);
        mapModel.setRoot(rootNode);
        NodeModel childNode = new NodeModel("child", mapModel);
        rootNode.insert(childNode, 0);
        childNode.setMap(mapModel);
        AIEdits aiEdits = new AIEdits();
        childNode.addExtension(aiEdits);
        MapController mapController = mock(MapController.class);
        AiEditsMarkerRemoval uut = new AiEditsMarkerRemoval();
        Map<NodeModel, AIEdits> removedEditsByNode = uut.collectNodesWithAiEdits(Collections.singletonList(childNode));

        IActor actor = uut.createUndoableRemoval(removedEditsByNode, mapController, "clear");
        actor.act();

        assertThat(childNode.getExtension(AIEdits.class)).isNull();
        verify(mapController).nodeChanged(childNode, AIEdits.class, aiEdits, null);

        actor.undo();

        assertThat(childNode.getExtension(AIEdits.class)).isSameAs(aiEdits);
        verify(mapController).nodeChanged(childNode, AIEdits.class, null, aiEdits);
    }
}

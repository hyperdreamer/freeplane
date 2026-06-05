package org.freeplane.plugin.ai.edits;

import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeBuilder;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiEditsPersistenceBuilderTest {
    @Test
    public void readHandler_addsMarkerForNodeAttribute() {
        ReadManager readManager = new ReadManager();
        WriteManager writeManager = new WriteManager();
        AiEditsPersistenceBuilder uut = new AiEditsPersistenceBuilder(new AiEditsSettings(mock(ResourceController.class)));
        uut.registerBy(readManager, writeManager);
        IAttributeHandler handler = readManager.getAttributeHandlers()
            .get(NodeBuilder.XML_NODE)
            .get(AiEditsPersistenceBuilder.AI_EDITS_ATTRIBUTE);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel node = new NodeModel("node", mapModel);

        handler.setAttribute(node, "true");

        assertThat(node.getExtension(AIEdits.class)).isNotNull();
    }

    @Test
    public void readHandler_addsMarkerForStyleNodeAttribute() {
        ReadManager readManager = new ReadManager();
        WriteManager writeManager = new WriteManager();
        AiEditsPersistenceBuilder uut = new AiEditsPersistenceBuilder(new AiEditsSettings(mock(ResourceController.class)));
        uut.registerBy(readManager, writeManager);
        IAttributeHandler handler = readManager.getAttributeHandlers()
            .get(NodeBuilder.XML_STYLENODE)
            .get(AiEditsPersistenceBuilder.AI_EDITS_ATTRIBUTE);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel node = new NodeModel("node", mapModel);

        handler.setAttribute(node, "true");

        assertThat(node.getExtension(AIEdits.class)).isNotNull();
    }

    @Test
    public void writeAttributes_emitsAttributeWhenPersistenceEnabled() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getBooleanProperty(AiEditsSettings.AI_EDITS_PERSISTENCE_ENABLED_PROPERTY))
            .thenReturn(true);
        AiEditsPersistenceBuilder uut = new AiEditsPersistenceBuilder(new AiEditsSettings(resourceController));
        ITreeWriter writer = mock(ITreeWriter.class);

        uut.writeAttributes(writer, new Object(), new AIEdits());

        verify(writer).addAttribute(AiEditsPersistenceBuilder.AI_EDITS_ATTRIBUTE, "true");
    }

    @Test
    public void writeAttributes_skipsWhenPersistenceDisabledEvenWithMarker() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getBooleanProperty(AiEditsSettings.AI_EDITS_PERSISTENCE_ENABLED_PROPERTY))
            .thenReturn(false);
        AiEditsPersistenceBuilder uut = new AiEditsPersistenceBuilder(new AiEditsSettings(resourceController));
        ITreeWriter writer = mock(ITreeWriter.class);

        uut.writeAttributes(writer, new Object(), new AIEdits());

        verify(writer, never()).addAttribute(AiEditsPersistenceBuilder.AI_EDITS_ATTRIBUTE, "true");
    }
}

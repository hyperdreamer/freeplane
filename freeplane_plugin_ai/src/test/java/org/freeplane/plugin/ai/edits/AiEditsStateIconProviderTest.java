package org.freeplane.plugin.ai.edits;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.icon.UIIcon;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiEditsStateIconProviderTest {
    @Test
    public void getStateIcon_returnsIconWhenVisibleAndMarked() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getBooleanProperty(AiEditsSettings.AI_EDITS_STATE_ICON_VISIBLE_PROPERTY)).thenReturn(true);
        AiEditsSettings aiEditsSettings = new AiEditsSettings(resourceController);
        UIIcon icon = mock(UIIcon.class);
        AiEditsStateIconDecision decision = new AiEditsStateIconDecision();
        AiEditsStateIconProvider uut = new AiEditsStateIconProvider(aiEditsSettings, decision, () -> icon);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        nodeModel.addExtension(new AIEdits());

        assertThat(uut.getStateIcon(nodeModel)).isSameAs(icon);
    }

    @Test
    public void getStateIcon_returnsNullWhenHidden() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getBooleanProperty(AiEditsSettings.AI_EDITS_STATE_ICON_VISIBLE_PROPERTY)).thenReturn(false);
        AiEditsSettings aiEditsSettings = new AiEditsSettings(resourceController);
        UIIcon icon = mock(UIIcon.class);
        AiEditsStateIconDecision decision = new AiEditsStateIconDecision();
        AiEditsStateIconProvider uut = new AiEditsStateIconProvider(aiEditsSettings, decision, () -> icon);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        nodeModel.addExtension(new AIEdits());

        assertThat(uut.getStateIcon(nodeModel)).isNull();
    }
}

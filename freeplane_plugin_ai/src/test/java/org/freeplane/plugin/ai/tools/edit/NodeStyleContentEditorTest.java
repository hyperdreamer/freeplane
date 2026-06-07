package org.freeplane.plugin.ai.tools.edit;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NodeStyleContentEditorTest {
    @Test
    public void setInitialMainStyle_rejectsBlankStyleName() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeStyleContentEditor uut = new NodeStyleContentEditor();

        assertThatThrownBy(() -> uut.setInitialMainStyle(nodeModel, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid mainStyle: blank string. Omit mainStyle to use the default style.");
    }
}

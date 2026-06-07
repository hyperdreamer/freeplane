package org.freeplane.plugin.ai.tools.create;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NodeModelCreatorTest {
    @Test
    public void createNodeModel_usesProvidedMapModel() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModelCreator unitUnderTest = new NodeModelCreator();

        NodeModel node = unitUnderTest.createNodeModel(mapModel);

        assertThat(node.getMap()).isSameAs(mapModel);
    }

    @Test
    public void createNodeModel_requiresMapModel() {
        NodeModelCreator unitUnderTest = new NodeModelCreator();

        assertThatThrownBy(() -> unitUnderTest.createNodeModel(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing map model.");
    }
}

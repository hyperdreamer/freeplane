package org.freeplane.plugin.ai.tools.create;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AnchorPlacementCalculatorTest {
    @Test
    public void calculatePlacement_returnsFirstChildPlacement() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        anchorNode.insert(new NodeModel("child", mapModel), 0);
        AnchorPlacementCalculator unitUnderTest = new AnchorPlacementCalculator();

        AnchorPlacementResult result = unitUnderTest.calculatePlacement(anchorNode, AnchorPlacementMode.FIRST_CHILD);

        assertThat(result.getParentNode()).isSameAs(anchorNode);
        assertThat(result.getInsertionIndex()).isEqualTo(0);
    }

    @Test
    public void calculatePlacement_returnsLastChildPlacement() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        anchorNode.insert(new NodeModel("child-1", mapModel), 0);
        anchorNode.insert(new NodeModel("child-2", mapModel), 1);
        AnchorPlacementCalculator unitUnderTest = new AnchorPlacementCalculator();

        AnchorPlacementResult result = unitUnderTest.calculatePlacement(anchorNode, AnchorPlacementMode.LAST_CHILD);

        assertThat(result.getParentNode()).isSameAs(anchorNode);
        assertThat(result.getInsertionIndex()).isEqualTo(2);
    }

    @Test
    public void calculatePlacement_returnsSiblingBeforePlacement() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel parentNode = new NodeModel("parent", mapModel);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        parentNode.insert(anchorNode, 0);
        AnchorPlacementCalculator unitUnderTest = new AnchorPlacementCalculator();

        AnchorPlacementResult result = unitUnderTest.calculatePlacement(anchorNode, AnchorPlacementMode.SIBLING_BEFORE);

        assertThat(result.getParentNode()).isSameAs(parentNode);
        assertThat(result.getInsertionIndex()).isEqualTo(0);
    }

    @Test
    public void calculatePlacement_returnsSiblingAfterPlacement() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel parentNode = new NodeModel("parent", mapModel);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        parentNode.insert(anchorNode, 0);
        AnchorPlacementCalculator unitUnderTest = new AnchorPlacementCalculator();

        AnchorPlacementResult result = unitUnderTest.calculatePlacement(anchorNode, AnchorPlacementMode.SIBLING_AFTER);

        assertThat(result.getParentNode()).isSameAs(parentNode);
        assertThat(result.getInsertionIndex()).isEqualTo(1);
    }

    @Test
    public void calculatePlacement_throwsForSiblingPlacementWithoutParent() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel anchorNode = new NodeModel("anchor", mapModel);
        AnchorPlacementCalculator unitUnderTest = new AnchorPlacementCalculator();

        assertThatThrownBy(() -> unitUnderTest.calculatePlacement(anchorNode, AnchorPlacementMode.SIBLING_BEFORE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Sibling placement requires a non-root anchor node.");
    }
}

package org.freeplane.features.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.freeplane.features.filter.condition.NoFilteringCondition;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

public class FilterAncestorTest {
    @Test
    public void marksOnlyAncestorsForOrdinaryFiltering() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("root", map);
        map.setRoot(root);
        NodeModel ancestor = new NodeModel("plain-parent", map);
        root.insert(ancestor);
        NodeModel matchingLeaf = new NodeModel("match-leaf", map);
        ancestor.insert(matchingLeaf);

        Filter filter = new Filter(node -> node.toString().startsWith("match"), false, true, true, false, null);
        filter.calculateFilterResults(map);

        assertThat(filter.isFilteredAsAncestor(ancestor)).isTrue();
        assertThat(filter.isFilteredAsAncestor(matchingLeaf)).isFalse();
    }

    @Test
    public void marksOnlyAncestorsWhenMatchesAreHidden() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("match-root", map);
        map.setRoot(root);
        NodeModel matchingParent = new NodeModel("match-parent", map);
        root.insert(matchingParent);
        NodeModel nonMatchingLeaf = new NodeModel("non-match-leaf", map);
        matchingParent.insert(nonMatchingLeaf);

        Filter filter = new Filter(node -> node.toString().startsWith("match"), true, true, true, false, null);
        filter.calculateFilterResults(map);

        assertThat(filter.isFilteredAsAncestor(matchingParent)).isTrue();
        assertThat(filter.isFilteredAsAncestor(nonMatchingLeaf)).isFalse();
    }

    @Test
    public void doesNotTreatNoFilteringConditionAsAncestorState() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("root", map);
        map.setRoot(root);
        NodeModel child = new NodeModel("child", map);
        root.insert(child);

        Filter filter = new Filter(NoFilteringCondition.createCondition(), false, true, true, false, null);
        filter.calculateFilterResults(map);

        assertThat(filter.isFilteredAsAncestor(root)).isFalse();
        assertThat(filter.isFilteredAsAncestor(child)).isFalse();
    }

    @Test
    public void doesNotTreatConnectorOnlyFilteringAsAncestorState() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("root", map);
        map.setRoot(root);
        NodeModel matchingLeaf = new NodeModel("match-leaf", map);
        root.insert(matchingLeaf);

        Filter filter = new Filter(node -> node.toString().startsWith("match"), false, true, true, false,
                Filter.FilteredElement.CONNECTOR, null);
        filter.calculateFilterResults(map);

        assertThat(filter.isFilteredAsAncestor(root)).isFalse();
        assertThat(filter.isFilteredAsAncestor(matchingLeaf)).isFalse();
    }
}

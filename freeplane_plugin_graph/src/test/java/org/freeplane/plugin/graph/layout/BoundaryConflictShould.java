package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;

public class BoundaryConflictShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void normalizeThePairIntoCanonicalOrder() {
        final EnclosureHullKey larger = hull(MAP_TWO, "z");
        final EnclosureHullKey smaller = hull(MAP_ONE, "a");

        final BoundaryConflict conflict = new BoundaryConflict(larger, smaller,
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Collections.<PinProjection>emptyList());

        assertThat(conflict.firstHull()).isEqualTo(smaller);
        assertThat(conflict.secondHull()).isEqualTo(larger);
        assertThat(conflict.pairKey()).isEqualTo(CanonicalLayoutKeys.pair(smaller, larger));
        assertThat(conflict.firstMap()).isEqualTo(MAP_ONE);
        assertThat(conflict.secondMap()).isEqualTo(MAP_TWO);
        assertThat(conflict.kind()).isEqualTo(BoundaryConflict.Kind.SIBLING_CROSSING);
        assertThat(conflict.reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
    }

    @Test
    public void allowSameMapConflictsAndSortBlockingPins() {
        final EnclosureHullKey first = hull(MAP_ONE, "a");
        final EnclosureHullKey second = hull(MAP_ONE, "b");
        final PinProjection laterNode = active(MAP_ONE, "n2");
        final PinProjection earlierNode = active(MAP_ONE, "n1");
        final PinProjection otherMap = active(MAP_TWO, "n0");

        final BoundaryConflict conflict = new BoundaryConflict(first, second,
            BoundaryConflict.Kind.SIBLING_CONTAINMENT, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Arrays.asList(laterNode, otherMap, earlierNode));

        assertThat(conflict.firstMap()).isEqualTo(conflict.secondMap());
        assertThat(conflict.blockingPins()).containsExactly(earlierNode, laterNode, otherMap);
        assertThatThrownBy(() -> conflict.blockingPins().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void rejectDormantBlockingPins() {
        final PinProjection active = active(MAP_ONE, "n1");

        assertThatThrownBy(() -> new BoundaryConflict(hull(MAP_ONE, "a"), hull(MAP_ONE, "b"),
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Collections.singletonList(PinProjection.dormant(active.record()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PinProjection active(MapReferenceId map, String id) {
        final ProjectedNodeKey node = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(map, PersistedNodeId.of(id))));
        return PinProjection.active(PinRecord.of(node.source().persistedReference().get(), 1.0, 2.0,
            Collections.<UnknownXml>emptyList()), node);
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }
}

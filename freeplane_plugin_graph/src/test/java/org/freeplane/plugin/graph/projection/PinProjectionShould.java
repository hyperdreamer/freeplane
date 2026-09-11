package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.junit.Test;

public class PinProjectionShould {
    @Test
    public void resolvesPinnedStateFromActiveProjectionPins() {
        final MapReferenceId map = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final NodeReference firstReference = NodeReference.of(map, PersistedNodeId.of("first"));
        final NodeReference secondReference = NodeReference.of(map, PersistedNodeId.of("second"));
        final NodeReference thirdReference = NodeReference.of(map, PersistedNodeId.of("third"));
        final ProjectedNodeKey first = ProjectedNodeKey.of(SourceNodeKey.persisted(firstReference));
        final ProjectedNodeKey second = ProjectedNodeKey.of(SourceNodeKey.persisted(secondReference));
        final ProjectedNodeKey third = ProjectedNodeKey.of(SourceNodeKey.persisted(thirdReference));
        final GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(
                ProjectedNode.of(first, SafeNodeLabel.of("First", "First"), "Map", false),
                ProjectedNode.of(second, SafeNodeLabel.of("Second", "Second"), "Map", false),
                ProjectedNode.of(third, SafeNodeLabel.of("Third", "Third"), "Map", false)),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(),
            Arrays.asList(
                PinProjection.active(PinRecord.of(firstReference, 1.0, 2.0, Collections.emptyList()), first),
                PinProjection.dormant(PinRecord.of(secondReference, 3.0, 4.0, Collections.emptyList()))));

        assertThat(PinProjection.isPinned(projection, first)).isTrue();
        assertThat(PinProjection.isPinned(projection, second)).isFalse();
        assertThat(PinProjection.isPinned(projection, third)).isFalse();
    }
}

package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.junit.Test;

public class DirectionCoverageShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void applyTheCompleteSingleContributorDirectionTruthTable() {
        SourceNodeKey source = source("source");
        NodeReference target = reference("target");
        ProjectedEndpointKey projectedSource = endpoint(source);
        ProjectedEndpointKey projectedTarget = endpoint(SourceNodeKey.persisted(target));

        for (int bits = 0; bits < 4; bits++) {
            EdgeContributor contributor = contributor(0, source, target, (bits & 1) != 0, (bits & 2) != 0,
                projectedSource, projectedTarget);
            boolean atSource = (bits & 1) != 0;
            boolean atTarget = (bits & 2) != 0;
            assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"), target,
                RelationshipDirection.FORWARD)).isEqualTo(atTarget);
            assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"), target,
                RelationshipDirection.BIDIRECTIONAL)).isEqualTo(atSource && atTarget);
            assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"), target,
                RelationshipDirection.UNDIRECTED)).isEqualTo(!atSource && !atTarget);
        }
    }

    @Test
    public void reorientArrowsWhenTheContributorSourceIsRequestedTarget() {
        SourceNodeKey contributorSource = source("target");
        NodeReference contributorTarget = reference("source");
        EdgeContributor contributor = contributor(0, contributorSource, contributorTarget, true, false,
            endpoint(contributorSource), endpoint(SourceNodeKey.persisted(contributorTarget)));

        assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"),
            reference("target"), RelationshipDirection.FORWARD)).isTrue();
        assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("target"),
            reference("source"), RelationshipDirection.FORWARD)).isFalse();
    }

    @Test
    public void letOneBidirectionalContributorCoverEitherDirectedRequest() {
        SourceNodeKey source = source("source");
        NodeReference target = reference("target");
        EdgeContributor contributor = contributor(0, source, target, true, true, endpoint(source),
            endpoint(SourceNodeKey.persisted(target)));

        assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"), target,
            RelationshipDirection.FORWARD)).isTrue();
        assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), target, reference("source"),
            RelationshipDirection.FORWARD)).isTrue();
    }

    @Test
    public void unionOppositeDirectedContributorsForBidirectionalCoverage() {
        SourceNodeKey source = source("source");
        SourceNodeKey target = source("target");
        EdgeContributor forward = contributor(0, source, reference("target"), false, true, endpoint(source),
            endpoint(target));
        EdgeContributor reverse = contributor(1, target, reference("source"), false, true, endpoint(target),
            endpoint(source));

        assertThat(DirectionCoverage.covers(Arrays.asList(forward, reverse), reference("source"),
            reference("target"), RelationshipDirection.BIDIRECTIONAL)).isTrue();
    }

    @Test
    public void neverLetDirectedOrBidirectionalContributorsCoverAnUndirectedRequest() {
        SourceNodeKey source = source("source");
        NodeReference target = reference("target");
        EdgeContributor directed = contributor(0, source, target, false, true, endpoint(source),
            endpoint(SourceNodeKey.persisted(target)));
        EdgeContributor bidirectional = contributor(1, source, target, true, true, endpoint(source),
            endpoint(SourceNodeKey.persisted(target)));

        assertThat(DirectionCoverage.covers(Arrays.asList(directed, bidirectional), reference("source"), target,
            RelationshipDirection.UNDIRECTED)).isFalse();
    }

    @Test
    public void ignoreTransientContributorSources() {
        SourceNodeKey transientSource = SourceNodeKey.transientPath(MAP, Arrays.asList(0, 1));
        NodeReference target = reference("target");
        EdgeContributor contributor = contributor(0, transientSource, target, false, true,
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(transientSource)),
            endpoint(SourceNodeKey.persisted(target)));

        assertThat(contributor.sourceReference()).isEmpty();
        assertThat(DirectionCoverage.covers(Collections.singletonList(contributor), reference("source"), target,
            RelationshipDirection.FORWARD)).isFalse();
    }

    @Test
    public void requireExactPairsEvenWhenDifferentPairsCollapseToOneProjectedPair() {
        SourceNodeKey firstSource = source("first-source");
        SourceNodeKey firstTarget = source("first-target");
        SourceNodeKey secondSource = source("second-source");
        SourceNodeKey secondTarget = source("second-target");
        ProjectedEndpointKey collapsedSource = ProjectedEndpointKey.ofNode(
            ProjectedNodeKey.of(source("collapsed-source")));
        ProjectedEndpointKey collapsedTarget = ProjectedEndpointKey.ofNode(
            ProjectedNodeKey.of(source("collapsed-target")));
        EdgeContributor first = contributor(0, firstSource, reference("first-target"), false, true,
            collapsedSource, collapsedTarget);
        EdgeContributor second = contributor(1, secondSource, reference("second-target"), true, true,
            collapsedSource, collapsedTarget);

        assertThat(DirectionCoverage.covers(Arrays.asList(first, second), reference("first-source"),
            reference("first-target"), RelationshipDirection.FORWARD)).isTrue();
        assertThat(DirectionCoverage.covers(Arrays.asList(first, second), reference("first-source"),
            reference("second-target"), RelationshipDirection.FORWARD)).isFalse();
        assertThat(DirectionCoverage.covers(Arrays.asList(first, second), reference("second-source"),
            reference("second-target"), RelationshipDirection.BIDIRECTIONAL)).isTrue();
    }

    @Test
    public void rejectNullsAndExactSelfPairs() {
        SourceNodeKey source = source("source");
        NodeReference reference = reference("source");
        EdgeContributor contributor = contributor(0, source, reference("target"), false, true, endpoint(source),
            endpoint(SourceNodeKey.persisted(reference("target"))));

        assertThatThrownBy(() -> DirectionCoverage.covers(Collections.singletonList(contributor), reference, reference,
            RelationshipDirection.FORWARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DirectionCoverage.covers(null, reference, reference("target"),
            RelationshipDirection.FORWARD)).isInstanceOf(NullPointerException.class);
    }

    private static EdgeContributor contributor(int occurrence, SourceNodeKey source, NodeReference target,
            boolean arrowAtSource, boolean arrowAtTarget, ProjectedEndpointKey projectedSource,
            ProjectedEndpointKey projectedTarget) {
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, target, arrowAtSource, arrowAtTarget,
            "source", "middle", "target");
        return EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor), projectedSource,
            projectedTarget);
    }

    private static SourceNodeKey source(String id) {
        return SourceNodeKey.persisted(reference(id));
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }

    private static ProjectedEndpointKey endpoint(SourceNodeKey source) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source));
    }
}

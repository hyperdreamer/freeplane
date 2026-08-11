package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.testmodel.MutableProjectionScenario;
import org.freeplane.plugin.graph.projection.testmodel.MutableProjectionScenario.Operation;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProjectionPureReloadShould {
    private static final long GENERATION = 41L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void projectTheDecodedWorkspaceLikeAnIndependentlyRebuiltLiveInput() throws Exception {
        final List<Operation> shuffledOperations = new ArrayList<Operation>(
            MutableProjectionScenario.commutativeOperations());
        Collections.shuffle(shuffledOperations, new Random(0x1eadbeef20260810L));
        final MutableProjectionScenario scenario = MutableProjectionScenario.create();
        scenario.applyAll(shuffledOperations);
        final WorkspaceDocument liveWorkspace = scenario.workspace();
        final List<MapReferenceId> liveOrder = Arrays.asList(
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_TWO,
            MutableProjectionScenario.MAP_THREE);
        final List<MapReferenceId> liveAvailabilityOrder = Arrays.asList(
            MutableProjectionScenario.MAP_TWO, MutableProjectionScenario.MAP_THREE,
            MutableProjectionScenario.MAP_ONE);
        final List<MapSnapshot> liveSnapshots = scenario.rebuiltSnapshots(liveOrder);
        final GraphProjection liveProjection = project(scenario.inputForWorkspace(GENERATION, liveWorkspace,
            liveSnapshots, liveAvailabilityOrder));

        final WorkspaceXmlCodec codec = new WorkspaceXmlCodec(
            new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList()));
        final Path location = temporaryFolder.newFile("pure-reload.fpg").toPath();
        final byte[] bytes = codec.write(liveWorkspace, location);
        Files.write(location, bytes);
        final WorkspaceDocument decodedWorkspace = codec.read(location);
        assertThat(decodedWorkspace).isEqualTo(liveWorkspace);

        final List<MapReferenceId> coldOrder = Arrays.asList(
            MutableProjectionScenario.MAP_THREE, MutableProjectionScenario.MAP_TWO,
            MutableProjectionScenario.MAP_ONE);
        final List<MapReferenceId> coldAvailabilityOrder = Arrays.asList(
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_THREE,
            MutableProjectionScenario.MAP_TWO);
        final List<MapSnapshot> coldSnapshots = scenario.rebuiltSnapshots(coldOrder);
        assertFreshEquivalentSnapshots(liveSnapshots, coldSnapshots);
        final GraphProjection coldProjection = project(scenario.inputForWorkspace(GENERATION, decodedWorkspace,
            coldSnapshots, coldAvailabilityOrder));

        assertThat(coldProjection).isEqualTo(liveProjection);
        assertThat(ProjectionDiff.between(liveProjection, coldProjection).isEmpty()).isTrue();
        assertConnectorContributors(coldProjection);
        assertRelationshipResolutions(coldProjection);
        assertPins(coldProjection);
    }

    private static void assertFreshEquivalentSnapshots(final List<MapSnapshot> liveSnapshots,
            final List<MapSnapshot> coldSnapshots) {
        assertThat(coldSnapshots).isNotSameAs(liveSnapshots);
        for (final MapReferenceId id : Arrays.asList(MutableProjectionScenario.MAP_ONE,
                MutableProjectionScenario.MAP_TWO, MutableProjectionScenario.MAP_THREE)) {
            final MapSnapshot live = snapshotFor(liveSnapshots, id);
            final MapSnapshot cold = snapshotFor(coldSnapshots, id);
            assertThat(cold).isNotSameAs(live);
            assertThat(cold.root()).isNotSameAs(live.root());
            assertThat(cold).isEqualTo(live);
        }
    }

    private static void assertConnectorContributors(final GraphProjection projection) {
        ProjectedEdge connectorEdge = null;
        for (final ProjectedEdge edge : projection.edges()) {
            for (final EdgeContributor contributor : edge.contributors()) {
                if (contributor.connectorDescriptor().isPresent()) {
                    connectorEdge = edge;
                    break;
                }
            }
            if (connectorEdge != null) {
                break;
            }
        }
        assertThat(connectorEdge).isNotNull();
        assertThat(connectorEdge.contributors()).extracting(EdgeContributor::key)
            .extracting(key -> key.occurrence().getAsInt()).containsExactly(0, 1);
        assertThat(connectorEdge.contributors()).extracting(EdgeContributor::sourceLabel)
            .containsExactly("a-one-source", "a-one-source-two");
        assertThat(connectorEdge.contributors()).extracting(EdgeContributor::middleLabel)
            .containsExactly("connector-zero", "connector-one");
        assertThat(connectorEdge.contributors()).extracting(EdgeContributor::targetLabel)
            .containsExactly("a-two-target", "a-two-target-two");
    }

    private static void assertRelationshipResolutions(final GraphProjection projection) {
        assertThat(projection.relationshipResolutions()).extracting(RelationshipResolution::relationshipId)
            .containsExactly(MutableProjectionScenario.RELATIONSHIP_ONE,
                MutableProjectionScenario.RELATIONSHIP_TWO);
        assertThat(projection.relationshipResolutions()).extracting(RelationshipResolution::status)
            .containsExactly(RelationshipStatus.ACTIVE, RelationshipStatus.ACTIVE);
    }

    private static void assertPins(final GraphProjection projection) {
        assertThat(projection.pins()).extracting(PinProjection::source).containsExactly(
            MutableProjectionScenario.reference(MutableProjectionScenario.MAP_ONE, "a-one"),
            MutableProjectionScenario.reference(MutableProjectionScenario.MAP_TWO, "b-one"));
        assertThat(projection.pins()).extracting(PinProjection::active).containsExactly(true, true);
        assertThat(projection.pins()).extracting(PinProjection::x).containsExactly(1.25, -3.75);
        assertThat(projection.pins()).extracting(PinProjection::y).containsExactly(-2.5, 4.5);
    }

    private static MapSnapshot snapshotFor(final List<MapSnapshot> snapshots, final MapReferenceId id) {
        for (final MapSnapshot snapshot : snapshots) {
            if (snapshot.mapReferenceId().equals(id)) {
                return snapshot;
            }
        }
        throw new AssertionError("Missing snapshot " + id);
    }

    private static GraphProjection project(final org.freeplane.plugin.graph.projection.input.ProjectionInput input) {
        return new ProjectionEngine().project(input);
    }
}

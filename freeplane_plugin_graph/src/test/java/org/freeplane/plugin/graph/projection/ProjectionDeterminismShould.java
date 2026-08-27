package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.projection.testmodel.MutableProjectionScenario;
import org.freeplane.plugin.graph.projection.testmodel.MutableProjectionScenario.Operation;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.junit.Test;

public class ProjectionDeterminismShould {
    private static final long GENERATION = 17L;
    private static final long BASE_SEED = 0x5eed5eed20260810L;
    private static final List<MapReferenceId> SNAPSHOT_ORDER = Collections.unmodifiableList(Arrays.asList(
        MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_THREE,
        MutableProjectionScenario.MAP_TWO));
    private static final List<MapReferenceId> AVAILABILITY_ORDER = Collections.unmodifiableList(Arrays.asList(
        MutableProjectionScenario.MAP_THREE, MutableProjectionScenario.MAP_ONE,
        MutableProjectionScenario.MAP_TWO));

    @Test
    public void commutativeOperationsProduceTheSameProjectionFor64RecordedSeeds() {
        final List<Operation> operations = MutableProjectionScenario.commutativeOperations();
        assertCategoryCoverage(operations);

        final MutableProjectionScenario canonicalScenario = scenarioWith(operations);
        final GraphProjection canonical = project(canonicalScenario.input(GENERATION, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));
        final WorkspaceDocument canonicalWorkspace = canonicalScenario.workspace();
        final List<MapSnapshot> canonicalSnapshots = canonicalScenario.rebuiltSnapshots(SNAPSHOT_ORDER);

        final GraphProjection generationOnly = project(canonicalScenario.input(GENERATION + 1, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));
        assertThat(ProjectionDiff.between(canonical, generationOnly).isEmpty())
            .as("generation-only recomputation")
            .isTrue();

        final Random seedSource = new Random(BASE_SEED);
        for (int index = 0; index < 64; index++) {
            final long seed = seedSource.nextLong();
            final List<Operation> shuffled = new ArrayList<Operation>(operations);
            Collections.shuffle(shuffled, new Random(seed));
            final MutableProjectionScenario candidateScenario = scenarioWith(shuffled);
            final String description = failureDescription(seed, shuffled);

            assertThat(project(candidateScenario.input(GENERATION, SNAPSHOT_ORDER, AVAILABILITY_ORDER)))
                .as(description)
                .isEqualTo(canonical);
            assertThat(candidateScenario.workspace())
                .as(description + " workspace")
                .isEqualTo(canonicalWorkspace);
            assertThat(candidateScenario.rebuiltSnapshots(SNAPSHOT_ORDER))
                .as(description + " rebuilt snapshots")
                .containsExactlyElementsOf(canonicalSnapshots);
        }
    }

    @Test
    public void mapOrderIndependentOfHashInsertion() {
        final List<Operation> operations = MutableProjectionScenario.commutativeOperations();
        assertCategoryCoverage(operations);
        final MutableProjectionScenario firstScenario = scenarioWith(operations);
        final MutableProjectionScenario secondScenario = scenarioWith(operations);
        final List<MapReferenceId> firstSnapshots = Arrays.asList(
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_THREE,
            MutableProjectionScenario.MAP_TWO);
        final List<MapReferenceId> secondSnapshots = Arrays.asList(
            MutableProjectionScenario.MAP_TWO, MutableProjectionScenario.MAP_ONE,
            MutableProjectionScenario.MAP_THREE);
        final List<MapReferenceId> firstAvailability = Arrays.asList(
            MutableProjectionScenario.MAP_THREE, MutableProjectionScenario.MAP_TWO,
            MutableProjectionScenario.MAP_ONE);
        final List<MapReferenceId> secondAvailability = Arrays.asList(
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_TWO,
            MutableProjectionScenario.MAP_THREE);

        final GraphProjection first = project(firstScenario.input(GENERATION, firstSnapshots, firstAvailability));
        final GraphProjection second = project(secondScenario.input(GENERATION, secondSnapshots, secondAvailability));

        assertThat(first).isEqualTo(second);
        assertThat(ProjectionDiff.between(first, second).isEmpty())
            .as("first snapshot/availability insertion order to second insertion order")
            .isTrue();
        assertThat(ProjectionDiff.between(second, first).isEmpty())
            .as("second snapshot/availability insertion order to first insertion order")
            .isTrue();
        assertThat(first.nodes()).isEmpty();
        assertThat(first.enclosures()).extracting(ProjectedEnclosure::mapReferenceId).containsExactly(
            MutableProjectionScenario.MAP_TWO, MutableProjectionScenario.MAP_TWO,
            MutableProjectionScenario.MAP_THREE, MutableProjectionScenario.MAP_THREE,
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_ONE,
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_ONE,
            MutableProjectionScenario.MAP_ONE, MutableProjectionScenario.MAP_ONE);
    }

    @Test
    public void textOnlyRetainsPositionKeys() {
        final List<Operation> operations = MutableProjectionScenario.commutativeOperations();
        assertCategoryCoverage(operations);
        final MutableProjectionScenario beforeScenario = scenarioWith(withoutCategory(operations, "text"));
        final MutableProjectionScenario afterScenario = scenarioWith(operations);
        final GraphProjection before = project(beforeScenario.input(GENERATION, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));
        final GraphProjection after = project(afterScenario.input(GENERATION, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));

        assertThat(label(before, MutableProjectionScenario.A_ONE))
            .isNotEqualTo(label(after, MutableProjectionScenario.A_ONE));
        assertThat(nodeKeys(after)).isEmpty();
        assertThat(nodeKeys(before)).isEmpty();
        assertThat(enclosureKeys(after)).containsExactlyElementsOf(enclosureKeys(before));
        assertThat(edgeKeys(after)).containsExactlyElementsOf(edgeKeys(before));

        final ProjectionDiff diff = ProjectionDiff.between(before, after);
        assertThat(diff.addedNodes()).isEmpty();
        assertThat(diff.removedNodes()).isEmpty();
        assertThat(diff.changedNodes()).isEmpty();
        assertThat(diff.addedEnclosures()).isEmpty();
        assertThat(diff.removedEnclosures()).isEmpty();
        assertThat(diff.changedEnclosures()).containsExactly(EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(MutableProjectionScenario.A_ONE))));
        assertThat(diff.addedEdges()).isEmpty();
        assertThat(diff.removedEdges()).isEmpty();
        assertThat(diff.changedEdges()).isEmpty();
    }

    @Test
    public void reportOnlyTheAffectedStructuralDiff() {
        final List<Operation> operations = MutableProjectionScenario.commutativeOperations();
        assertCategoryCoverage(operations);
        final MutableProjectionScenario beforeScenario = scenarioWith(withoutCategory(operations, "structural"));
        final MutableProjectionScenario afterScenario = scenarioWith(operations);
        final GraphProjection before = project(beforeScenario.input(GENERATION, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));
        final GraphProjection after = project(afterScenario.input(GENERATION, SNAPSHOT_ORDER,
            AVAILABILITY_ORDER));
        final ProjectionDiff diff = ProjectionDiff.between(before, after);

        final EnclosureHullKey addedHull = hull(MutableProjectionScenario.A_NEW);
        final EnclosureHullKey affectedHull = hull(MutableProjectionScenario.A_BRANCH);
        final EnclosureHullKey rootHull = hull(MutableProjectionScenario.A_ROOT);
        final EnclosureHullKey otherHull = hull(MutableProjectionScenario.A_OTHER_BRANCH);

        assertThat(diff.addedNodes()).isEmpty();
        assertThat(diff.removedNodes()).isEmpty();
        assertThat(diff.changedNodes()).isEmpty();
        assertThat(diff.addedEnclosures()).containsExactly(addedHull);
        assertThat(diff.removedEnclosures()).isEmpty();
        assertThat(diff.changedEnclosures()).containsExactly(affectedHull);
        assertThat(diff.addedEdges()).isEmpty();
        assertThat(diff.removedEdges()).isEmpty();
        assertThat(diff.changedEdges()).isEmpty();
        assertThat(diff.addedEnclosures()).doesNotContain(rootHull, otherHull);
        assertThat(diff.removedEnclosures()).doesNotContain(rootHull, otherHull);
        assertThat(diff.changedEnclosures()).doesNotContain(rootHull, otherHull);
        assertThat(edgeKeys(after)).containsExactlyElementsOf(edgeKeys(before));
    }

    private static void assertCategoryCoverage(final List<Operation> operations) {
        final Set<String> categories = new HashSet<String>();
        for (final Operation operation : operations) {
            categories.add(operation.category());
        }
        assertThat(categories).contains("map", "structural", "group", "connector", "relationship", "pin", "text");
    }

    private static MutableProjectionScenario scenarioWith(final List<Operation> operations) {
        final MutableProjectionScenario scenario = MutableProjectionScenario.create();
        scenario.applyAll(operations);
        return scenario;
    }

    private static List<Operation> withoutCategory(final List<Operation> operations, final String category) {
        final List<Operation> result = new ArrayList<Operation>();
        for (final Operation operation : operations) {
            if (!category.equals(operation.category())) {
                result.add(operation);
            }
        }
        return result;
    }

    private static GraphProjection project(final ProjectionInput input) {
        return new ProjectionEngine().project(input);
    }

    private static SafeNodeLabel label(final GraphProjection projection, final SourceNodeKey source) {
        final EnclosureKey key = EnclosureKey.of(source);
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final int index = enclosure.endpointKeys().indexOf(key);
            if (index >= 0) {
                return enclosure.labels().get(index);
            }
        }
        throw new AssertionError("Missing projected enclosure " + source);
    }

    private static List<ProjectedNodeKey> nodeKeys(final GraphProjection projection) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            keys.add(node.key());
        }
        return keys;
    }

    private static List<EnclosureHullKey> enclosureKeys(final GraphProjection projection) {
        final List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            keys.add(enclosure.hullKey());
        }
        return keys;
    }

    private static List<ProjectedEdgeKey> edgeKeys(final GraphProjection projection) {
        final List<ProjectedEdgeKey> keys = new ArrayList<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : projection.edges()) {
            keys.add(edge.key());
        }
        return keys;
    }

    private static EnclosureHullKey hull(final SourceNodeKey source) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source)));
    }

    private static String failureDescription(final long seed, final List<Operation> operations) {
        final List<String> descriptions = new ArrayList<String>();
        for (final Operation operation : operations) {
            descriptions.add(operation.category() + ":" + operation.description());
        }
        return String.format("seed=%d (0x%x), operations=%s", Long.valueOf(seed), Long.valueOf(seed), descriptions);
    }
}

package org.freeplane.plugin.graph.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Assume;
import org.junit.Test;

public class RealWorkspaceCrossingShould {
    @Test
    public void replaysTheReportedWorkspaceWithoutClearanceViolations() throws Exception {
        final String configuredRoot = System.getenv("GRAPH_REPORTED_MAP_ROOT");
        Assume.assumeTrue("External maps require explicit GRAPH_REPORTED_MAP_ROOT",
            configuredRoot != null);
        final Path mathRoot = Paths.get(configuredRoot.isEmpty()
            ? "/home/henry/Documents/999.notebooks/01.math" : configuredRoot);
        for (final String relative : Arrays.asList(
                "00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm",
                "04.abstract/01.topology/01.general_topology/mindmap/Point-set_Topology.mm",
                "01.analysis/03.real_analysis/mindmap/Real_Anaylsis.mm")) {
            assertThat(Files.isRegularFile(mathRoot.resolve(relative)))
                .as("required external map %s", mathRoot.resolve(relative)).isTrue();
        }
        final Class<?> probe = Class.forName(
            "org.freeplane.plugin.graph.probe.RealWorkspaceCrossingProbe");
        final Method replay = probe.getDeclaredMethod("replay", Path.class);
        replay.setAccessible(true);
        final Object evidence = replay.invoke(null, mathRoot);
        assertPublishedPathsClear(evidence);
        final int[][] counts = (int[][]) field(evidence, "sourceCounts");
        assertThat(counts.length).isEqualTo(3);
        assertThat(counts[0]).containsExactly(7455, 171, 1445, 10);
        assertThat(counts[1]).containsExactly(9025, 247, 1229, 11);
        assertThat(counts[2]).containsExactly(9834, 150, 1474, 7);
        assertThat(field(evidence, "availableMaps")).isEqualTo(3);
        assertThat(field(evidence, "relationshipCount")).isEqualTo(0);
        assertThat(field(evidence, "workspaceId")).isEqualTo(WorkspaceId.of(
            UUID.nameUUIDFromBytes("real-workspace-probe".getBytes(StandardCharsets.UTF_8))));

        final GraphProjection projection = (GraphProjection) field(evidence, "projection");
        assertThat(projection.generation()).isEqualTo(1L);
        assertThat(projection.nodes()).hasSize(28);
        assertThat(projection.edges()).hasSize(12);
        assertThat(projection.enclosures()).hasSize(7);
        assertThat(projection.pins()).isEmpty();
        for (final ProjectedNode node : projection.nodes()) {
            assertThat(node.graphGroup()).as("graph-group disc %s", node.key()).isTrue();
        }
        assertThat(field(evidence, "steps")).isEqualTo(178);
        assertThat(field(evidence, "idle")).isEqualTo(true);
        assertThat(field(evidence, "residualViolations")).isEqualTo(1);
        final LayoutFrame frame = (LayoutFrame) field(evidence, "settledFrame");
        assertThat(frame.failed()).isFalse();
        assertThat(field(evidence, "settledPositions")).isSameAs(frame.positions());
        assertThat(field(evidence, "correction")).isSameAs(frame.edgeConflictDiagnostics());
        final GraphGeometry geometry = (GraphGeometry) field(evidence, "routedGeometry");
        assertThat(field(evidence, "settledGeometry")).isSameAs(geometry);
        assertThat(geometry.nodes()).hasSize(28);

        final EdgeConflictDiagnostics correction = frame.edgeConflictDiagnostics();
        assertThat(correction.conflictsDetected()).isEqualTo(1);
        assertThat(correction.crossingsDetected()).isZero();
        assertThat(correction.edgeOverNodeDetected()).isEqualTo(1);
        assertThat(correction.crossingsResolved()).isZero();
        assertThat(correction.edgeOverNodeResolved()).isEqualTo(1);
        assertThat(correction.conflictsResidual()).isEmpty();
        assertThat(correction.rounds()).isEqualTo(1);
        assertThat(correction.verifications()).isEqualTo(1);
        assertThat(correction.verificationWork()).isEqualTo(1207L);
        assertThat(correction.indexInsertedItems()).isEqualTo(300L);
        assertThat(correction.indexPairChecks()).isEqualTo(84L);
        assertThat(correction.indexSubdivisions()).isZero();
        assertThat(correction.verificationBudgetExhausted()).isFalse();
        assertThat(correction.indexBudgetExhausted()).isFalse();
        assertThat(correction.detectionTruncated()).isFalse();
        assertThat(correction.repairsApplied()).hasSize(1);
        final EdgeConflictDiagnostics.Repair repair = correction.repairsApplied().get(0);
        assertThat(repair.kind().name()).isEqualTo("TRANSLATION");
        assertThat(repair.batchIndex()).isZero();
        assertThat(repair.nodeKeys()).hasSize(1);
        final ProjectedNodeKey key = repair.nodeKeys().get(0);
        assertThat(key.source().persistedReference().get().nodeId().toString())
            .isEqualTo("ID_1732260180");
        assertThat(repair.fromCentres()).containsOnlyKeys(key);
        assertThat(repair.toCentres()).containsOnlyKeys(key);
        final LayoutPoint from = repair.fromCentres().get(key);
        final LayoutPoint to = repair.toCentres().get(key);
        final double dx = to.x() - from.x();
        final double dy = to.y() - from.y();
        assertThat(String.format(Locale.ROOT, "%.3f,%.3f;%.3f", dx, dy, Math.hypot(dx, dy)))
            .as("rounded display only; full precision is retained in repair maps")
            .isEqualTo("1.177,-3.016;3.238");
        assertThat(((LayoutPositions) field(evidence, "settledPositions")).nodes().get(key))
            .isEqualTo(to);
        assertThat(geometry.nodes().get(key).center()).isEqualTo(to);

        final EdgePaths paths = (EdgePaths) field(evidence, "paths");
        assertThat(paths.diagnostics().edgesStraight()).isEqualTo(12);
        assertThat(paths.diagnostics().edgesBow()).isZero();
        assertThat(paths.diagnostics().edgesDogleg()).isZero();
        assertThat(paths.diagnostics().edgesFallback()).isZero();
        assertThat(paths.diagnostics().routeBudgetExhausted()).isFalse();
        assertThat(paths.diagnostics().detectionTruncated()).isFalse();
        assertThat(paths.diagnostics().routeObstacleChecks()).isEqualTo(8L);
        assertThat(paths.diagnostics().routePairChecks()).isZero();
        assertThat(paths.diagnostics().routeIndexItems()).isEqualTo(2156L);
        assertThat(paths.byEdge()).hasSize(12);
        final List<String> identities = new ArrayList<String>();
        for (final ProjectedEdge edge : projection.edges()) {
            identities.add(endpoint(projection, edge.first()) + " -> "
                + endpoint(projection, edge.second()));
            assertThat(paths.path(edge.key()).isPresent()).isTrue();
        }
        assertThat(identities).containsExactly(
            "1/ID_1133378501 -> 1/ID_1387156674",
            "1/ID_1133378501 -> 1/ID_1901523076",
            "1/ID_1222855984 -> 1/ID_297617399",
            "1/ID_1887656421 -> 1/ID_215702338",
            "2/ID_192579155 -> 2/ID_675241169",
            "2/ID_626846666 -> 2/ID_845954544",
            "3/ID_1165913806 -> 3/ID_309040602",
            "3/ID_1437543342 -> 3/ID_1763143138",
            "3/ID_1437543342 -> 3/ID_309040602",
            "3/ID_1721568649 -> 3/ID_295118693",
            "3/ID_1763143138 -> 3/ID_309040602",
            "3/ID_1763143138 -> 3/ID_792798100");
        assertThat(((Integer) field(evidence, "auditedDiscPairs")).intValue())
            .as("every non-attached disc audited").isEqualTo(12 * (28 - 2));
    }

    private static void assertPublishedPathsClear(final Object evidence) throws Exception {
        assertThat(field(evidence, "auditedEdges")).as("every published edge audited")
            .isEqualTo(12);
        assertThat((List<?>) field(evidence, "clearanceViolations"))
            .as("published paths must clear every non-attached disc by 3 wu").isEmpty();
    }

    private static Object field(final Object evidence, final String name) throws Exception {
        final Field field = evidence.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(evidence);
    }

    private static String endpoint(final GraphProjection projection,
            final ProjectedEndpointKey endpoint) {
        for (final ProjectedNode node : projection.nodes()) {
            if (ProjectedEndpointKey.ofNode(node.key()).equals(endpoint)) {
                final String map = node.key().source().persistedReference().get()
                    .mapReferenceId().toString();
                return Integer.parseInt(map.substring(map.lastIndexOf('-') + 1)) + "/"
                    + node.key().source().persistedReference().get().nodeId();
            }
        }
        throw new AssertionError("Expected a persisted node endpoint: " + endpoint);
    }
}

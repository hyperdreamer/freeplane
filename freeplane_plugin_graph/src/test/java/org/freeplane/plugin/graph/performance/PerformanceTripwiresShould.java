package org.freeplane.plugin.graph.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PerformanceTripwiresShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void computeNearestRankWithoutMutatingTheInput() {
        List<Long> values = new ArrayList<Long>(Arrays.asList(1L, 2L, 4L, 8L));
        assertThat(NearestRankPercentile.of(values, 0.50)).isEqualTo(2L);
        assertThat(NearestRankPercentile.of(values, 0.95)).isEqualTo(8L);
        assertThat(values).containsExactly(1L, 2L, 4L, 8L);
    }

    @Test
    public void rejectInvalidPercentileSamples() {
        assertThatThrownBy(() -> NearestRankPercentile.of(Collections.<Long>emptyList(), 0.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NearestRankPercentile.of(Arrays.asList(1L, 0L), 0.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NearestRankPercentile.of(Arrays.asList(1L), 0.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NearestRankPercentile.of(Arrays.asList(1L), Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void exposeTheCanonicalStageOrderAndThresholds() {
        assertThat(PerformanceMeasurements.Stage.names()).containsExactly(
            "snapshot", "projection", "diff", "mutation", "force", "correction",
            "hull", "label", "full-worker", "edt-swap", "repaint", "accepted-batch-first-frame");
        assertThat(PerformanceMeasurements.normalThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.FORCE)).isEqualTo(250_000_000L);
        assertThat(PerformanceMeasurements.strictThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.FORCE)).isEqualTo(50_000_000L);
        assertThat(PerformanceMeasurements.strictThresholdNanos("two-map",
            PerformanceMeasurements.Stage.FORCE)).isEqualTo(-1L);
        assertThat(PerformanceMeasurements.NORMAL_FIRST_FRAME_P99_NANOS)
            .isEqualTo(1_500_000_000L);
        assertThat(PerformanceMeasurements.strictP99ThresholdNanos("reference-2000-5000",
            PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME)).isEqualTo(300_000_000L);
    }

    @Test
    public void serializeCompleteRowsInStableCsvOrder() {
        PerformanceMeasurements measurements = new PerformanceMeasurements("test", 1, 3);
        for (PerformanceMeasurements.Stage stage : PerformanceMeasurements.Stage.values()) {
            measurements.recordWarmup(stage, 10L);
            measurements.recordMeasured(stage, 20L);
            measurements.recordMeasured(stage, 30L);
            measurements.recordMeasured(stage, 40L);
        }
        String csv = measurements.toCsv();
        String[] lines = csv.split("\\n");
        assertThat(lines[0]).isEqualTo(
            "scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,"
                + "normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass");
        assertThat(lines).hasSize(PerformanceMeasurements.Stage.values().length + 1);
        assertThat(lines[1]).startsWith("test,snapshot,1,3,30,40,40,40,");
    }

    @Test
    public void writeOnlyTheThreeDeterministicFixtureFiles() throws Exception {
        java.nio.file.Path output = temporaryFolder.newFolder("fixtures").toPath();
        GeneratedWorkspace.writeFixtures(output);
        List<String> names = new ArrayList<String>();
        try (java.nio.file.DirectoryStream<java.nio.file.Path> files =
                java.nio.file.Files.newDirectoryStream(output)) {
            for (java.nio.file.Path file : files) {
                names.add(file.getFileName().toString());
            }
        }
        assertThat(names).containsExactlyInAnyOrder("two-map.fpg", "three-map.fpg",
            "reference-2000-5000.fpg");
        Map<String, byte[]> first = new java.util.LinkedHashMap<String, byte[]>();
        for (String name : names) {
            first.put(name, java.nio.file.Files.readAllBytes(output.resolve(name)));
        }
        GeneratedWorkspace.writeFixtures(output);
        for (String name : names) {
            assertThat(java.nio.file.Files.readAllBytes(output.resolve(name))).isEqualTo(first.get(name));
        }
    }

    @Test
    public void generateTheExactVariantMatrix() {
        for (GeneratedWorkspace.Scenario scenario : GeneratedWorkspace.Scenario.values()) {
            GeneratedWorkspace workspace = GeneratedWorkspace.forScenario(scenario);
            assertThat(workspace.mapCount()).isEqualTo(scenario.mapCount());
            assertThat(workspace.nodeCount()).isEqualTo(sum(scenario.nodesByMap()));
            assertThat(workspace.enclosureCount()).isEqualTo(sum(scenario.enclosuresByMap()));
            assertThat(workspace.nativeContributorCount()).isEqualTo(sum(scenario.nativeContributorsByMap()));
            assertThat(workspace.relationshipCount()).isEqualTo(
                scenario == GeneratedWorkspace.Scenario.SKEWED_REFERENCE
                    || scenario == GeneratedWorkspace.Scenario.REFERENCE_2000_5000 ? 1500
                    : scenario == GeneratedWorkspace.Scenario.TWO_MAP ? 120
                    : 180);
        }
    }

    @Test
    public void permitOnlyTheDocumentedEmptyBucketsOnTheSkewedFinalMap() {
        int[] skewedFinalMapLeafCounts = directLeafCounts(24);
        skewedFinalMapLeafCounts[22] = 0;
        skewedFinalMapLeafCounts[23] = 0;

        GeneratedWorkspace.assertDirectLeafAllocationContract(
            GeneratedWorkspace.Scenario.SKEWED_REFERENCE, 19, skewedFinalMapLeafCounts);

        int[] unexpectedSkewedLeafCounts = skewedFinalMapLeafCounts.clone();
        unexpectedSkewedLeafCounts[21] = 0;
        assertThatThrownBy(() -> GeneratedWorkspace.assertDirectLeafAllocationContract(
            GeneratedWorkspace.Scenario.SKEWED_REFERENCE, 19, unexpectedSkewedLeafCounts))
            .isInstanceOf(IllegalStateException.class);

        int[] unexpectedRegularLeafCounts = directLeafCounts(20);
        unexpectedRegularLeafCounts[0] = 0;
        assertThatThrownBy(() -> GeneratedWorkspace.assertDirectLeafAllocationContract(
            GeneratedWorkspace.Scenario.TWO_MAP, 0, unexpectedRegularLeafCounts))
            .isInstanceOf(IllegalStateException.class);

        GeneratedWorkspace workspace = GeneratedWorkspace.forScenario(
            GeneratedWorkspace.Scenario.SKEWED_REFERENCE);
        assertThat(emptyEnclosureBuckets(workspace.snapshots().get(19).root())).containsExactly(22, 23);
        for (int mapIndex = 0; mapIndex < 19; mapIndex++) {
            assertThat(emptyEnclosureBuckets(workspace.snapshots().get(mapIndex).root())).isEmpty();
        }
    }

    private static int[] directLeafCounts(int enclosureCount) {
        int[] result = new int[enclosureCount];
        Arrays.fill(result, 1);
        return result;
    }

    private static List<Integer> emptyEnclosureBuckets(NodeSnapshot root) {
        List<Integer> result = new ArrayList<Integer>();
        for (int childIndex = 0; childIndex < root.children().size(); childIndex++) {
            NodeSnapshot child = root.children().get(childIndex);
            if (!child.structuralLeaf() && child.children().isEmpty()) {
                result.add(Integer.valueOf(child.key().structuralPath().get(0).intValue()));
            }
        }
        return result;
    }

    private static int sum(int[] values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }

    @Test
    public void generateTheReferenceStructuralCounts() {
        GeneratedWorkspace workspace = GeneratedWorkspace.forScenario("reference-2000-5000");
        assertThat(workspace.mapCount()).isEqualTo(20);
        assertThat(workspace.nodeCount()).isEqualTo(2000);
        assertThat(workspace.enclosureCount()).isEqualTo(1200);
        assertThat(workspace.nativeContributorCount()).isEqualTo(3500);
        assertThat(workspace.relationshipCount()).isEqualTo(1500);
        assertThat(workspace.containmentLinkCount()).isEqualTo(2000);
        assertThat(workspace.hierarchyLinkCount()).isEqualTo(1180);
        assertThat(workspace.particleCount()).isEqualTo(3200);
        assertThat(workspace.springCount()).isEqualTo(8180);
    }
}

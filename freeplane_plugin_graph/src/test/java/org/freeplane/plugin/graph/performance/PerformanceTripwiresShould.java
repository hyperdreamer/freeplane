package org.freeplane.plugin.graph.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.NanoClock;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
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
    public void serializeCorrectedVisibleLeafLabelsIntoFixtureBytes() throws Exception {
        java.nio.file.Path output = temporaryFolder.newFolder("label-fixtures").toPath();
        GeneratedWorkspace.writeFixtures(output);
        Map<String, List<String>> expectedLabels = new LinkedHashMap<String, List<String>>();
        expectedLabels.put("two-map.fpg", Arrays.asList(
            "node-full-m00-n0001", "node-m00-n0001", "node-full-m01-n0060", "node-m01-n0060"));
        expectedLabels.put("three-map.fpg", Arrays.asList(
            "node-full-m00-n0001", "node-m00-n0001", "node-full-m02-n0060", "node-m02-n0060"));
        expectedLabels.put("reference-2000-5000.fpg", Arrays.asList(
            "node-full-m00-n0001", "node-m00-n0001", "node-full-m19-n0100", "node-m19-n0100"));
        Map<String, String> historicalHashes = new LinkedHashMap<String, String>();
        historicalHashes.put("two-map.fpg",
            "c66acb490c564a8cc8203a2742a193e4c81421b688a4b13b5168d24cc44ce5ad");
        historicalHashes.put("three-map.fpg",
            "9939eb26768c2be69bd378a97e9afd0af3a455bac767cb9acc2f29754b8a4202");
        historicalHashes.put("reference-2000-5000.fpg",
            "366a7bbe316b9f11b974730f2f063821ddb0d6ed3cf0f1fc6ee67e92766a691c");

        for (Map.Entry<String, List<String>> fixture : expectedLabels.entrySet()) {
            byte[] bytes = java.nio.file.Files.readAllBytes(output.resolve(fixture.getKey()));
            String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            for (String label : fixture.getValue()) {
                assertThat(xml).contains(label);
            }
            assertThat(sha256(bytes)).isNotEqualTo(historicalHashes.get(fixture.getKey()));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) {
                result.append('0');
            }
            result.append(hex);
        }
        return result.toString();
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
    public void labelEveryGeneratedProjectedNodeFromItsPersistedIdentifier() {
        for (GeneratedWorkspace.Scenario scenario : GeneratedWorkspace.Scenario.values()) {
            GeneratedWorkspace workspace = GeneratedWorkspace.forScenario(scenario);
            for (ProjectedNode node : workspace.projection().nodes()) {
                NodeReference persistedReference = node.source().persistedReference().get();
                assertThat(node.label().fullText())
                    .isEqualTo("node-full-" + persistedReference.nodeId().value());
                assertThat(node.label().displayText())
                    .isEqualTo("node-" + persistedReference.nodeId().value());
            }
        }
    }

    @Test
    public void acceptOnlyTheExactSkewedMapAllocation() {
        int[] nodesByMap = new int[] {1600, 21, 21, 21, 21, 21, 21, 21, 21, 21,
            21, 21, 21, 21, 21, 21, 21, 21, 21, 22};
        int[] enclosuresByMap = new int[] {960, 12, 12, 12, 12, 12, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 24};

        GeneratedWorkspace.assertSkewedMapAllocationContract(GeneratedWorkspace.Scenario.SKEWED_REFERENCE,
            nodesByMap, enclosuresByMap);

        int[] incompleteNodesByMap = nodesByMap.clone();
        incompleteNodesByMap[0] = 1599;
        assertThatThrownBy(() -> GeneratedWorkspace.assertSkewedMapAllocationContract(
            GeneratedWorkspace.Scenario.SKEWED_REFERENCE, incompleteNodesByMap, enclosuresByMap))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> GeneratedWorkspace.assertSkewedMapAllocationContract(
            GeneratedWorkspace.Scenario.TWO_MAP, nodesByMap, enclosuresByMap))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneratedWorkspace.assertSkewedMapAllocationContract(null, nodesByMap,
            enclosuresByMap)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectFramesWhoseNodeAndAnchorKeysDoNotExactlyCoverTheProjection() throws Exception {
        GraphWorkspacePerformanceDiagnostic diagnostic = diagnostic(new CountingNanoClock());
        MapReferenceId mapId = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        ProjectedNode node = ProjectedNode.of(ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(mapId,
            PersistedNodeId.of("node")))), SafeNodeLabel.of("full", "display"), "map", false);
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(0))))));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, hullKey.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("full", "display")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(node.key()),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.singletonList(enclosure));
        Map<ProjectedNodeKey, LayoutPoint> expectedNodes = Collections.singletonMap(node.key(),
            LayoutPoint.of(0.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> expectedAnchors = Collections.singletonMap(hullKey,
            LayoutPoint.of(0.0, 0.0));

        assertThatThrownBy(() -> diagnostic.validateFrameCoverage(
            frame(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), expectedAnchors), projection,
            "missing node")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing node node coverage differs");
        assertThatThrownBy(() -> diagnostic.validateFrameCoverage(
            frame(expectedNodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), projection,
            "missing anchor")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing anchor anchor coverage differs");

        Map<ProjectedNodeKey, LayoutPoint> extraNodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(
            expectedNodes);
        extraNodes.put(ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(mapId,
            PersistedNodeId.of("extra-node")))), LayoutPoint.of(1.0, 0.0));
        assertThatThrownBy(() -> diagnostic.validateFrameCoverage(frame(extraNodes, expectedAnchors), projection,
            "extra node")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("extra node node coverage differs");

        EnclosureHullKey extraHullKey = EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(1))))));
        Map<EnclosureHullKey, LayoutPoint> extraAnchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>(
            expectedAnchors);
        extraAnchors.put(extraHullKey, LayoutPoint.of(1.0, 0.0));
        assertThatThrownBy(() -> diagnostic.validateFrameCoverage(frame(expectedNodes, extraAnchors), projection,
            "extra anchor")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("extra anchor anchor coverage differs");

        diagnostic.validateFrameCoverage(frame(expectedNodes, expectedAnchors), projection, "exact coverage");

        GraphProjection emptyProjection = GraphProjection.structure(0L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList());
        diagnostic.validateFrameCoverage(frame(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), emptyProjection, "empty coverage");
    }

    @Test
    public void retainTheInjectedClockAndPublishAcceptedFirstFramesAsSettling() throws Exception {
        NanoClock clock = new CountingNanoClock();
        GraphWorkspacePerformanceDiagnostic diagnostic = diagnostic(clock);
        GraphProjection projection = GraphProjection.structure(0L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList());
        LayoutFrame frame = LayoutFrame.of(0L,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false);

        assertThat(diagnostic.clock()).isSameAs(clock);
        CanvasState state = diagnostic.acceptedFirstFrameState(0L, projection, frame,
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()));
        assertThat(state.status()).isEqualTo(OperationalStatus.SETTLING);
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

    private static LayoutFrame frame(Map<ProjectedNodeKey, LayoutPoint> nodes,
            Map<EnclosureHullKey, LayoutPoint> anchors) {
        return LayoutFrame.of(0L, LayoutPositions.of(nodes, anchors), false);
    }

    private GraphWorkspacePerformanceDiagnostic diagnostic(NanoClock clock) throws Exception {
        return new GraphWorkspacePerformanceDiagnostic(temporaryFolder.newFolder("diagnostic").toPath(), false,
            clock);
    }

    private static final class CountingNanoClock implements NanoClock {
        private long value;

        @Override
        public long nanoTime() {
            return value++;
        }
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

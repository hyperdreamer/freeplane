package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class PerceptualIdlePolicyShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final ProjectedNodeKey NODE = ProjectedNodeKey.of(SourceNodeKey.persisted(
        NodeReference.of(MAP, PersistedNodeId.of("node"))));
    private static final EnclosureHullKey HULL = EnclosureHullKey.of(Collections.singletonList(
        EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of("hull"))))));

    @Test
    public void calculateRmsAndMaximumAcrossNodesAndAnchors() {
        LayoutPositions before = positions(0.0, 0.0, 0.0);
        LayoutPositions after = positions(3.0, 4.0, 12.0);

        PerceptualIdlePolicy.IdleMeasurement measurement = new PerceptualIdlePolicy(1, 10.0, 12.0)
            .observe(before, after);

        assertThat(measurement.rms()).isEqualTo(Math.sqrt(169.0 / 2.0));
        assertThat(measurement.max()).isEqualTo(12.0);
        assertThat(measurement.consecutiveStableFrames()).isEqualTo(1);
        assertThat(measurement.idle()).isTrue();
    }

    @Test
    public void resetTheStableStreakWhenANodeOrAnchorKeySetChanges() {
        LayoutPositions before = positions(0.0, 0.0, 0.0);
        LayoutPositions after = positions(0.0, 0.0, 0.0);
        PerceptualIdlePolicy nodePolicy = new PerceptualIdlePolicy(2, 0.1, 0.1);
        LayoutPositions changedNodes = LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), after.anchors());

        assertThat(nodePolicy.observe(before, after).consecutiveStableFrames()).isEqualTo(1);
        assertThat(nodePolicy.observe(after, changedNodes).consecutiveStableFrames()).isZero();

        PerceptualIdlePolicy anchorPolicy = new PerceptualIdlePolicy(2, 0.1, 0.1);
        LayoutPositions changedAnchors = LayoutPositions.of(after.nodes(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        assertThat(anchorPolicy.observe(before, after).consecutiveStableFrames()).isEqualTo(1);
        assertThat(anchorPolicy.observe(after, changedAnchors).consecutiveStableFrames()).isZero();
    }

    @Test
    public void acceptExactSpikeRmsAndMaximumBoundaries() {
        LayoutPositions zero = boundaryPositions(0.0, 0);
        LayoutPositions rmsBoundary = boundaryPositions(0.05, 0);
        PerceptualIdlePolicy rmsPolicy = PerceptualIdlePolicy.spikeDefaults();
        PerceptualIdlePolicy.IdleMeasurement rmsMeasurement = null;
        for (int frame = 0; frame < 8; frame++) {
            rmsMeasurement = rmsPolicy.observe(zero, rmsBoundary);
        }
        assertThat(rmsMeasurement.rms()).isEqualTo(0.05);
        assertThat(rmsMeasurement.max()).isEqualTo(0.05);
        assertThat(rmsMeasurement.consecutiveStableFrames()).isEqualTo(8);
        assertThat(rmsMeasurement.idle()).isTrue();

        LayoutPositions maxBoundary = boundaryPositions(0.10, 8);
        PerceptualIdlePolicy maxPolicy = PerceptualIdlePolicy.spikeDefaults();
        PerceptualIdlePolicy.IdleMeasurement maxMeasurement = null;
        for (int frame = 0; frame < 8; frame++) {
            maxMeasurement = maxPolicy.observe(zeroWithZeros(8), maxBoundary);
        }
        assertThat(maxMeasurement.rms()).isLessThanOrEqualTo(0.05);
        assertThat(maxMeasurement.max()).isEqualTo(0.10);
        assertThat(maxMeasurement.consecutiveStableFrames()).isEqualTo(8);
        assertThat(maxMeasurement.idle()).isTrue();
    }

    @Test
    public void treatTwoMatchingEmptyFramesAsImmediatelyIdle() {
        PerceptualIdlePolicy.IdleMeasurement measurement = PerceptualIdlePolicy.spikeDefaults()
            .observe(LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
                LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                    Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));

        assertThat(measurement.idle()).isTrue();
        assertThat(measurement.consecutiveStableFrames()).isEqualTo(8);
        assertThat(measurement.rms()).isZero();
        assertThat(measurement.max()).isZero();
    }

    @Test
    public void rejectInvalidThresholds() {
        assertThatThrownBy(() -> new PerceptualIdlePolicy(0, 0.1, 0.1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerceptualIdlePolicy(1, Double.NaN, 0.1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerceptualIdlePolicy(1, 0.1, -0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static LayoutPositions boundaryPositions(double moved, int zeroCount) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE, LayoutPoint.of(moved, 0.0));
        for (int index = 0; index < zeroCount; index++) {
            nodes.put(nodeKey("zero-" + index), LayoutPoint.of(0.0, 0.0));
        }
        return LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    }

    private static LayoutPositions zeroWithZeros(int zeroCount) {
        return boundaryPositions(0.0, zeroCount);
    }

    private static ProjectedNodeKey nodeKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(id))));
    }
    private static LayoutPositions positions(double nodeX, double nodeY, double anchorY) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE, LayoutPoint.of(nodeX, nodeY));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(HULL, LayoutPoint.of(0.0, anchorY));
        return LayoutPositions.of(nodes, anchors);
    }
}

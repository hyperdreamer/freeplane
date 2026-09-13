package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class BoundarySeparationDiagnosticsShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void reportVerificationCoverageAndCompactness() {
        final EnclosureHullKey first = hull(MAP_ONE, "a");
        final EnclosureHullKey second = hull(MAP_ONE, "b");
        final BoundaryConflict conflict = new BoundaryConflict(first, second,
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.ROUND_LIMIT,
            Collections.emptyList());
        // The largest vector (magnitude 5.0) is deliberately neither first nor last.
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
        field.put("a:m:" + MAP_TWO.value() + "|p:h", LayoutPoint.of(2.0, 0.0));
        field.put("n:m:" + MAP_ONE.value() + "|p:a", LayoutPoint.of(3.0, 4.0));
        field.put("a:m:" + MAP_TWO.value() + "|p:g", LayoutPoint.of(0.0, 1.0));
        final BoundarySeparationDiagnostics diagnostics = new BoundarySeparationDiagnostics(
            Collections.singletonList(conflict), Collections.singletonList(conflict.pairKey()),
            2, 1, 3, 5.0, 5.0, field, 0.0, 0.0);

        assertThat(diagnostics.hullViolationsDetected()).isEqualTo(2);
        assertThat(diagnostics.hullResidualViolations()).isEqualTo(1);
        assertThat(diagnostics.rounds()).isEqualTo(3);
        assertThat(diagnostics.displacementRms()).isEqualTo(5.0);
        assertThat(diagnostics.displacementMax()).isEqualTo(5.0);
        assertThat(diagnostics.boundaryVerified()).isFalse();
        assertThat(diagnostics.boundaryCovered()).isTrue();
        assertThat(diagnostics.worstMapDisplacement()).isEqualTo(5.0);
        assertThat(diagnostics.appliedDisplacements()).isEqualTo(field);
        assertThat(diagnostics.conflicts()).containsExactly(conflict);
        assertThat(diagnostics.residualHullPairs()).containsExactly(conflict.pairKey());
    }

    @Test
    public void boundaryCoverageRequiresExactConflictAndResidualPairSets() {
        final BoundaryConflict first = conflict(MAP_ONE, "a", "b");
        final BoundaryConflict second = conflict(MAP_ONE, "a", "c");
        final Map<String, LayoutPoint> noDisplacements = Collections.emptyMap();
        assertThat(first.pairKey()).isNotEqualTo(second.pairKey());

        final BoundarySeparationDiagnostics missingResiduals = new BoundarySeparationDiagnostics(
            Arrays.asList(first, second), Collections.singletonList(first.pairKey()),
            2, 1, 1, 0.0, 0.0, noDisplacements, 0.0, 0.0);
        final BoundarySeparationDiagnostics extraResiduals = new BoundarySeparationDiagnostics(
            Collections.singletonList(first), Arrays.asList(first.pairKey(), second.pairKey()),
            2, 1, 1, 0.0, 0.0, noDisplacements, 0.0, 0.0);

        assertThat(missingResiduals.boundaryCovered()).isFalse();
        assertThat(extraResiduals.boundaryCovered()).isFalse();
    }

    @Test
    public void verifiedDiagnosticsAreVacuouslyCoveredAndEmptyIsZeroed() {
        final BoundarySeparationDiagnostics empty = BoundarySeparationDiagnostics.empty();

        assertThat(empty.boundaryVerified()).isTrue();
        assertThat(empty.boundaryCovered()).isTrue();
        assertThat(empty.worstMapDisplacement()).isZero();
        assertThat(empty.appliedDisplacements()).isEmpty();
        assertThat(empty.conflicts()).isEmpty();
        assertThat(empty.residualHullPairs()).isEmpty();
        assertThat(empty.withDeltas(1.5, 2.5).deltaRms()).isEqualTo(1.5);
        assertThat(empty.withDeltas(1.5, 2.5).deltaMax()).isEqualTo(2.5);
        assertThat(empty.deltaRms()).isZero();
        assertThat(empty.deltaMax()).isZero();
    }

    @Test
    public void rejectInconsistentInputs() {
        final Map<String, LayoutPoint> zeroVector = new LinkedHashMap<String, LayoutPoint>();
        zeroVector.put("n:m:" + MAP_ONE.value() + "|p:a", LayoutPoint.of(0.0, 0.0));

        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), 0, 0, 0, 0.0, 0.0, zeroVector, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), -1, 0, 0, 0.0, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), 0, 0, 0, Double.NaN, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BoundaryConflict conflict(MapReferenceId map, String firstId, String secondId) {
        return new BoundaryConflict(hull(map, firstId), hull(map, secondId),
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.ROUND_LIMIT,
            Collections.emptyList());
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }
}

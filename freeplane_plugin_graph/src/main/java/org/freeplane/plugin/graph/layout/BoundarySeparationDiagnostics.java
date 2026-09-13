package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundarySeparationDiagnostics {
    private final List<BoundaryConflict> conflicts;
    private final List<String> residualHullPairs;
    private final int hullViolationsDetected;
    private final int hullResidualViolations;
    private final int rounds;
    private final double displacementRms;
    private final double displacementMax;
    private final Map<String, LayoutPoint> appliedDisplacements;
    private final double deltaRms;
    private final double deltaMax;

    public BoundarySeparationDiagnostics(final List<BoundaryConflict> conflicts,
            final List<String> residualHullPairs, final int hullViolationsDetected,
            final int hullResidualViolations, final int rounds, final double displacementRms,
            final double displacementMax, final Map<String, LayoutPoint> appliedDisplacements,
            final double deltaRms, final double deltaMax) {
        Objects.requireNonNull(conflicts, "conflicts");
        Objects.requireNonNull(residualHullPairs, "residualHullPairs");
        Objects.requireNonNull(appliedDisplacements, "appliedDisplacements");
        if (hullViolationsDetected < 0 || hullResidualViolations < 0 || rounds < 0) {
            throw new IllegalArgumentException("Boundary diagnostics counts must be nonnegative");
        }
        requireNonnegativeFinite(displacementRms, "displacementRms");
        requireNonnegativeFinite(displacementMax, "displacementMax");
        requireNonnegativeFinite(deltaRms, "deltaRms");
        requireNonnegativeFinite(deltaMax, "deltaMax");
        final List<BoundaryConflict> conflictCopy = new ArrayList<BoundaryConflict>(conflicts.size());
        for (final BoundaryConflict conflict : conflicts) {
            conflictCopy.add(Objects.requireNonNull(conflict, "conflicts entry"));
        }
        final List<String> pairCopy = new ArrayList<String>(residualHullPairs.size());
        for (final String pair : residualHullPairs) {
            pairCopy.add(Objects.requireNonNull(pair, "residualHullPairs entry"));
        }
        final Map<String, LayoutPoint> displacementCopy = new LinkedHashMap<String, LayoutPoint>();
        for (final Map.Entry<String, LayoutPoint> entry : appliedDisplacements.entrySet()) {
            final String key = Objects.requireNonNull(entry.getKey(), "appliedDisplacements key");
            final LayoutPoint value = Objects.requireNonNull(entry.getValue(), "appliedDisplacements value");
            if (value.x() == 0.0 && value.y() == 0.0) {
                throw new IllegalArgumentException("Applied displacement keys must carry a non-zero vector: "
                    + key);
            }
            displacementCopy.put(key, value);
        }
        this.conflicts = Collections.unmodifiableList(conflictCopy);
        this.residualHullPairs = Collections.unmodifiableList(pairCopy);
        this.hullViolationsDetected = hullViolationsDetected;
        this.hullResidualViolations = hullResidualViolations;
        this.rounds = rounds;
        this.displacementRms = displacementRms;
        this.displacementMax = displacementMax;
        this.appliedDisplacements = Collections.unmodifiableMap(displacementCopy);
        this.deltaRms = deltaRms;
        this.deltaMax = deltaMax;
    }

    public static BoundarySeparationDiagnostics empty() {
        return new BoundarySeparationDiagnostics(Collections.<BoundaryConflict>emptyList(),
            Collections.<String>emptyList(), 0, 0, 0, 0.0, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0);
    }

    public BoundarySeparationDiagnostics withDeltas(final double deltaRms, final double deltaMax) {
        return new BoundarySeparationDiagnostics(conflicts, residualHullPairs, hullViolationsDetected,
            hullResidualViolations, rounds, displacementRms, displacementMax, appliedDisplacements,
            deltaRms, deltaMax);
    }

    public List<BoundaryConflict> conflicts() {
        return conflicts;
    }

    public List<String> residualHullPairs() {
        return residualHullPairs;
    }

    public int hullViolationsDetected() {
        return hullViolationsDetected;
    }

    public int hullResidualViolations() {
        return hullResidualViolations;
    }

    public int rounds() {
        return rounds;
    }

    public double displacementRms() {
        return displacementRms;
    }

    public double displacementMax() {
        return displacementMax;
    }

    public Map<String, LayoutPoint> appliedDisplacements() {
        return appliedDisplacements;
    }

    public double deltaRms() {
        return deltaRms;
    }

    public double deltaMax() {
        return deltaMax;
    }

    public boolean boundaryVerified() {
        return hullResidualViolations == 0;
    }

    public boolean boundaryCovered() {
        final Set<String> conflictPairs = new LinkedHashSet<String>();
        for (final BoundaryConflict conflict : conflicts) {
            conflictPairs.add(conflict.pairKey());
        }
        return conflictPairs.equals(new LinkedHashSet<String>(residualHullPairs));
    }

    public double worstMapDisplacement() {
        final Map<MapReferenceId, Double> perMap = new LinkedHashMap<MapReferenceId, Double>();
        for (final Map.Entry<String, LayoutPoint> entry : appliedDisplacements.entrySet()) {
            final MapReferenceId map = CanonicalLayoutKeys.mapOfField(entry.getKey());
            final LayoutPoint value = entry.getValue();
            final double magnitude = Math.hypot(value.x(), value.y());
            final Double previous = perMap.get(map);
            if (previous == null || magnitude > previous.doubleValue()) {
                perMap.put(map, Double.valueOf(magnitude));
            }
        }
        double worst = 0.0;
        for (final Double value : perMap.values()) {
            worst = Math.max(worst, value.doubleValue());
        }
        return worst;
    }

    private static void requireNonnegativeFinite(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}

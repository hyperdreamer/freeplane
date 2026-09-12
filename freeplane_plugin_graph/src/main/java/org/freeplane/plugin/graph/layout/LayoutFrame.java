package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class LayoutFrame {
    public static final int UNVERIFIED = -1;

    private final long stepIndex;
    private final LayoutPositions positions;
    private final boolean failed;
    private final int residualViolations;
    private final List<LayoutConflict> conflicts;
    private final PerceptualIdlePolicy.IdleMeasurement idle;

    private LayoutFrame(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations, final List<LayoutConflict> conflicts,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("Layout frame index must be nonnegative");
        }
        if (residualViolations < UNVERIFIED) {
            throw new IllegalArgumentException("Residual violations must be >= UNVERIFIED");
        }
        this.stepIndex = stepIndex;
        this.positions = Objects.requireNonNull(positions, "positions");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");
        this.failed = failed;
        this.residualViolations = residualViolations;
        this.conflicts = copyConflicts(conflicts);
        this.idle = Objects.requireNonNull(idle, "idle");
    }

    /** Engine-internal frames only: the residual is unknown and `LayoutWorker.accept` re-wraps them. */
    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed) {
        return of(stepIndex, positions, failed, UNVERIFIED);
    }

    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations) {
        return new LayoutFrame(stepIndex, positions, failed, residualViolations,
            Collections.<LayoutConflict>emptyList(), PerceptualIdlePolicy.IdleMeasurement.initial());
    }

    public static LayoutFrame withDiagnostics(final LayoutFrame raw, final List<LayoutConflict> conflicts,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        final LayoutFrame value = Objects.requireNonNull(raw, "raw");
        return new LayoutFrame(value.stepIndex, value.positions, value.failed, value.residualViolations,
            conflicts, idle);
    }

    public long stepIndex() {
        return stepIndex;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public boolean failed() {
        return failed;
    }

    public int residualViolations() {
        return residualViolations;
    }

    public boolean verified() {
        return residualViolations >= 0;
    }

    public List<LayoutConflict> conflicts() {
        return conflicts;
    }

    public PerceptualIdlePolicy.IdleMeasurement idle() {
        return idle;
    }

    private static List<LayoutConflict> copyConflicts(final List<LayoutConflict> values) {
        Objects.requireNonNull(values, "conflicts");
        final List<LayoutConflict> copy = new ArrayList<LayoutConflict>(values.size());
        for (final LayoutConflict value : values) {
            copy.add(Objects.requireNonNull(value, "conflicts entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final Map.Entry<K, LayoutPoint> entry : values.entrySet()) {
            final LayoutPoint point = Objects.requireNonNull(entry.getValue(), kind + " position");
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout frame coordinates must be finite");
            }
        }
    }
}

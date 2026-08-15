package org.freeplane.plugin.graph.layout;

import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class LayoutFrame {
    private final long stepIndex;
    private final LayoutPositions positions;
    private final boolean failed;

    private LayoutFrame(final long stepIndex, final LayoutPositions positions, final boolean failed) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("Layout frame index must be nonnegative");
        }
        this.stepIndex = stepIndex;
        this.positions = Objects.requireNonNull(positions, "positions");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");
        this.failed = failed;
    }

    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed) {
        return new LayoutFrame(stepIndex, positions, failed);
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

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final Map.Entry<K, LayoutPoint> entry : values.entrySet()) {
            final LayoutPoint point = Objects.requireNonNull(entry.getValue(), kind + " position");
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout frame coordinates must be finite");
            }
        }
    }
}

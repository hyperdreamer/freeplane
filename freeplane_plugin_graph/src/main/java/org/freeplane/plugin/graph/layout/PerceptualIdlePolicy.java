package org.freeplane.plugin.graph.layout;

import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class PerceptualIdlePolicy {
    private static final int SPIKE_CONSECUTIVE = 8;
    private static final double SPIKE_RMS = 0.02;
    private static final double SPIKE_MAX = 0.05;

    private final int requiredConsecutiveFrames;
    private final double rmsThreshold;
    private final double maxThreshold;
    private KeySet previousKeys;
    private int stableFrames;

    public PerceptualIdlePolicy(final int consecutive, final double rms, final double max) {
        if (consecutive <= 0) {
            throw new IllegalArgumentException("Consecutive stable frames must be positive");
        }
        if (!Double.isFinite(rms) || rms < 0.0) {
            throw new IllegalArgumentException("RMS threshold must be finite and nonnegative");
        }
        if (!Double.isFinite(max) || max < 0.0) {
            throw new IllegalArgumentException("Maximum threshold must be finite and nonnegative");
        }
        requiredConsecutiveFrames = consecutive;
        rmsThreshold = rms;
        maxThreshold = max;
    }

    public static PerceptualIdlePolicy spikeDefaults() {
        return new PerceptualIdlePolicy(SPIKE_CONSECUTIVE, SPIKE_RMS, SPIKE_MAX);
    }

    public IdleMeasurement observe(final LayoutPositions before, final LayoutPositions after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        validateFinite(before);
        validateFinite(after);

        final KeySet beforeKeys = KeySet.of(before);
        final KeySet afterKeys = KeySet.of(after);
        final boolean matchingKeys = beforeKeys.equals(afterKeys);
        final Displacement displacement = displacement(before, after);
        final boolean continuing = previousKeys == null || previousKeys.equals(afterKeys);
        if (matchingKeys && afterKeys.isEmpty()) {
            stableFrames = requiredConsecutiveFrames;
        }
        else if (!matchingKeys || !continuing) {
            stableFrames = 0;
        }
        else if (displacement.rms <= rmsThreshold && displacement.max <= maxThreshold) {
            stableFrames++;
        }
        else {
            stableFrames = 0;
        }
        previousKeys = afterKeys;
        return new IdleMeasurement(displacement.rms, displacement.max, stableFrames,
            matchingKeys && stableFrames >= requiredConsecutiveFrames);
    }

    private static Displacement displacement(final LayoutPositions before, final LayoutPositions after) {
        double sumSquared = 0.0;
        double maximum = 0.0;
        int count = 0;
        for (final org.freeplane.plugin.graph.projection.ProjectedNodeKey key : before.nodes().keySet()) {
            if (after.nodes().containsKey(key)) {
                final double dx = after.nodes().get(key).x() - before.nodes().get(key).x();
                final double dy = after.nodes().get(key).y() - before.nodes().get(key).y();
                final double squared = dx * dx + dy * dy;
                sumSquared += squared;
                maximum = Math.max(maximum, Math.sqrt(squared));
                count++;
            }
        }
        for (final org.freeplane.plugin.graph.projection.EnclosureHullKey key : before.anchors().keySet()) {
            if (after.anchors().containsKey(key)) {
                final double dx = after.anchors().get(key).x() - before.anchors().get(key).x();
                final double dy = after.anchors().get(key).y() - before.anchors().get(key).y();
                final double squared = dx * dx + dy * dy;
                sumSquared += squared;
                maximum = Math.max(maximum, Math.sqrt(squared));
                count++;
            }
        }
        final double rms = count == 0 ? 0.0 : Math.sqrt(sumSquared / count);
        return new Displacement(rms, maximum);
    }

    private static void validateFinite(final LayoutPositions positions) {
        for (final LayoutPoint point : positions.nodes().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Idle positions must be finite");
            }
        }
        for (final LayoutPoint point : positions.anchors().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Idle positions must be finite");
            }
        }
    }

    private static final class Displacement {
        private final double rms;
        private final double max;

        Displacement(final double rms, final double max) {
            this.rms = rms;
            this.max = max;
        }
    }

    private static final class KeySet {
        private final Set<?> nodes;
        private final Set<?> anchors;

        private KeySet(final Set<?> nodes, final Set<?> anchors) {
            this.nodes = nodes;
            this.anchors = anchors;
        }

        static KeySet of(final LayoutPositions positions) {
            return new KeySet(positions.nodes().keySet(), positions.anchors().keySet());
        }

        boolean isEmpty() {
            return nodes.isEmpty() && anchors.isEmpty();
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof KeySet)) {
                return false;
            }
            final KeySet that = (KeySet) other;
            return nodes.equals(that.nodes) && anchors.equals(that.anchors);
        }

        @Override
        public int hashCode() {
            return 31 * nodes.hashCode() + anchors.hashCode();
        }
    }

    public static final class IdleMeasurement {
        private final double rms;
        private final double max;
        private final int consecutiveStableFrames;
        private final boolean idle;

        public IdleMeasurement(final double rms, final double max, final int consecutiveStableFrames,
                final boolean idle) {
            if (!Double.isFinite(rms) || rms < 0.0) {
                throw new IllegalArgumentException("RMS measurement must be finite and nonnegative");
            }
            if (!Double.isFinite(max) || max < 0.0) {
                throw new IllegalArgumentException("Maximum measurement must be finite and nonnegative");
            }
            if (consecutiveStableFrames < 0) {
                throw new IllegalArgumentException("Stable frame count must be nonnegative");
            }
            this.rms = rms;
            this.max = max;
            this.consecutiveStableFrames = consecutiveStableFrames;
            this.idle = idle;
        }

        static IdleMeasurement initial() {
            return new IdleMeasurement(0.0, 0.0, 0, false);
        }

        public double rms() {
            return rms;
        }

        public double max() {
            return max;
        }

        public int consecutiveStableFrames() {
            return consecutiveStableFrames;
        }

        public boolean idle() {
            return idle;
        }
    }
}

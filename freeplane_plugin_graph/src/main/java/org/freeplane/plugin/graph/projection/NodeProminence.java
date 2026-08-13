package org.freeplane.plugin.graph.projection;

import java.util.Objects;

public final class NodeProminence {
    private static final double MAX_SCALE = 1.75;
    private static final double BASE_SCALE = 1.0;
    private static final double SCALE_STEP = 0.20;
    private static final double LOGARITHM_OF_TWO = Math.log(2.0);

    private final int visibleOutgoingTargets;
    private final double scale;

    private NodeProminence(final int visibleOutgoingTargets) {
        if (visibleOutgoingTargets < 0) {
            throw new IllegalArgumentException("Visible outgoing targets must be nonnegative");
        }
        this.visibleOutgoingTargets = visibleOutgoingTargets;
        this.scale = scaleFor(visibleOutgoingTargets);
    }

    public static NodeProminence of(final int visibleOutgoingTargets) {
        return new NodeProminence(visibleOutgoingTargets);
    }

    public int visibleOutgoingTargets() {
        return visibleOutgoingTargets;
    }

    public double scale() {
        return scale;
    }

    private static double scaleFor(final int visibleOutgoingTargets) {
        final double reach = Math.max(1, visibleOutgoingTargets);
        return Math.min(MAX_SCALE, BASE_SCALE + SCALE_STEP * (Math.log(reach) / LOGARITHM_OF_TWO));
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeProminence)) {
            return false;
        }
        final NodeProminence that = (NodeProminence) other;
        return visibleOutgoingTargets == that.visibleOutgoingTargets && scale == that.scale;
    }

    @Override
    public int hashCode() {
        return Objects.hash(visibleOutgoingTargets, scale);
    }

    @Override
    public String toString() {
        return "NodeProminence{" + "visibleOutgoingTargets=" + visibleOutgoingTargets + ", scale=" + scale + '}';
    }
}

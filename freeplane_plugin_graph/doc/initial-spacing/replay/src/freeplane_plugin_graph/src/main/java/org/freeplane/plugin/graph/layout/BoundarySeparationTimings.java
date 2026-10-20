package org.freeplane.plugin.graph.layout;

public final class BoundarySeparationTimings {
    private final long separationNanos;
    private final long hullNanos;
    private final long planNanos;
    private final long applyNanos;

    public BoundarySeparationTimings(final long separationNanos, final long hullNanos, final long planNanos,
            final long applyNanos) {
        if (separationNanos < 0L || hullNanos < 0L || planNanos < 0L || applyNanos < 0L) {
            throw new IllegalArgumentException("Boundary separation timings must be nonnegative");
        }
        this.separationNanos = separationNanos;
        this.hullNanos = hullNanos;
        this.planNanos = planNanos;
        this.applyNanos = applyNanos;
    }

    public long separationNanos() {
        return separationNanos;
    }

    public long hullNanos() {
        return hullNanos;
    }

    public long planNanos() {
        return planNanos;
    }

    public long applyNanos() {
        return applyNanos;
    }
}

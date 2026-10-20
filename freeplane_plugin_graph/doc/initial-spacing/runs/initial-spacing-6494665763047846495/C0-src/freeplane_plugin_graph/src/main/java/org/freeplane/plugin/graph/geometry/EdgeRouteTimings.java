package org.freeplane.plugin.graph.geometry;

/**
 * Immutable route stage timings (spec 2.4). No {@code System.nanoTime()} value enters any
 * diagnostics value; the three stage durations live only here.
 */
public final class EdgeRouteTimings {
    private final long indexNanos;
    private final long selectionNanos;
    private final long residualNanos;

    public EdgeRouteTimings(final long indexNanos, final long selectionNanos, final long residualNanos) {
        requireNonnegative(indexNanos, "indexNanos");
        requireNonnegative(selectionNanos, "selectionNanos");
        requireNonnegative(residualNanos, "residualNanos");
        this.indexNanos = indexNanos;
        this.selectionNanos = selectionNanos;
        this.residualNanos = residualNanos;
    }

    public long indexNanos() {
        return indexNanos;
    }

    public long selectionNanos() {
        return selectionNanos;
    }

    public long residualNanos() {
        return residualNanos;
    }

    public long totalNanos() {
        return indexNanos + selectionNanos + residualNanos;
    }

    private static void requireNonnegative(final long value, final String role) {
        if (value < 0L) {
            throw new IllegalArgumentException(role + " must be nonnegative but was " + value);
        }
    }
}

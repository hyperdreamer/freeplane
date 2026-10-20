package org.freeplane.plugin.graph.layout;

public final class EdgeConflictTimings {
    private final long detectionNanos;
    private final long indexNanos;
    private final long verificationNanos;
    private final long rebuildNanos;

    public EdgeConflictTimings(final long detectionNanos, final long indexNanos, final long verificationNanos,
            final long rebuildNanos) {
        requireNonnegative(detectionNanos, "detectionNanos");
        requireNonnegative(indexNanos, "indexNanos");
        requireNonnegative(verificationNanos, "verificationNanos");
        requireNonnegative(rebuildNanos, "rebuildNanos");
        this.detectionNanos = detectionNanos;
        this.indexNanos = indexNanos;
        this.verificationNanos = verificationNanos;
        this.rebuildNanos = rebuildNanos;
    }

    public long detectionNanos() {
        return detectionNanos;
    }

    public long indexNanos() {
        return indexNanos;
    }

    public long verificationNanos() {
        return verificationNanos;
    }

    public long rebuildNanos() {
        return rebuildNanos;
    }

    public long totalNanos() {
        return detectionNanos + indexNanos + verificationNanos + rebuildNanos;
    }

    private static void requireNonnegative(final long value, final String role) {
        if (value < 0L) {
            throw new IllegalArgumentException(role + " must be nonnegative");
        }
    }
}

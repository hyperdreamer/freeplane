package org.freeplane.plugin.graph.performance;

import java.util.List;
import java.util.Objects;

public final class NearestRankPercentile {
    private NearestRankPercentile() {
    }

    public static long of(final List<Long> sortedNanos, final double percentile) {
        final List<Long> values = Objects.requireNonNull(sortedNanos, "sortedNanos");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Percentile input must not be empty");
        }
        if (!Double.isFinite(percentile) || !(percentile > 0.0) || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be finite and in (0, 1]");
        }

        long previous = -1L;
        for (final Long boxed : values) {
            final long value = Objects.requireNonNull(boxed, "sortedNanos entry").longValue();
            if (value < 0L) {
                throw new IllegalArgumentException("Durations must be nonnegative");
            }
            if (value < previous) {
                throw new IllegalArgumentException("Percentile input must be ascending");
            }
            previous = value;
        }

        final int size = values.size();
        final double rankValue = percentile * (double) size;
        if (!Double.isFinite(rankValue) || !(rankValue > 0.0)
                || rankValue > (double) Long.MAX_VALUE) {
            throw new IllegalArgumentException("Percentile rank overflow");
        }
        final double ceiling = Math.ceil(rankValue);
        if (!Double.isFinite(ceiling) || ceiling < 1.0 || ceiling > (double) size) {
            throw new IllegalArgumentException("Percentile rank is outside the input");
        }
        final long rank = (long) ceiling;
        if (rank < 1L || rank > (long) size) {
            throw new IllegalArgumentException("Percentile rank is outside the input");
        }
        final long index = rank - 1L;
        if (index < 0L || index >= (long) size || index > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Percentile index overflow");
        }
        return values.get((int) index).longValue();
    }
}

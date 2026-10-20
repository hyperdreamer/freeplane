package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The counted result of {@link NodeSeparationProjection#violatingPairsCounted}: the ascending
 * canonical node-pair keys examined before the pair budget was consumed, the number of node
 * pairs examined, and whether the budget stopped the scan early.
 */
public final class CountedViolatingPairs {
    private final List<String> pairs;
    private final long pairEvaluations;
    private final boolean truncated;

    private CountedViolatingPairs(final List<String> pairs, final long pairEvaluations,
            final boolean truncated) {
        this.pairs = freeze(pairs);
        if (pairEvaluations < 0L) {
            throw new IllegalArgumentException("pairEvaluations must be nonnegative");
        }
        this.pairEvaluations = pairEvaluations;
        this.truncated = truncated;
    }

    public static CountedViolatingPairs of(final List<String> pairs, final long pairEvaluations,
            final boolean truncated) {
        return new CountedViolatingPairs(pairs, pairEvaluations, truncated);
    }

    public List<String> pairs() {
        return pairs;
    }

    public long pairEvaluations() {
        return pairEvaluations;
    }

    public boolean truncated() {
        return truncated;
    }

    private static List<String> freeze(final List<String> values) {
        Objects.requireNonNull(values, "pairs");
        final List<String> copy = new ArrayList<String>(values.size());
        for (final String value : values) {
            copy.add(Objects.requireNonNull(value, "pairs entry"));
        }
        return Collections.unmodifiableList(copy);
    }
}

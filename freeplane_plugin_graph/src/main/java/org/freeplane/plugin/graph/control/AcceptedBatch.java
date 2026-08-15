package org.freeplane.plugin.graph.control;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class AcceptedBatch {
    private final long generation;
    private final long acceptedAtNanos;
    private final Set<ChangeKind> kinds;

    public AcceptedBatch(final long generation, final long acceptedAtNanos, final Set<ChangeKind> kinds) {
        if (generation < 0) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        if (acceptedAtNanos < 0) {
            throw new IllegalArgumentException("Acceptance time must be nonnegative");
        }
        Objects.requireNonNull(kinds, "kinds");
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("Kinds must not be empty");
        }
        final EnumSet<ChangeKind> copy = EnumSet.noneOf(ChangeKind.class);
        for (final ChangeKind kind : kinds) {
            copy.add(Objects.requireNonNull(kind, "kind"));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Kinds must not be empty");
        }
        this.generation = generation;
        this.acceptedAtNanos = acceptedAtNanos;
        this.kinds = Collections.unmodifiableSet(copy);
    }

    public long generation() {
        return generation;
    }

    public long acceptedAtNanos() {
        return acceptedAtNanos;
    }

    public Set<ChangeKind> kinds() {
        return kinds;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AcceptedBatch)) {
            return false;
        }
        final AcceptedBatch that = (AcceptedBatch) other;
        return generation == that.generation && acceptedAtNanos == that.acceptedAtNanos
            && kinds.equals(that.kinds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, acceptedAtNanos, kinds);
    }

    @Override
    public String toString() {
        return "AcceptedBatch{" + "generation=" + generation + ", acceptedAtNanos=" + acceptedAtNanos
            + ", kinds=" + kinds + '}';
    }
}

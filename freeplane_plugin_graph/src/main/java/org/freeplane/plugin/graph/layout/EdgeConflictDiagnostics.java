package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class EdgeConflictDiagnostics {
    public enum RepairKind {
        SWAP,
        MIRROR,
        TRANSLATION
    }

    public static final class Repair {
        private final RepairKind kind;
        private final List<ProjectedNodeKey> nodeKeys;
        private final Map<ProjectedNodeKey, LayoutPoint> fromCentres;
        private final Map<ProjectedNodeKey, LayoutPoint> toCentres;
        private final int batchIndex;

        public Repair(final RepairKind kind, final List<ProjectedNodeKey> nodeKeys,
                final Map<ProjectedNodeKey, LayoutPoint> fromCentres,
                final Map<ProjectedNodeKey, LayoutPoint> toCentres, final int batchIndex) {
            this.kind = Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(nodeKeys, "nodeKeys");
            Objects.requireNonNull(fromCentres, "fromCentres");
            Objects.requireNonNull(toCentres, "toCentres");
            if (batchIndex < 0) {
                throw new IllegalArgumentException("batchIndex must be nonnegative");
            }
            final List<ProjectedNodeKey> sorted = new ArrayList<ProjectedNodeKey>(nodeKeys.size());
            for (final ProjectedNodeKey key : nodeKeys) {
                sorted.add(Objects.requireNonNull(key, "nodeKeys entry"));
            }
            Collections.sort(sorted, new Comparator<ProjectedNodeKey>() {
                @Override
                public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                    return CanonicalKeys.node(first).compareTo(CanonicalKeys.node(second));
                }
            });
            requireExactKeys(sorted, fromCentres, "fromCentres");
            requireExactKeys(sorted, toCentres, "toCentres");
            this.nodeKeys = Collections.unmodifiableList(sorted);
            this.fromCentres = freezeCentres(sorted, fromCentres, "fromCentres");
            this.toCentres = freezeCentres(sorted, toCentres, "toCentres");
            this.batchIndex = batchIndex;
        }

        public RepairKind kind() {
            return kind;
        }

        public List<ProjectedNodeKey> nodeKeys() {
            return nodeKeys;
        }

        public Map<ProjectedNodeKey, LayoutPoint> fromCentres() {
            return fromCentres;
        }

        public Map<ProjectedNodeKey, LayoutPoint> toCentres() {
            return toCentres;
        }

        public int batchIndex() {
            return batchIndex;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Repair)) {
                return false;
            }
            final Repair that = (Repair) other;
            return batchIndex == that.batchIndex && kind == that.kind && nodeKeys.equals(that.nodeKeys)
                && fromCentres.equals(that.fromCentres) && toCentres.equals(that.toCentres);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, nodeKeys, fromCentres, toCentres, Integer.valueOf(batchIndex));
        }

        private static void requireExactKeys(final List<ProjectedNodeKey> keys,
                final Map<ProjectedNodeKey, LayoutPoint> centres, final String role) {
            if (!centres.keySet().equals(new LinkedHashSet<ProjectedNodeKey>(keys))
                    || centres.size() != keys.size()) {
                throw new IllegalArgumentException(role + " must contain exactly the node keys");
            }
        }

        private static Map<ProjectedNodeKey, LayoutPoint> freezeCentres(final List<ProjectedNodeKey> keys,
                final Map<ProjectedNodeKey, LayoutPoint> centres, final String role) {
            final Map<ProjectedNodeKey, LayoutPoint> copy = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (final ProjectedNodeKey key : keys) {
                copy.put(key, Objects.requireNonNull(centres.get(key), role + " value"));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    private final int conflictsDetected;
    private final int crossingsDetected;
    private final int edgeOverNodeDetected;
    private final int crossingsResolved;
    private final int edgeOverNodeResolved;
    private final List<String> conflictsResidual;
    private final List<Repair> repairsApplied;
    private final int rounds;
    private final int verifications;
    private final boolean verificationBudgetExhausted;
    private final boolean indexBudgetExhausted;
    private final long indexInsertedItems;
    private final long indexPairChecks;
    private final int indexSubdivisions;

    public EdgeConflictDiagnostics(final int conflictsDetected, final int crossingsDetected,
            final int edgeOverNodeDetected, final int crossingsResolved, final int edgeOverNodeResolved,
            final List<String> conflictsResidual, final List<Repair> repairsApplied, final int rounds,
            final int verifications, final boolean verificationBudgetExhausted,
            final boolean indexBudgetExhausted, final long indexInsertedItems, final long indexPairChecks,
            final int indexSubdivisions) {
        requireNonnegative(conflictsDetected, "conflictsDetected");
        requireNonnegative(crossingsDetected, "crossingsDetected");
        requireNonnegative(edgeOverNodeDetected, "edgeOverNodeDetected");
        requireNonnegative(crossingsResolved, "crossingsResolved");
        requireNonnegative(edgeOverNodeResolved, "edgeOverNodeResolved");
        requireNonnegative(rounds, "rounds");
        requireNonnegative(verifications, "verifications");
        requireNonnegative(indexInsertedItems, "indexInsertedItems");
        requireNonnegative(indexPairChecks, "indexPairChecks");
        requireNonnegative(indexSubdivisions, "indexSubdivisions");
        this.conflictsDetected = conflictsDetected;
        this.crossingsDetected = crossingsDetected;
        this.edgeOverNodeDetected = edgeOverNodeDetected;
        this.crossingsResolved = crossingsResolved;
        this.edgeOverNodeResolved = edgeOverNodeResolved;
        this.conflictsResidual = freezeStrings(conflictsResidual);
        this.repairsApplied = freezeRepairs(repairsApplied);
        this.rounds = rounds;
        this.verifications = verifications;
        this.verificationBudgetExhausted = verificationBudgetExhausted;
        this.indexBudgetExhausted = indexBudgetExhausted;
        this.indexInsertedItems = indexInsertedItems;
        this.indexPairChecks = indexPairChecks;
        this.indexSubdivisions = indexSubdivisions;
    }

    public static EdgeConflictDiagnostics empty() {
        return new EdgeConflictDiagnostics(0, 0, 0, 0, 0, Collections.<String>emptyList(),
            Collections.<Repair>emptyList(), 0, 0, false, false, 0L, 0L, 0);
    }

    public int conflictsDetected() {
        return conflictsDetected;
    }

    public int crossingsDetected() {
        return crossingsDetected;
    }

    public int edgeOverNodeDetected() {
        return edgeOverNodeDetected;
    }

    public int crossingsResolved() {
        return crossingsResolved;
    }

    public int edgeOverNodeResolved() {
        return edgeOverNodeResolved;
    }

    public List<String> conflictsResidual() {
        return conflictsResidual;
    }

    public List<Repair> repairsApplied() {
        return repairsApplied;
    }

    public int rounds() {
        return rounds;
    }

    public int verifications() {
        return verifications;
    }

    public boolean verificationBudgetExhausted() {
        return verificationBudgetExhausted;
    }

    public boolean indexBudgetExhausted() {
        return indexBudgetExhausted;
    }

    public long indexInsertedItems() {
        return indexInsertedItems;
    }

    public long indexPairChecks() {
        return indexPairChecks;
    }

    public int indexSubdivisions() {
        return indexSubdivisions;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EdgeConflictDiagnostics)) {
            return false;
        }
        final EdgeConflictDiagnostics that = (EdgeConflictDiagnostics) other;
        return conflictsDetected == that.conflictsDetected && crossingsDetected == that.crossingsDetected
            && edgeOverNodeDetected == that.edgeOverNodeDetected
            && crossingsResolved == that.crossingsResolved
            && edgeOverNodeResolved == that.edgeOverNodeResolved && rounds == that.rounds
            && verifications == that.verifications
            && verificationBudgetExhausted == that.verificationBudgetExhausted
            && indexBudgetExhausted == that.indexBudgetExhausted
            && indexInsertedItems == that.indexInsertedItems && indexPairChecks == that.indexPairChecks
            && indexSubdivisions == that.indexSubdivisions
            && conflictsResidual.equals(that.conflictsResidual)
            && repairsApplied.equals(that.repairsApplied);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(conflictsDetected), Integer.valueOf(crossingsDetected),
            Integer.valueOf(edgeOverNodeDetected), Integer.valueOf(crossingsResolved),
            Integer.valueOf(edgeOverNodeResolved), conflictsResidual, repairsApplied, Integer.valueOf(rounds),
            Integer.valueOf(verifications), Boolean.valueOf(verificationBudgetExhausted),
            Boolean.valueOf(indexBudgetExhausted), Long.valueOf(indexInsertedItems),
            Long.valueOf(indexPairChecks), Integer.valueOf(indexSubdivisions));
    }

    private static void requireNonnegative(final int value, final String role) {
        if (value < 0) {
            throw new IllegalArgumentException(role + " must be nonnegative");
        }
    }

    private static void requireNonnegative(final long value, final String role) {
        if (value < 0L) {
            throw new IllegalArgumentException(role + " must be nonnegative");
        }
    }

    private static List<String> freezeStrings(final List<String> values) {
        Objects.requireNonNull(values, "conflictsResidual");
        final List<String> copy = new ArrayList<String>(values.size());
        for (final String value : values) {
            copy.add(Objects.requireNonNull(value, "conflictsResidual entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Repair> freezeRepairs(final List<Repair> values) {
        Objects.requireNonNull(values, "repairsApplied");
        final List<Repair> copy = new ArrayList<Repair>(values.size());
        for (final Repair value : values) {
            copy.add(Objects.requireNonNull(value, "repairsApplied entry"));
        }
        return Collections.unmodifiableList(copy);
    }
}

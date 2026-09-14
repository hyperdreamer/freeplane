package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundaryConflict {
    public enum Kind {
        SIBLING_CROSSING,
        SIBLING_CONTAINMENT,
        ANCESTOR_ESCAPE
    }

    public enum Reason {
        STRUCTURAL_ESCAPE,
        IMMOVABLE_SIDES,
        ROUND_LIMIT
    }

    private final EnclosureHullKey firstHull;
    private final EnclosureHullKey secondHull;
    private final Kind kind;
    private final Reason reason;
    private final List<PinProjection> blockingPins;

    public BoundaryConflict(final EnclosureHullKey firstHull, final EnclosureHullKey secondHull, final Kind kind,
            final Reason reason, final List<PinProjection> blockingPins) {
        Objects.requireNonNull(firstHull, "firstHull");
        Objects.requireNonNull(secondHull, "secondHull");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reason = Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(blockingPins, "blockingPins");
        final String firstKey = CanonicalLayoutKeys.hull(firstHull);
        final String secondKey = CanonicalLayoutKeys.hull(secondHull);
        if (firstKey.compareTo(secondKey) <= 0) {
            this.firstHull = firstHull;
            this.secondHull = secondHull;
        }
        else {
            this.firstHull = secondHull;
            this.secondHull = firstHull;
        }
        final List<PinProjection> copy = new ArrayList<PinProjection>(blockingPins.size());
        for (final PinProjection pin : blockingPins) {
            final PinProjection value = Objects.requireNonNull(pin, "blockingPins entry");
            if (!value.active()) {
                throw new IllegalArgumentException("Boundary conflicts can contain active pins only");
            }
            copy.add(value);
        }
        Collections.sort(copy, new Comparator<PinProjection>() {
            @Override
            public int compare(final PinProjection left, final PinProjection right) {
                final int byMap = left.source().mapReferenceId().value().toString()
                    .compareTo(right.source().mapReferenceId().value().toString());
                if (byMap != 0) {
                    return byMap;
                }
                return left.source().nodeId().value().compareTo(right.source().nodeId().value());
            }
        });
        this.blockingPins = Collections.unmodifiableList(copy);
    }

    public EnclosureHullKey firstHull() {
        return firstHull;
    }

    public EnclosureHullKey secondHull() {
        return secondHull;
    }

    public MapReferenceId firstMap() {
        return firstHull.mapReferenceId();
    }

    public MapReferenceId secondMap() {
        return secondHull.mapReferenceId();
    }

    public String pairKey() {
        return CanonicalLayoutKeys.pair(firstHull, secondHull);
    }

    public Kind kind() {
        return kind;
    }

    public Reason reason() {
        return reason;
    }

    public List<PinProjection> blockingPins() {
        return blockingPins;
    }
}

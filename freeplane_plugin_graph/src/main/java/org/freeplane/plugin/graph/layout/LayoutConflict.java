package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class LayoutConflict {
    private final MapReferenceId firstMap;
    private final MapReferenceId secondMap;
    private final List<PinProjection> blockingPins;

    public LayoutConflict(final MapReferenceId firstMap, final MapReferenceId secondMap,
            final List<PinProjection> blockingPins) {
        this.firstMap = Objects.requireNonNull(firstMap, "firstMap");
        this.secondMap = Objects.requireNonNull(secondMap, "secondMap");
        if (firstMap.equals(secondMap)) {
            throw new IllegalArgumentException("A layout conflict requires two distinct maps");
        }
        Objects.requireNonNull(blockingPins, "blockingPins");
        final List<PinProjection> copy = new ArrayList<PinProjection>(blockingPins.size());
        for (final PinProjection pin : blockingPins) {
            final PinProjection value = Objects.requireNonNull(pin, "blockingPins entry");
            if (!value.active()) {
                throw new IllegalArgumentException("Layout conflicts can contain active pins only");
            }
            copy.add(value);
        }
        this.blockingPins = Collections.unmodifiableList(copy);
    }

    public static LayoutConflict of(final MapReferenceId firstMap, final MapReferenceId secondMap,
            final List<PinProjection> blockingPins) {
        return new LayoutConflict(firstMap, secondMap, blockingPins);
    }

    public MapReferenceId firstMap() {
        return firstMap;
    }

    public MapReferenceId secondMap() {
        return secondMap;
    }

    public List<PinProjection> blockingPins() {
        return blockingPins;
    }
}

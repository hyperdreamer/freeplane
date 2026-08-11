package org.freeplane.plugin.graph.adapter;

import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class MapAdapterEvent {
    private final MapReferenceId mapReferenceId;
    private final MapOperationalState state;

    public MapAdapterEvent(final MapReferenceId mapReferenceId, final MapOperationalState state) {
        this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        this.state = Objects.requireNonNull(state, "state");
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    public MapOperationalState state() {
        return state;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MapAdapterEvent)) {
            return false;
        }
        final MapAdapterEvent that = (MapAdapterEvent) other;
        return mapReferenceId.equals(that.mapReferenceId) && state == that.state;
    }

    @Override
    public int hashCode() {
        return 31 * mapReferenceId.hashCode() + state.hashCode();
    }

    @Override
    public String toString() {
        return "MapAdapterEvent{" + "mapReferenceId=" + mapReferenceId + ", state=" + state + '}';
    }
}

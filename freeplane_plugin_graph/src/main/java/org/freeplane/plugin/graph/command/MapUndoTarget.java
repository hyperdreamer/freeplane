package org.freeplane.plugin.graph.command;

import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class MapUndoTarget {
    private final MapReferenceId mapReferenceId;
    private final String mapName;
    private final boolean canUndo;

    public MapUndoTarget(final MapReferenceId mapReferenceId, final String mapName, final boolean canUndo) {
        this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        this.mapName = Objects.requireNonNull(mapName, "mapName");
        this.canUndo = canUndo;
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    public String mapName() {
        return mapName;
    }

    public boolean canUndo() {
        return canUndo;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MapUndoTarget)) {
            return false;
        }
        final MapUndoTarget that = (MapUndoTarget) other;
        return canUndo == that.canUndo && mapReferenceId.equals(that.mapReferenceId) && mapName.equals(that.mapName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapReferenceId, mapName, Boolean.valueOf(canUndo));
    }

    @Override
    public String toString() {
        return "MapUndoTarget{" + "mapReferenceId=" + mapReferenceId + ", mapName=" + mapName
            + ", canUndo=" + canUndo + '}';
    }
}

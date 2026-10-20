package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.command.MapUndoTarget;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class WorkspaceSessionStatus {
    private static final Comparator<MapReferenceId> MAP_ID_ORDER = new Comparator<MapReferenceId>() {
        @Override
        public int compare(final MapReferenceId first, final MapReferenceId second) {
            return first.value().toString().compareTo(second.value().toString());
        }
    };

    private final boolean workspaceDirty;
    private final boolean workspaceUndoAvailable;
    private final boolean workspaceRedoAvailable;
    private final boolean saveFailed;
    private final Set<MapReferenceId> dirtySourceMaps;
    private final Optional<MapUndoTarget> sourceMapUndoTarget;

    private WorkspaceSessionStatus(final boolean workspaceDirty, final boolean workspaceUndoAvailable,
            final boolean workspaceRedoAvailable, final boolean saveFailed, final Set<MapReferenceId> dirtySourceMaps,
            final Optional<MapUndoTarget> sourceMapUndoTarget) {
        this.workspaceDirty = workspaceDirty;
        this.workspaceUndoAvailable = workspaceUndoAvailable;
        this.workspaceRedoAvailable = workspaceRedoAvailable;
        this.saveFailed = saveFailed;
        this.dirtySourceMaps = copyMapIds(dirtySourceMaps);
        this.sourceMapUndoTarget = Objects.requireNonNull(sourceMapUndoTarget, "sourceMapUndoTarget");
    }

    public static WorkspaceSessionStatus empty() {
        return of(false, false, false, false, Collections.<MapReferenceId>emptySet(),
            Optional.<MapUndoTarget>empty());
    }

    public static WorkspaceSessionStatus of(final boolean workspaceDirty, final boolean workspaceUndoAvailable,
            final boolean workspaceRedoAvailable, final boolean saveFailed, final Set<MapReferenceId> dirtySourceMaps,
            final Optional<MapUndoTarget> sourceMapUndoTarget) {
        return new WorkspaceSessionStatus(workspaceDirty, workspaceUndoAvailable, workspaceRedoAvailable, saveFailed,
            dirtySourceMaps, sourceMapUndoTarget);
    }

    public boolean workspaceDirty() {
        return workspaceDirty;
    }

    public boolean workspaceUndoAvailable() {
        return workspaceUndoAvailable;
    }

    public boolean workspaceRedoAvailable() {
        return workspaceRedoAvailable;
    }

    public boolean saveFailed() {
        return saveFailed;
    }

    public Set<MapReferenceId> dirtySourceMaps() {
        return dirtySourceMaps;
    }

    public Optional<MapUndoTarget> sourceMapUndoTarget() {
        return sourceMapUndoTarget;
    }

    private static Set<MapReferenceId> copyMapIds(final Set<MapReferenceId> maps) {
        final List<MapReferenceId> sorted = new ArrayList<MapReferenceId>(Objects.requireNonNull(maps,
            "dirtySourceMaps"));
        for (final MapReferenceId map : sorted) {
            Objects.requireNonNull(map, "map");
        }
        Collections.sort(sorted, MAP_ID_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<MapReferenceId>(sorted));
    }
}

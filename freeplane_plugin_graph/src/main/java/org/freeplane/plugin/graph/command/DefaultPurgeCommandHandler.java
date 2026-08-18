package org.freeplane.plugin.graph.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommands;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

public final class DefaultPurgeCommandHandler implements PurgeCommandHandler {
    private static final String PURGE_EMPTY = "graph_workspace.purge.empty";
    private static final String PURGE_STALE = "graph_workspace.purge.stale";
    private static final String PURGE_PENDING = "graph_workspace.purge.pending";
    private static final String PURGE_UNAVAILABLE = "graph_workspace.purge.unavailable";
    private static final String PURGE_NOT_FOUND = "graph_workspace.purge.relationship_not_found";
    private static final String PURGE_NOT_MISSING = "graph_workspace.purge.relationship_not_missing";

    private final GraphUpdateCoordinator updates;
    private final GraphWorkspaceStore store;
    private final EdtExecutor edt;

    public DefaultPurgeCommandHandler(final GraphUpdateCoordinator updates, final GraphWorkspaceStore store,
            final EdtExecutor edt) {
        this.updates = Objects.requireNonNull(updates, "updates");
        this.store = Objects.requireNonNull(store, "store");
        this.edt = Objects.requireNonNull(edt, "edt");
    }

    @Override
    public GraphCommandResult purge(final GraphCommands.Purge command) {
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                return purgeOnEdt(Objects.requireNonNull(command, "command"));
            }
        });
    }

    private GraphCommandResult purgeOnEdt(final GraphCommands.Purge command) {
        final Set<RelationshipId> requested = command.relationships();
        if (requested.isEmpty()) {
            return rejected(PURGE_EMPTY);
        }

        final GraphProjection displayed = updates.currentProjection();
        GraphCommandResult rejection = validateDisplayedState(displayed, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        rejection = validateRelationships(displayed.relationshipResolutions(), requested);
        if (rejection != null) {
            return rejection;
        }

        final GraphProjection current = updates.currentProjection();
        rejection = validateDisplayedState(current, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        rejection = validateRelationships(current.relationshipResolutions(), requested);
        if (rejection != null) {
            return rejection;
        }

        final Set<RelationshipId> validated = new LinkedHashSet<RelationshipId>(requested);
        return store.execute(WorkspaceCommands.purgeRelationships(validated));
    }

    private GraphCommandResult validateDisplayedState(final GraphProjection projection, final long displayedGeneration) {
        if (projection == null) {
            return rejected(PURGE_UNAVAILABLE);
        }
        if (projection.generation() != displayedGeneration) {
            return rejected(PURGE_STALE, displayedGeneration, projection.generation());
        }
        if (updates.hasPendingChanges()) {
            return rejected(PURGE_PENDING);
        }
        return null;
    }

    private GraphCommandResult validateRelationships(final List<RelationshipResolution> records,
            final Set<RelationshipId> requested) {
        if (records == null) {
            return rejected(PURGE_UNAVAILABLE);
        }
        for (final RelationshipId id : requested) {
            final RelationshipResolution record = find(records, id);
            if (record == null) {
                return rejected(PURGE_NOT_FOUND, id);
            }
            if (record.status() != RelationshipStatus.UNRESOLVED_MISSING_NODE) {
                return rejected(PURGE_NOT_MISSING, id, record.status());
            }
        }
        return null;
    }

    private static RelationshipResolution find(final List<RelationshipResolution> records, final RelationshipId id) {
        for (final RelationshipResolution record : records) {
            if (record != null && id.equals(record.relationshipId())) {
                return record;
            }
        }
        return null;
    }

    private GraphCommandResult rejected(final String messageKey, final Object... arguments) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(store.currentDocument(), messageKey, arguments));
    }
}

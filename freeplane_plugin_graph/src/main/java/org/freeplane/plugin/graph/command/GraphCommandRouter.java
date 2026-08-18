package org.freeplane.plugin.graph.command;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.control.WorkspacePathReservation;
import org.freeplane.plugin.graph.control.WorkspaceSessionId;
import org.freeplane.plugin.graph.control.WorkspaceSessionRegistry;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommand;
import org.freeplane.plugin.graph.workspace.WorkspaceCommands;
import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class GraphCommandRouter {
    private static final String MAP_NOT_FOUND = "graph_workspace.map.not_found";
    private static final String MAP_RETRY_INACTIVE = "graph_workspace.map.retry.inactive";
    private static final String MAP_RETRY_FAILED = "graph_workspace.map.retry.failed";
    private static final String SAVE_COMPLETED = "graph_workspace.workspace.saved";
    private static final String SAVE_FAILED = "graph_workspace.workspace.save_failed";
    private static final String SAVE_AS_COMPLETED = "graph_workspace.workspace.save_as_completed";
    private static final String SAVE_AS_FAILED = "graph_workspace.workspace.save_as_failed";
    private static final String LAYOUT_PAUSED = "graph_workspace.layout.paused";
    private static final String LAYOUT_RESTARTED = "graph_workspace.layout.restarted";
    private static final String LAYOUT_RESET = "graph_workspace.layout.reset";

    public interface MapRetryHandler {
        GraphCommandResult retry(MapReference reference);
    }

    private final GraphWorkspaceStore store;
    private final MapRetryHandler mapRetry;
    private final FreeplaneMapCommandExecutor mapCommands;
    private final SourceNavigation navigation;
    private final GraphUpdateCoordinator updates;
    private final WorkspaceSessionRegistry sessions;
    private final WorkspaceSessionId sessionId;
    private final PurgeCommandHandler purgeHandler;
    private final ContributorDeletionHandler deletionHandler;

    public GraphCommandRouter(final GraphWorkspaceStore store, final MapRetryHandler mapRetry,
            final FreeplaneMapCommandExecutor mapCommands, final SourceNavigation navigation,
            final GraphUpdateCoordinator updates, final WorkspaceSessionRegistry sessions,
            final WorkspaceSessionId sessionId, final PurgeCommandHandler purgeHandler,
            final ContributorDeletionHandler deletionHandler) {
        this.store = Objects.requireNonNull(store, "store");
        this.mapRetry = Objects.requireNonNull(mapRetry, "mapRetry");
        this.mapCommands = Objects.requireNonNull(mapCommands, "mapCommands");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.updates = Objects.requireNonNull(updates, "updates");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.purgeHandler = Objects.requireNonNull(purgeHandler, "purgeHandler");
        this.deletionHandler = Objects.requireNonNull(deletionHandler, "deletionHandler");
    }

    public GraphCommandResult execute(final GraphCommand command) {
        final GraphCommand value = Objects.requireNonNull(command, "command");
        if (value instanceof GraphCommands.AddMap) {
            return executeAddMap((GraphCommands.AddMap) value);
        }
        if (value instanceof GraphCommands.RetryMap) {
            return executeRetryMap((GraphCommands.RetryMap) value);
        }
        if (value instanceof GraphCommands.RemoveMap) {
            return executeRemoveMap((GraphCommands.RemoveMap) value);
        }
        if (value instanceof GraphCommands.LocateMap) {
            return executeLocateMap((GraphCommands.LocateMap) value);
        }
        if (value instanceof GraphCommands.CreateRelationship) {
            return executeCreateRelationship((GraphCommands.CreateRelationship) value);
        }
        if (value instanceof GraphCommands.UpdateRelationship) {
            return executeUpdateRelationship((GraphCommands.UpdateRelationship) value);
        }
        if (value instanceof GraphCommands.DeleteRelationship) {
            return executeDeleteRelationship((GraphCommands.DeleteRelationship) value);
        }
        if (value instanceof GraphCommands.Purge) {
            return requireResult(purgeHandler.purge((GraphCommands.Purge) value));
        }
        if (value instanceof GraphCommands.DeleteContributor) {
            return requireResult(deletionHandler.deleteOne((GraphCommands.DeleteContributor) value));
        }
        if (value instanceof GraphCommands.DeleteAllContributors) {
            return requireResult(deletionHandler.deleteAll((GraphCommands.DeleteAllContributors) value));
        }
        if (value instanceof GraphCommands.Pin) {
            final GraphCommands.Pin pin = (GraphCommands.Pin) value;
            return executeWorkspace(WorkspaceCommands.pin(pin.node(), pin.x(), pin.y()));
        }
        if (value instanceof GraphCommands.Unpin) {
            return executeWorkspace(WorkspaceCommands.unpin(((GraphCommands.Unpin) value).node()));
        }
        if (value instanceof GraphCommands.UnpinAll) {
            return executeWorkspace(WorkspaceCommands.unpinAll());
        }
        if (value instanceof GraphCommands.Display) {
            return executeWorkspace(WorkspaceCommands.setDisplaySettings(
                ((GraphCommands.Display) value).settings()));
        }
        if (value instanceof GraphCommands.Viewport) {
            return store.updateViewport(((GraphCommands.Viewport) value).viewport());
        }
        if (value instanceof GraphCommands.UndoWorkspace) {
            return store.undo();
        }
        if (value instanceof GraphCommands.RedoWorkspace) {
            return store.redo();
        }
        if (value instanceof GraphCommands.UndoSourceMap) {
            return mapCommands.undoCurrentSourceMap();
        }
        if (value instanceof GraphCommands.Save) {
            return saveNow();
        }
        if (value instanceof GraphCommands.RetrySave) {
            return saveNow();
        }
        if (value instanceof GraphCommands.SaveAs) {
            return executeSaveAs((GraphCommands.SaveAs) value);
        }
        if (value instanceof GraphCommands.PauseLayout) {
            return executeLayoutPause();
        }
        if (value instanceof GraphCommands.RestartLayout) {
            return executeLayoutRestart();
        }
        if (value instanceof GraphCommands.ResetLayout) {
            return executeLayoutReset();
        }
        if (value instanceof GraphCommands.Connect) {
            final GraphCommands.Connect connect = (GraphCommands.Connect) value;
            return mapCommands.createConnector(connect.source(), connect.target(), connect.direction());
        }
        if (value instanceof GraphCommands.OpenSource) {
            return navigation.open(((GraphCommands.OpenSource) value).source());
        }
        throw new IllegalArgumentException("Unsupported graph command: " + value.getClass().getName());
    }

    public Optional<MapUndoTarget> currentMapUndoTarget() {
        final Optional<MapUndoTarget> target = mapCommands.currentUndoTarget();
        return target == null ? Optional.<MapUndoTarget>empty() : target;
    }

    private GraphCommandResult executeAddMap(final GraphCommands.AddMap command) {
        return executeWorkspace(WorkspaceCommands.addMap(command.proposedId(), command.storedUri()));
    }

    private GraphCommandResult executeRetryMap(final GraphCommands.RetryMap command) {
        final MapReference reference = findMap(command.mapReferenceId());
        if (reference == null) {
            return rejected(MAP_NOT_FOUND, command.mapReferenceId());
        }
        if (!reference.active()) {
            return rejected(MAP_RETRY_INACTIVE, command.mapReferenceId());
        }
        try {
            return requireResult(mapRetry.retry(reference));
        }
        catch (final RuntimeException failure) {
            return rejected(MAP_RETRY_FAILED, command.mapReferenceId());
        }
    }

    private GraphCommandResult executeRemoveMap(final GraphCommands.RemoveMap command) {
        return executeWorkspace(WorkspaceCommands.removeMap(command.mapReferenceId()));
    }

    private GraphCommandResult executeLocateMap(final GraphCommands.LocateMap command) {
        return executeWorkspace(WorkspaceCommands.locateMap(command.mapReferenceId(), command.replacementUri()));
    }

    private GraphCommandResult executeCreateRelationship(final GraphCommands.CreateRelationship command) {
        return executeWorkspace(WorkspaceCommands.createRelationship(command.id(), command.source(), command.target(),
            command.direction()));
    }

    private GraphCommandResult executeUpdateRelationship(final GraphCommands.UpdateRelationship command) {
        return executeWorkspace(WorkspaceCommands.updateRelationship(command.id(), command.source(), command.target(),
            command.direction()));
    }

    private GraphCommandResult executeDeleteRelationship(final GraphCommands.DeleteRelationship command) {
        return executeWorkspace(WorkspaceCommands.deleteRelationship(command.id()));
    }

    private GraphCommandResult executeWorkspace(final WorkspaceCommand command) {
        return requireResult(store.execute(Objects.requireNonNull(command, "command")));
    }

    private GraphCommandResult saveNow() {
        try {
            store.saveNow();
            return applied(SAVE_COMPLETED);
        }
        catch (final RuntimeException failure) {
            return rejected(SAVE_FAILED);
        }
    }

    private GraphCommandResult executeSaveAs(final GraphCommands.SaveAs command) {
        final WorkspacePathReservation reservation;
        try {
            reservation = sessions.reserveSaveAs(sessionId, command.target());
        }
        catch (final RuntimeException failure) {
            return rejected(SAVE_AS_FAILED);
        }

        try {
            final WorkspaceIdentityChange change = store.saveAs(command.target());
            reservation.commit(change);
            return applied(SAVE_AS_COMPLETED).withIdentityChange(change);
        }
        catch (final RuntimeException failure) {
            reservation.close();
            return rejected(SAVE_AS_FAILED);
        }
    }

    private GraphCommandResult executeLayoutPause() {
        try {
            updates.pauseLayout();
            return applied(LAYOUT_PAUSED);
        }
        catch (final RuntimeException failure) {
            return rejected(LAYOUT_PAUSED);
        }
    }

    private GraphCommandResult executeLayoutRestart() {
        try {
            updates.restartLayout();
            return applied(LAYOUT_RESTARTED);
        }
        catch (final RuntimeException failure) {
            return rejected(LAYOUT_RESTARTED);
        }
    }

    private GraphCommandResult executeLayoutReset() {
        try {
            updates.resetLayout();
            return applied(LAYOUT_RESET);
        }
        catch (final RuntimeException failure) {
            return rejected(LAYOUT_RESET);
        }
    }

    private MapReference findMap(final MapReferenceId id) {
        for (final MapReference reference : store.currentDocument().maps()) {
            if (reference.id().equals(id)) {
                return reference;
            }
        }
        return null;
    }

    private GraphCommandResult applied(final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.applied(store.currentDocument(), messageKey));
    }

    private GraphCommandResult rejected(final String messageKey, final Object... arguments) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(store.currentDocument(), messageKey, arguments));
    }

    private static GraphCommandResult requireResult(final GraphCommandResult result) {
        return Objects.requireNonNull(result, "command result");
    }
}

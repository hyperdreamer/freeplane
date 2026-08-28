package org.freeplane.plugin.graph.command;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class SourceNavigation {
    private static final String SOURCE_OPENED = "graph_workspace.source.opened";
    private static final String SOURCE_MAP_UNAVAILABLE = "graph_workspace.source_map.unavailable";
    private static final String SOURCE_NODE_NOT_FOUND = "graph_workspace.source_node.not_found";

    private final FreeplaneMapCommandExecutor.MapLeaseLookup leases;
    private final ModeController modeController;
    private final EdtExecutor edt;
    private final FreeplaneMapCommandExecutor.TraversalResolver traversal;
    private final FreeplaneMapCommandExecutor.ResultEnvelope results;

    public SourceNavigation(final GraphWorkspaceStore workspace,
            final FreeplaneMapCommandExecutor.MapLeaseLookup leases, final ModeController modeController,
            final EdtExecutor edt) {
        this(leases, modeController, edt, FreeplaneMapCommandExecutor.productionTraversalResolver(),
            FreeplaneMapCommandExecutor.resultEnvelope(workspace));
    }

    SourceNavigation(final FreeplaneMapCommandExecutor.MapLeaseLookup leases, final ModeController modeController,
            final EdtExecutor edt, final FreeplaneMapCommandExecutor.TraversalResolver traversal,
            final FreeplaneMapCommandExecutor.ResultEnvelope results) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.modeController = Objects.requireNonNull(modeController, "modeController");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.traversal = Objects.requireNonNull(traversal, "traversal");
        this.results = Objects.requireNonNull(results, "results");
    }

    public GraphCommandResult open(final SourceNodeKey source) {
        final SourceNodeKey requestedSource = Objects.requireNonNull(source, "source");
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                final Optional<MapLease> lease = activeLease(requestedSource.mapReferenceId());
                if (!lease.isPresent()) {
                    return rejected(SOURCE_MAP_UNAVAILABLE);
                }
                final Optional<NodeModel> resolved = resolveAttached(lease.get(), requestedSource);
                if (!resolved.isPresent()) {
                    return rejected(SOURCE_NODE_NOT_FOUND);
                }
                final MapController mapController = modeController.getMapController();
                if (mapController == null) {
                    return rejected(SOURCE_MAP_UNAVAILABLE);
                }
                mapController.select(resolved.get());
                mapController.centerNode(resolved.get());
                return GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(), SOURCE_OPENED))
                    .withEditorViewActivated(true);
            }
        });
    }

    private Optional<MapLease> activeLease(final MapReferenceId mapId) {
        final Optional<MapLease> found = leases.find(mapId);
        if (found == null || !found.isPresent()) {
            return Optional.empty();
        }
        final MapLease lease = found.get();
        if (!mapId.equals(lease.mapReferenceId()) || lease.state() != MapOperationalState.AVAILABLE) {
            return Optional.empty();
        }
        return Optional.of(lease);
    }

    private Optional<NodeModel> resolveAttached(final MapLease lease, final SourceNodeKey key) {
        if (!lease.mapReferenceId().equals(key.mapReferenceId())) {
            return Optional.empty();
        }
        final Optional<NodeModel> resolved = traversal.resolve(lease, key);
        if (resolved == null || !resolved.isPresent() || !attachedToMap(resolved.get())) {
            return Optional.empty();
        }
        return resolved;
    }

    private static boolean attachedToMap(final NodeModel node) {
        final MapModel map = node.getMap();
        if (map == null) {
            return false;
        }
        final NodeModel root = map.getRootNode();
        for (NodeModel current = node; current != null; current = current.getParentNode()) {
            if (current == root) {
                return true;
            }
        }
        return false;
    }

    private GraphCommandResult rejected(final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(results.currentDocument(), messageKey));
    }
}

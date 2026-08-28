package org.freeplane.plugin.graph.command;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
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
    private final ViewMaterializationTracker views;

    public SourceNavigation(final GraphWorkspaceStore workspace,
            final FreeplaneMapCommandExecutor.MapLeaseLookup leases, final ModeController modeController,
            final EdtExecutor edt, final ViewMaterializationTracker views) {
        this(leases, modeController, edt, FreeplaneMapCommandExecutor.productionTraversalResolver(),
            FreeplaneMapCommandExecutor.resultEnvelope(workspace), views);
    }

    SourceNavigation(final FreeplaneMapCommandExecutor.MapLeaseLookup leases, final ModeController modeController,
            final EdtExecutor edt, final FreeplaneMapCommandExecutor.TraversalResolver traversal,
            final FreeplaneMapCommandExecutor.ResultEnvelope results, final ViewMaterializationTracker views) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.modeController = Objects.requireNonNull(modeController, "modeController");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.traversal = Objects.requireNonNull(traversal, "traversal");
        this.results = Objects.requireNonNull(results, "results");
        this.views = Objects.requireNonNull(views, "views");
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
                final MapModel map = resolved.get().getMap();
                if (!views.containsView(map)) {
                    // Workspace maps are loaded model-only (no MapView). MapController.select
                    // silently no-ops without one (changeToMap finds no view, displayNode
                    // early-returns on map mismatch), so the view must exist first. If the
                    // same file is already open under another instance (a parallel load),
                    // switch to that view instead of materializing a duplicate.
                    if (!switchToOpenView(requestedSource, map)) {
                        views.materialize(requestedSource.mapReferenceId(), map);
                    }
                    else {
                        return GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(),
                            SOURCE_OPENED)).withEditorViewActivated(true);
                    }
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

    private boolean switchToOpenView(final SourceNodeKey key, final MapModel map) {
        final URL url = map.getURL();
        if (url == null) {
            return false;
        }
        final Controller controller = modeController.getController();
        final IMapViewManager viewManager = controller == null ? null : controller.getMapViewManager();
        if (viewManager == null) {
            return false;
        }
        final boolean switched;
        try {
            switched = viewManager.tryToChangeToMapView(url);
        }
        catch (final MalformedURLException exception) {
            return false;
        }
        if (!switched || controller.getMap() == null) {
            return false;
        }
        final Optional<NodeModel> openNode = traversal.resolve(controller.getMap(), key);
        if (openNode == null || !openNode.isPresent() || !attachedToMap(openNode.get())) {
            return false;
        }
        final MapController mapController = modeController.getMapController();
        if (mapController == null) {
            return false;
        }
        mapController.select(openNode.get());
        mapController.centerNode(openNode.get());
        return true;
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

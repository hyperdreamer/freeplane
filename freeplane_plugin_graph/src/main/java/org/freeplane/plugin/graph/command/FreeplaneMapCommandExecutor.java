package org.freeplane.plugin.graph.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.features.link.ArrowType;
import org.freeplane.features.link.ConnectorArrows;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.adapter.TraversalNodeResolver;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class FreeplaneMapCommandExecutor {
    private static final String CONNECTOR_CREATED = "graph_workspace.connector.created";
    private static final String CONNECTOR_DELETED = "graph_workspace.connector.deleted";
    private static final String SOURCE_MAP_UNDONE = "graph_workspace.source_map.undone";
    private static final String SOURCE_MAP_UNAVAILABLE = "graph_workspace.source_map.unavailable";
    private static final String SOURCE_NODE_NOT_FOUND = "graph_workspace.source_node.not_found";
    private static final String CONNECTOR_SAME_MAP_REQUIRED = "graph_workspace.connector.same_map_required";
    private static final String CONNECTOR_SELF_CONNECTION = "graph_workspace.connector.self_connection";
    private static final String SOURCE_MAP_READ_ONLY = "graph_workspace.source_map.read_only";
    private static final String SOURCE_MAP_UNDO_UNAVAILABLE = "graph_workspace.source_map.undo_unavailable";
    private static final String CONNECTOR_TARGET_REQUIRES_SAVED_ID = "graph_workspace.connector.target_requires_saved_id";
    private static final String CONNECTOR_CHANGED = "graph_workspace.connector.changed";
    private static final String SOURCE_MAP_NOTHING_TO_UNDO = "graph_workspace.source_map.nothing_to_undo";

    public interface MapLeaseLookup {
        Optional<MapLease> find(MapReferenceId mapReferenceId);
    }

    interface TraversalResolver {
        Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key);
    }

    interface ResultEnvelope {
        WorkspaceDocument currentDocument();
    }

    interface ContributorDeletionTransaction {
        GraphCommandResult outcome();
        Set<MapReferenceId> dirtySourceMaps();
        boolean editorViewActivated();
        void commit();
        void rollback();
    }

    interface NativeConnector {
        ConnectorModel addConnector(NodeModel source, String targetId);
        void changeArrows(ConnectorModel connector, ConnectorArrows arrows);
        void removeArrowLink(ConnectorModel connector);
    }

    private final MapLeaseLookup leases;
    private final ModeController modeController;
    private final EdtExecutor edt;
    private final ViewMaterializationTracker views;
    private final TraversalResolver traversal;
    private final ResultEnvelope results;
    private final NativeConnector connectors;

    private SourceNodeKey undoSource;

    public FreeplaneMapCommandExecutor(final GraphWorkspaceStore workspace, final MapLeaseLookup leases,
            final ModeController modeController, final EdtExecutor edt, final ViewMaterializationTracker views) {
        this(leases, modeController, edt, views, productionTraversalResolver(), resultEnvelope(workspace),
            new MLinkNativeConnector(modeController));
    }

    FreeplaneMapCommandExecutor(final MapLeaseLookup leases, final ModeController modeController,
            final EdtExecutor edt, final ViewMaterializationTracker views, final TraversalResolver traversal,
            final ResultEnvelope results, final NativeConnector connectors) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.modeController = Objects.requireNonNull(modeController, "modeController");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.views = Objects.requireNonNull(views, "views");
        this.traversal = Objects.requireNonNull(traversal, "traversal");
        this.results = Objects.requireNonNull(results, "results");
        this.connectors = Objects.requireNonNull(connectors, "connectors");
    }

    public GraphCommandResult createConnector(final SourceNodeKey source, final SourceNodeKey target,
            final RelationshipDirection direction) {
        final SourceNodeKey requestedSource = Objects.requireNonNull(source, "source");
        final SourceNodeKey requestedTarget = Objects.requireNonNull(target, "target");
        final RelationshipDirection requestedDirection = Objects.requireNonNull(direction, "direction");
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                return createConnectorOnEdt(requestedSource, requestedTarget, requestedDirection);
            }
        });
    }

    public GraphCommandResult deleteConnector(final ContributorKey key, final ConnectorDescriptor expected) {
        final ContributorKey requestedKey = Objects.requireNonNull(key, "key");
        final ConnectorDescriptor expectedDescriptor = Objects.requireNonNull(expected, "expected");
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                return deleteConnectorOnEdt(requestedKey, expectedDescriptor);
            }
        });
    }

    ContributorDeletionTransaction beginContributorDeletion(final ContributorDeletionPlan plan) {
        final ContributorDeletionPlan requested = Objects.requireNonNull(plan, "plan");
        if (!edt.isEdt()) {
            throw new IllegalStateException("Contributor deletion transactions must run on the EDT");
        }
        return beginContributorDeletionOnEdt(requested);
    }

    public GraphCommandResult undoCurrentSourceMap() {
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                final Optional<UndoContext> context = currentUndoContextOnEdt();
                if (!context.isPresent()) {
                    return rejected(SOURCE_MAP_UNDO_UNAVAILABLE);
                }
                final IUndoHandler handler = context.get().undoHandler;
                if (handler == null) {
                    return rejected(SOURCE_MAP_UNDO_UNAVAILABLE);
                }
                if (!handler.canUndo()) {
                    return noOp(SOURCE_MAP_NOTHING_TO_UNDO);
                }
                try {
                    handler.undo();
                    return applied(SOURCE_MAP_UNDONE, context.get().mapId, false);
                }
                catch (final RuntimeException failure) {
                    return rejected(SOURCE_MAP_UNAVAILABLE);
                }
            }
        });
    }

    public Optional<MapUndoTarget> currentUndoTarget() {
        return edt.call(new Callable<Optional<MapUndoTarget>>() {
            @Override
            public Optional<MapUndoTarget> call() {
                final Optional<UndoContext> context = currentUndoContextOnEdt();
                if (!context.isPresent() || context.get().map.getTitle() == null) {
                    return Optional.empty();
                }
                final IUndoHandler handler = context.get().undoHandler;
                return Optional.of(new MapUndoTarget(context.get().mapId, context.get().map.getTitle(),
                    handler != null && handler.canUndo()));
            }
        });
    }

    static TraversalResolver productionTraversalResolver() {
        return new TraversalNodeResolverAdapter();
    }

    static ResultEnvelope resultEnvelope(final GraphWorkspaceStore workspace) {
        return new WorkspaceResultEnvelope(workspace);
    }

    private void requireEdt() {
        if (!edt.isEdt()) {
            throw new IllegalStateException("Contributor deletion transactions must run on the EDT");
        }
    }

    private ContributorDeletionTransaction beginContributorDeletionOnEdt(final ContributorDeletionPlan plan) {
        final SourceNodeKey previousUndoSource = undoSource;
        final List<PrevalidatedMap> prevalidatedMaps = new ArrayList<PrevalidatedMap>();
        final List<PreparedMap> preparedMaps = new ArrayList<PreparedMap>();
        final Set<MapReferenceId> dirtyMaps = new LinkedHashSet<MapReferenceId>();
        boolean editorViewActivated = false;
        if (!plan.hasNativeEdits()) {
            return new NativeDeletionTransaction(Collections.<PreparedMap>emptyList(),
                appliedForMaps(CONNECTOR_DELETED, Collections.<MapReferenceId>emptySet(), false),
                Collections.<MapReferenceId>emptySet(), previousUndoSource, null);
        }
        try {
            final List<MapReferenceId> mapIds = new ArrayList<MapReferenceId>(plan.nativeEditsByMap().keySet());
            Collections.sort(mapIds, MAP_ID_ORDER);
            for (final MapReferenceId mapId : mapIds) {
                prevalidatedMaps.add(prevalidateMap(mapId, plan.nativeEditsFor(mapId)));
                dirtyMaps.add(mapId);
            }
            for (final PrevalidatedMap prevalidated : prevalidatedMaps) {
                final PreparedMap prepared = materializeMap(prevalidated);
                preparedMaps.add(prepared);
                editorViewActivated = editorViewActivated || prepared.editorViewActivated;
            }

            final List<PreparedMap> startedMaps = new ArrayList<PreparedMap>();
            try {
                for (final PreparedMap prepared : preparedMaps) {
                    prepared.undoHandler.startTransaction();
                    prepared.transactionStarted = true;
                    startedMaps.add(prepared);
                }
                for (final PreparedMap prepared : preparedMaps) {
                    for (final ConnectorModel connector : prepared.connectors) {
                        connectors.removeArrowLink(connector);
                    }
                }
            }
            catch (final RuntimeException failure) {
                rollbackStarted(startedMaps, previousUndoSource);
                return new NativeDeletionTransaction(Collections.<PreparedMap>emptyList(),
                    rejectedTransaction(CONNECTOR_CHANGED), Collections.<MapReferenceId>emptySet(),
                    previousUndoSource, null);
            }
            final PreparedMap owner = preparedMaps.get(preparedMaps.size() - 1);
            final Set<MapReferenceId> immutableDirtyMaps =
                Collections.unmodifiableSet(new LinkedHashSet<MapReferenceId>(dirtyMaps));
            return new NativeDeletionTransaction(preparedMaps,
                appliedForMaps(CONNECTOR_DELETED, immutableDirtyMaps, editorViewActivated), immutableDirtyMaps,
                previousUndoSource, owner.undoSource);
        }
        catch (final BatchFailure failure) {
            rollbackStarted(preparedMaps, previousUndoSource);
            return new NativeDeletionTransaction(Collections.<PreparedMap>emptyList(),
                rejectedTransaction(failure.messageKey), Collections.<MapReferenceId>emptySet(),
                previousUndoSource, null);
        }
        catch (final RuntimeException failure) {
            rollbackStarted(preparedMaps, previousUndoSource);
            return new NativeDeletionTransaction(Collections.<PreparedMap>emptyList(),
                rejectedTransaction(SOURCE_MAP_UNAVAILABLE), Collections.<MapReferenceId>emptySet(),
                previousUndoSource, null);
        }
    }

    private PrevalidatedMap prevalidateMap(final MapReferenceId mapId,
            final List<ContributorDeletionPlan.NativeEdit> edits) {
        final Optional<MapLease> lease = activeLease(mapId);
        if (!lease.isPresent()) {
            throw new BatchFailure(SOURCE_MAP_UNAVAILABLE);
        }
        MapModel map = null;
        final List<PreparedSource> sources = new ArrayList<PreparedSource>(edits.size());
        for (final ContributorDeletionPlan.NativeEdit edit : edits) {
            final ContributorKey key = edit.key();
            final ConnectorDescriptor expected = edit.descriptor();
            if (!key.isNativeConnector() || !key.mapReferenceId().isPresent() || !key.source().isPresent()
                    || !mapId.equals(key.mapReferenceId().get())
                    || !mapId.equals(expected.source().mapReferenceId())
                    || !key.source().get().equals(expected.source())) {
                throw new BatchFailure(CONNECTOR_CHANGED);
            }
            final Optional<NodeModel> source = resolveAttached(lease.get(), key.source().get());
            if (!source.isPresent()) {
                throw new BatchFailure(SOURCE_NODE_NOT_FOUND);
            }
            final MapModel sourceMap = source.get().getMap();
            if (sourceMap == null) {
                throw new BatchFailure(SOURCE_MAP_UNAVAILABLE);
            }
            if (map == null) {
                map = sourceMap;
            }
            else if (map != sourceMap) {
                throw new BatchFailure(CONNECTOR_CHANGED);
            }
            if (!editable(sourceMap)) {
                throw new BatchFailure(SOURCE_MAP_READ_ONLY);
            }
            sources.add(new PreparedSource(key.source().get(), source.get(), expected,
                key.occurrence().getAsInt()));
        }
        if (map == null) {
            throw new BatchFailure(CONNECTOR_CHANGED);
        }
        final List<ConnectorModel> retained = new ArrayList<ConnectorModel>(sources.size());
        for (final PreparedSource source : sources) {
            final Optional<ConnectorModel> connector = connectorAt(source.node, source.occurrence);
            if (!connector.isPresent() || !matches(source.sourceKey, mapId, connector.get(), source.expected)) {
                throw new BatchFailure(CONNECTOR_CHANGED);
            }
            retained.add(connector.get());
        }
        return new PrevalidatedMap(mapId, map, retained, sources.get(sources.size() - 1).sourceKey);
    }

    private PreparedMap materializeMap(final PrevalidatedMap prevalidated) {
        final boolean createdView;
        try {
            createdView = views.materialize(prevalidated.mapId, prevalidated.map);
        }
        catch (final RuntimeException failure) {
            throw new BatchFailure(SOURCE_MAP_UNAVAILABLE);
        }
        final IUndoHandler undoHandler = prevalidated.map.getExtension(IUndoHandler.class);
        if (undoHandler == null) {
            throw new BatchFailure(SOURCE_MAP_UNDO_UNAVAILABLE);
        }
        return new PreparedMap(prevalidated.mapId, prevalidated.map, undoHandler, prevalidated.connectors,
            createdView, prevalidated.undoSource);
    }

    private void rollbackStarted(final List<PreparedMap> maps, final SourceNodeKey previousUndoSource) {
        for (int index = maps.size() - 1; index >= 0; index--) {
            final PreparedMap prepared = maps.get(index);
            if (prepared.transactionStarted) {
                try {
                    prepared.undoHandler.rollback();
                }
                catch (final RuntimeException ignored) {
                    // Continue restoring every other map transaction.
                }
                prepared.transactionStarted = false;
            }
        }
        undoSource = previousUndoSource;
    }

    private GraphCommandResult appliedForMaps(final String messageKey, final Set<MapReferenceId> maps,
            final boolean editorViewActivated) {
        GraphCommandResult result = GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(),
            messageKey)).withDirtySourceMaps(maps);
        return editorViewActivated ? result.withEditorViewActivated(true) : result;
    }

    private GraphCommandResult rejectedTransaction(final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(results.currentDocument(), messageKey));
    }

    private static final Comparator<MapReferenceId> MAP_ID_ORDER = new Comparator<MapReferenceId>() {
        @Override
        public int compare(final MapReferenceId first, final MapReferenceId second) {
            return first.value().toString().compareTo(second.value().toString());
        }
    };

    private static final class BatchFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String messageKey;

        private BatchFailure(final String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private static final class PreparedSource {
        private final SourceNodeKey sourceKey;
        private final NodeModel node;
        private final ConnectorDescriptor expected;
        private final int occurrence;

        private PreparedSource(final SourceNodeKey sourceKey, final NodeModel node,
                final ConnectorDescriptor expected, final int occurrence) {
            this.sourceKey = sourceKey;
            this.node = node;
            this.expected = expected;
            this.occurrence = occurrence;
        }
    }

    private static final class PrevalidatedMap {
        private final MapReferenceId mapId;
        private final MapModel map;
        private final List<ConnectorModel> connectors;
        private final SourceNodeKey undoSource;

        private PrevalidatedMap(final MapReferenceId mapId, final MapModel map,
                final List<ConnectorModel> connectors, final SourceNodeKey undoSource) {
            this.mapId = mapId;
            this.map = map;
            this.connectors = connectors;
            this.undoSource = undoSource;
        }
    }

    private static final class PreparedMap {
        private final MapReferenceId mapId;
        private final MapModel map;
        private final IUndoHandler undoHandler;
        private final List<ConnectorModel> connectors;
        private final boolean editorViewActivated;
        private final SourceNodeKey undoSource;
        private boolean transactionStarted;

        private PreparedMap(final MapReferenceId mapId, final MapModel map, final IUndoHandler undoHandler,
                final List<ConnectorModel> connectors, final boolean editorViewActivated,
                final SourceNodeKey undoSource) {
            this.mapId = mapId;
            this.map = map;
            this.undoHandler = undoHandler;
            this.connectors = connectors;
            this.editorViewActivated = editorViewActivated;
            this.undoSource = undoSource;
        }
    }

    private final class NativeDeletionTransaction implements ContributorDeletionTransaction {
        private final List<PreparedMap> maps;
        private final GraphCommandResult outcome;
        private final Set<MapReferenceId> dirtyMaps;
        private final SourceNodeKey previousUndoSource;
        private final SourceNodeKey ownerUndoSource;
        private boolean open;

        private NativeDeletionTransaction(final List<PreparedMap> maps, final GraphCommandResult outcome,
                final Set<MapReferenceId> dirtyMaps, final SourceNodeKey previousUndoSource,
                final SourceNodeKey ownerUndoSource) {
            this.maps = maps;
            this.outcome = outcome;
            this.dirtyMaps = Collections.unmodifiableSet(new LinkedHashSet<MapReferenceId>(dirtyMaps));
            this.previousUndoSource = previousUndoSource;
            this.ownerUndoSource = ownerUndoSource;
            this.open = !maps.isEmpty();
        }

        @Override
        public GraphCommandResult outcome() {
            return outcome;
        }

        @Override
        public Set<MapReferenceId> dirtySourceMaps() {
            return dirtyMaps;
        }

        @Override
        public boolean editorViewActivated() {
            return outcome.editorViewActivated();
        }

        @Override
        public void commit() {
            requireEdt();
            if (!open) {
                return;
            }
            try {
                for (final PreparedMap map : maps) {
                    map.undoHandler.commit();
                    map.transactionStarted = false;
                }
                open = false;
                undoSource = ownerUndoSource;
            }
            catch (final RuntimeException failure) {
                rollback();
                throw failure;
            }
        }

        @Override
        public void rollback() {
            requireEdt();
            if (!open) {
                return;
            }
            rollbackStarted(maps, previousUndoSource);
            open = false;
        }
    }

    private GraphCommandResult createConnectorOnEdt(final SourceNodeKey sourceKey, final SourceNodeKey targetKey,
            final RelationshipDirection direction) {
        if (!sourceKey.mapReferenceId().equals(targetKey.mapReferenceId())) {
            return rejected(CONNECTOR_SAME_MAP_REQUIRED);
        }
        final Optional<MapLease> lease = activeLease(sourceKey.mapReferenceId());
        if (!lease.isPresent()) {
            return rejected(SOURCE_MAP_UNAVAILABLE);
        }
        final Optional<NodeModel> source = resolveAttached(lease.get(), sourceKey);
        final Optional<NodeModel> target = resolveAttached(lease.get(), targetKey);
        if (!source.isPresent() || !target.isPresent()) {
            return rejected(SOURCE_NODE_NOT_FOUND);
        }
        if (source.get().getMap() != target.get().getMap()) {
            return rejected(CONNECTOR_SAME_MAP_REQUIRED);
        }
        if (source.get() == target.get()) {
            return rejected(CONNECTOR_SELF_CONNECTION);
        }
        final MapModel map = source.get().getMap();
        if (!editable(map)) {
            return rejected(SOURCE_MAP_READ_ONLY);
        }
        final boolean createdView = views.materialize(sourceKey.mapReferenceId(), map);
        final IUndoHandler undoHandler = map.getExtension(IUndoHandler.class);
        if (undoHandler == null) {
            return rejected(SOURCE_MAP_UNDO_UNAVAILABLE);
        }
        final String targetId = target.get().getID();
        if (targetId == null) {
            return rejected(CONNECTOR_TARGET_REQUIRES_SAVED_ID);
        }
        boolean transactionStarted = false;
        try {
            undoHandler.startTransaction();
            transactionStarted = true;
            final ConnectorModel connector = connectors.addConnector(source.get(), targetId);
            connectors.changeArrows(connector, arrowsFor(direction));
            undoHandler.commit();
            undoSource = sourceKey;
            return applied(CONNECTOR_CREATED, sourceKey.mapReferenceId(), createdView);
        }
        catch (final RuntimeException failure) {
            if (transactionStarted) {
                undoHandler.rollback();
            }
            return rejected(SOURCE_MAP_UNAVAILABLE);
        }
    }

    private GraphCommandResult deleteConnectorOnEdt(final ContributorKey key, final ConnectorDescriptor expected) {
        if (!key.isNativeConnector() || !key.mapReferenceId().get().equals(expected.source().mapReferenceId())
                || !key.source().get().equals(expected.source())) {
            return rejected(CONNECTOR_CHANGED);
        }
        final MapReferenceId mapId = key.mapReferenceId().get();
        final SourceNodeKey sourceKey = key.source().get();
        final Optional<MapLease> lease = activeLease(mapId);
        if (!lease.isPresent()) {
            return rejected(SOURCE_MAP_UNAVAILABLE);
        }
        final Optional<NodeModel> source = resolveAttached(lease.get(), sourceKey);
        if (!source.isPresent()) {
            return rejected(SOURCE_NODE_NOT_FOUND);
        }
        final MapModel map = source.get().getMap();
        if (!editable(map)) {
            return rejected(SOURCE_MAP_READ_ONLY);
        }
        final boolean createdView = views.materialize(mapId, map);
        final IUndoHandler undoHandler = map.getExtension(IUndoHandler.class);
        if (undoHandler == null) {
            return rejected(SOURCE_MAP_UNDO_UNAVAILABLE);
        }
        final Optional<ConnectorModel> connector = connectorAt(source.get(), key.occurrence().getAsInt());
        if (!connector.isPresent() || !matches(sourceKey, mapId, connector.get(), expected)) {
            return rejected(CONNECTOR_CHANGED);
        }
        boolean transactionStarted = false;
        try {
            undoHandler.startTransaction();
            transactionStarted = true;
            connectors.removeArrowLink(connector.get());
            undoHandler.commit();
            undoSource = sourceKey;
            return applied(CONNECTOR_DELETED, mapId, createdView);
        }
        catch (final RuntimeException failure) {
            if (transactionStarted) {
                undoHandler.rollback();
            }
            return rejected(CONNECTOR_CHANGED);
        }
    }

    private Optional<UndoContext> currentUndoContextOnEdt() {
        if (undoSource == null) {
            return Optional.empty();
        }
        final Optional<MapLease> lease = activeLease(undoSource.mapReferenceId());
        if (!lease.isPresent()) {
            return Optional.empty();
        }
        final Optional<NodeModel> source = resolveAttached(lease.get(), undoSource);
        if (!source.isPresent()) {
            return Optional.empty();
        }
        final MapModel map = source.get().getMap();
        return Optional.of(new UndoContext(undoSource.mapReferenceId(), map, map.getExtension(IUndoHandler.class)));
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

    private boolean editable(final MapModel map) {
        return !map.isReadOnly() && modeController.canEdit(map);
    }

    private Optional<ConnectorModel> connectorAt(final NodeModel source, final int requestedOccurrence) {
        final NodeLinks links = NodeLinks.getLinkExtension(source);
        if (links == null) {
            return Optional.empty();
        }
        int occurrence = 0;
        for (final NodeLinkModel link : links.getLinks()) {
            if (link instanceof ConnectorModel) {
                if (occurrence == requestedOccurrence) {
                    return Optional.of((ConnectorModel) link);
                }
                occurrence++;
            }
        }
        return Optional.empty();
    }

    private boolean matches(final SourceNodeKey sourceKey, final MapReferenceId mapId, final ConnectorModel connector,
            final ConnectorDescriptor expected) {
        final String targetId = connector.getTargetID();
        if (targetId == null) {
            return false;
        }
        try {
            final ConnectorArrows arrows = connector.getArrows().orElse(ConnectorArrows.DEFAULT);
            final ConnectorDescriptor actual = ConnectorDescriptor.of(sourceKey,
                NodeReference.of(mapId, PersistedNodeId.of(targetId)), arrows.start != ArrowType.NONE,
                arrows.end != ArrowType.NONE, normalizedLabel(connector.getSourceLabel()),
                normalizedLabel(connector.getMiddleLabel()), normalizedLabel(connector.getTargetLabel()));
            return actual.equals(expected);
        }
        catch (final IllegalArgumentException failure) {
            return false;
        }
    }

    private static String normalizedLabel(final Optional<String> label) {
        if (!label.isPresent()) {
            return "";
        }
        final String raw = label.get();
        final StringBuilder normalized = new StringBuilder(raw.length());
        boolean inLineBreakRun = false;
        for (int index = 0; index < raw.length(); index++) {
            final char character = raw.charAt(index);
            if (character == '\r' || character == '\n') {
                inLineBreakRun = true;
            }
            else {
                if (inLineBreakRun) {
                    normalized.append(' ');
                    inLineBreakRun = false;
                }
                normalized.append(character);
            }
        }
        if (inLineBreakRun) {
            normalized.append(' ');
        }
        return normalized.toString();
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

    private static ConnectorArrows arrowsFor(final RelationshipDirection direction) {
        if (direction == RelationshipDirection.UNDIRECTED) {
            return ConnectorArrows.NONE;
        }
        if (direction == RelationshipDirection.BIDIRECTIONAL) {
            return ConnectorArrows.BOTH;
        }
        return ConnectorArrows.FORWARD;
    }

    private GraphCommandResult applied(final String messageKey, final MapReferenceId dirtyMap,
            final boolean editorViewActivated) {
        GraphCommandResult result = GraphCommandResult.from(WorkspaceTransition.applied(results.currentDocument(),
            messageKey)).withDirtySourceMaps(Collections.singleton(dirtyMap));
        if (editorViewActivated) {
            result = result.withEditorViewActivated(true);
        }
        return result;
    }

    private GraphCommandResult noOp(final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.noOp(results.currentDocument(), messageKey));
    }

    private GraphCommandResult rejected(final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(results.currentDocument(), messageKey));
    }

    private static final class UndoContext {
        private final MapReferenceId mapId;
        private final MapModel map;
        private final IUndoHandler undoHandler;

        private UndoContext(final MapReferenceId mapId, final MapModel map, final IUndoHandler undoHandler) {
            this.mapId = mapId;
            this.map = map;
            this.undoHandler = undoHandler;
        }
    }

    private static final class TraversalNodeResolverAdapter implements TraversalResolver {
        private final TraversalNodeResolver resolver = new TraversalNodeResolver();

        @Override
        public Optional<NodeModel> resolve(final MapLease lease, final SourceNodeKey key) {
            return resolver.resolve(lease, key);
        }
    }

    private static final class WorkspaceResultEnvelope implements ResultEnvelope {
        private final GraphWorkspaceStore workspace;

        private WorkspaceResultEnvelope(final GraphWorkspaceStore workspace) {
            this.workspace = Objects.requireNonNull(workspace, "workspace");
        }

        @Override
        public WorkspaceDocument currentDocument() {
            return workspace.currentDocument();
        }
    }

    private static final class MLinkNativeConnector implements NativeConnector {
        private final MLinkController controller;

        private MLinkNativeConnector(final ModeController modeController) {
            final ModeController owner = Objects.requireNonNull(modeController, "modeController");
            final LinkController linkController = LinkController.getController(owner);
            if (!(linkController instanceof MLinkController)) {
                throw new IllegalArgumentException("Graph commands require the MindMap link controller");
            }
            controller = (MLinkController) linkController;
        }

        @Override
        public ConnectorModel addConnector(final NodeModel source, final String targetId) {
            return controller.addConnector(source, targetId);
        }

        @Override
        public void changeArrows(final ConnectorModel connector, final ConnectorArrows arrows) {
            controller.changeArrowsOfArrowLink(connector, Optional.of(arrows));
        }

        @Override
        public void removeArrowLink(final ConnectorModel connector) {
            controller.removeArrowLink(connector);
        }
    }
}

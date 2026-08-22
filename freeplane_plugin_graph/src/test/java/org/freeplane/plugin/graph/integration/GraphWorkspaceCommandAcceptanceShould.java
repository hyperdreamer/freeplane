package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphInteractionController;
import org.freeplane.plugin.graph.canvas.GraphInteractionListener;
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.canvas.GraphSearchModel;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.command.ContributorDeletionHandler;
import org.freeplane.plugin.graph.command.DefaultContributorDeletionHandler;
import org.freeplane.plugin.graph.command.DefaultPurgeCommandHandler;
import org.freeplane.plugin.graph.command.FreeplaneMapCommandExecutor;
import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.command.PurgeCommandHandler;
import org.freeplane.plugin.graph.command.SourceNavigation;
import org.freeplane.plugin.graph.command.ViewMaterializationTracker;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceMapCoordinator;
import org.freeplane.plugin.graph.control.WorkspaceSessionId;
import org.freeplane.plugin.graph.control.WorkspaceSessionRegistry;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutConflict;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RecoverableReason;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommand;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceCommandAcceptanceShould {
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000001");
    private static final WorkspaceSessionId SESSION =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000011");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000012");
    private static final MapReferenceId MAP_THREE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000013");
    private static final RelationshipId RELATIONSHIP =
        RelationshipId.of("00000000-0000-0000-0000-000000000021");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void scenario08RoutesSameMapNativeConnectorAndMapUndoThroughTheHandle() {
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE_ID);
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.currentDocument()).thenReturn(document);
        final FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        final SourceNodeKey source = source(MAP_ONE, "source");
        final SourceNodeKey target = source(MAP_ONE, "target");
        final GraphCommandResult created = applied(document, "graph_workspace.connector.created")
            .withDirtySourceMaps(Collections.singleton(MAP_ONE)).withEditorViewActivated(true);
        final GraphCommandResult undone = applied(document, "graph_workspace.source_map.undone")
            .withDirtySourceMaps(Collections.singleton(MAP_ONE));
        when(maps.createConnector(same(source), same(target), same(RelationshipDirection.BIDIRECTIONAL)))
            .thenReturn(created);
        when(maps.undoCurrentSourceMap()).thenReturn(undone);
        final GraphWorkspaceHandle handle = handle(store, maps, new WorkspaceSessionRegistry(),
            mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

        final GraphCommandResult createResult = handle.execute(GraphCommands.connect(source, target,
            RelationshipDirection.BIDIRECTIONAL));
        final GraphCommandResult undoResult = handle.execute(GraphCommands.undoSourceMap());

        assertApplied(createResult);
        assertThat(createResult.dirtySourceMaps()).containsExactly(MAP_ONE);
        assertThat(createResult.editorViewActivated()).isTrue();
        assertApplied(undoResult);
        assertThat(undoResult.dirtySourceMaps()).containsExactly(MAP_ONE);
        verify(maps).createConnector(source, target, RelationshipDirection.BIDIRECTIONAL);
        verify(maps).undoCurrentSourceMap();
    }

    @Test
    public void scenario09RejectsCrossMapNativeConnectorsAndStoresOnlyFpgRelationships() throws Exception {
        final StoreScope scope = newStore("scenario-09");
        try {
            final FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
            final SourceNodeKey source = source(MAP_ONE, "source");
            final SourceNodeKey target = source(MAP_TWO, "target");
            when(maps.createConnector(same(source), same(target), same(RelationshipDirection.FORWARD)))
                .thenAnswer(invocation -> rejected(scope.store.currentDocument(),
                    "graph_workspace.connector.same_map_required"));
            final GraphWorkspaceHandle handle = handle(scope.store, maps, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertRejected(handle.execute(GraphCommands.connect(source, target, RelationshipDirection.FORWARD)),
                "graph_workspace.connector.same_map_required");
            assertApplied(handle.execute(GraphCommands.createRelationship(RELATIONSHIP, node(MAP_ONE, "source"),
                node(MAP_TWO, "target"), RelationshipDirection.FORWARD)));

            assertThat(scope.store.currentDocument().relationships()).extracting(GraphRelationshipRecord::id)
                .containsExactly(RELATIONSHIP);
            verify(maps).createConnector(source, target, RelationshipDirection.FORWARD);
        }
        finally {
            scope.close();
        }
    }

    @Test
    public void scenario11RoutesEndpointDeletionToMapUndoAndReactivatesTheMap() throws Exception {
        final StoreScope scope = newStore("scenario-11");
        try {
            final FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
            final ContributorDeletionHandler deletion = mock(ContributorDeletionHandler.class);
            final SourceNodeKey source = source(MAP_ONE, "source");
            final ContributorKey contributor = ContributorKey.nativeConnector(MAP_ONE, source, 0);
            final GraphCommandResult deleted = applied(scope.store.currentDocument(),
                "graph_workspace.connector.deleted")
                .withDirtySourceMaps(Collections.singleton(MAP_ONE));
            final GraphCommandResult undone = applied(scope.store.currentDocument(),
                "graph_workspace.source_map.undone")
                .withDirtySourceMaps(Collections.singleton(MAP_ONE));
            when(deletion.deleteOne(any(GraphCommands.DeleteContributor.class))).thenReturn(deleted);
            when(maps.undoCurrentSourceMap()).thenReturn(undone);
            final GraphWorkspaceHandle handle = handle(scope.store, maps, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), deletion);

            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertApplied(handle.execute(GraphCommands.deleteContributor(7L, contributor, null)));
            assertApplied(handle.execute(GraphCommands.undoSourceMap()));
            assertApplied(handle.execute(GraphCommands.removeMap(MAP_TWO)));
            assertThat(mapReference(scope.store.currentDocument(), MAP_TWO).active()).isFalse();
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));

            assertThat(mapReference(scope.store.currentDocument(), MAP_TWO).active()).isTrue();
            verify(deletion).deleteOne(any(GraphCommands.DeleteContributor.class));
            verify(maps).undoCurrentSourceMap();
        }
        finally {
            scope.close();
        }
    }

    @Test
    public void scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect() {
        final CanvasFixture fixture = CanvasFixture.create();
        final GraphCanvas canvas = fixture.canvas;
        final RecordingIntentListener listener = new RecordingIntentListener();
        final GraphInteractionController interaction = new GraphInteractionController(listener);
        interaction.install(canvas);
        try {
            final GraphViewport initial = canvas.viewport();
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_PRESSED, 320, 80, 1, MouseEvent.BUTTON1));
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_DRAGGED, 360, 80, 1, MouseEvent.BUTTON1));
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_RELEASED, 360, 80, 1, MouseEvent.BUTTON1));
            assertThat(canvas.viewport().centerX()).isNotEqualTo(initial.centerX());

            dispatch(canvas, wheel(canvas, 200, 150, -1));
            assertThat(canvas.viewport().zoom()).isGreaterThan(initial.zoom());
            canvas.fitGraph();
            assertThat(canvas.viewport().centerX()).isEqualTo(0.0);
            canvas.resetZoom();
            assertThat(canvas.viewport()).isEqualTo(GraphViewport.of(0.0, 0.0, 1.0));

            assertThat(GraphSearchModel.search(fixture.state, "full safe label"))
                .containsExactly(fixture.firstEndpoint);
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_MOVED, 160, 150, 0, MouseEvent.NOBUTTON));
            assertThat(canvas.getToolTipText()).contains("First full safe label").contains("One");
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_CLICKED, 160, 150, 1, MouseEvent.BUTTON1));
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_CLICKED, 160, 150, 2, MouseEvent.BUTTON1));
            dispatch(canvas, mouse(canvas, MouseEvent.MOUSE_PRESSED, 200, 150, 1, MouseEvent.BUTTON3));

            assertThat(listener.intents).contains(new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.OpenSourceNode(fixture.firstEndpoint), new GraphIntent.InspectEdge(fixture.edge.key()));
        }
        finally {
            interaction.uninstall();
        }
    }

    @Test
    public void scenario16RejectsIdlessPersistentCommandAtomicallyThenAcceptsNormalSavedId() {
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE_ID);
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.currentDocument()).thenReturn(document);
        final FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        final SourceNodeKey source = source(MAP_ONE, "source");
        final SourceNodeKey idlessTarget = SourceNodeKey.transientPath(MAP_ONE,
            Collections.singletonList(Integer.valueOf(1)));
        final SourceNodeKey savedTarget = source(MAP_ONE, "ordinary-file-id");
        when(maps.createConnector(same(source), same(idlessTarget), same(RelationshipDirection.FORWARD)))
            .thenReturn(rejected(document, "graph_workspace.connector.target_requires_saved_id"));
        when(maps.createConnector(same(source), same(savedTarget), same(RelationshipDirection.FORWARD)))
            .thenReturn(applied(document, "graph_workspace.connector.created"));
        final GraphWorkspaceHandle handle = handle(store, maps, new WorkspaceSessionRegistry(),
            mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

        final GraphCommandResult rejected = handle.execute(GraphCommands.connect(source, idlessTarget,
            RelationshipDirection.FORWARD));
        final GraphCommandResult applied = handle.execute(GraphCommands.connect(source, savedTarget,
            RelationshipDirection.FORWARD));

        assertRejected(rejected, "graph_workspace.connector.target_requires_saved_id");
        assertApplied(applied);
        verify(maps).createConnector(source, idlessTarget, RelationshipDirection.FORWARD);
        verify(maps).createConnector(source, savedTarget, RelationshipDirection.FORWARD);
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void scenario17KeepsDenseThreeMapSourcesDistinct() {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (MapReferenceId map : Arrays.asList(MAP_ONE, MAP_TWO, MAP_THREE)) {
            for (int index = 0; index < 6; index++) {
                final SourceNodeKey source = source(map, "shared-" + index);
                nodes.add(ProjectedNode.of(ProjectedNodeKey.of(source), SafeNodeLabel.of("safe " + map + index,
                    "safe"), "Map " + map, false));
            }
        }
        final GraphProjection projection = GraphProjection.structure(4L, nodes, Collections.emptyList());

        assertThat(projection.nodes()).hasSize(18);
        assertThat(projection.nodes()).extracting(ProjectedNode::mapReferenceId)
            .contains(MAP_ONE, MAP_TWO, MAP_THREE);
        assertThat(projection.nodes()).extracting(ProjectedNode::key).doesNotHaveDuplicates();
    }

    @Test
    public void scenario20RetainsPinnedConflictUntilExplicitUnpin() throws Exception {
        final StoreScope scope = newStore("scenario-20");
        try {
            final GraphWorkspaceHandle handle = handle(scope.store, mock(FreeplaneMapCommandExecutor.class),
                new WorkspaceSessionRegistry(), mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));
            final NodeReference pinned = node(MAP_ONE, "pinned");
            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertApplied(handle.execute(GraphCommands.pin(pinned, 12.0, -8.0)));
            final PinProjection pin = PinProjection.active(PinRecord.of(pinned, 12.0, -8.0,
                Collections.emptyList()), ProjectedNodeKey.of(SourceNodeKey.persisted(pinned)));
            final LayoutConflict conflict = LayoutConflict.of(MAP_ONE, MAP_TWO, Collections.singletonList(pin));

            assertThat(conflict.blockingPins()).containsExactly(pin);
            assertThat(scope.store.currentDocument().pins()).hasSize(1);
            assertApplied(handle.execute(GraphCommands.unpin(pinned)));
            assertThat(scope.store.currentDocument().pins()).isEmpty();
        }
        finally {
            scope.close();
        }
    }

    @Test
    public void scenario21DoesNotLeakLockedContentAndRejectsLockedRelationshipPurge() {
        final CanvasFixture fixture = CanvasFixture.create();
        assertThat(GraphSearchModel.search(fixture.state, "LOCKED_SECRET_SENTINEL")).isEmpty();
        assertThat(GraphSearchModel.tooltip(fixture.state, fixture.firstEndpoint))
            .doesNotContain("LOCKED_SECRET_SENTINEL");

        final GraphProjection locked = GraphProjection.resolved(7L, fixture.state.projection().nodes(),
            Collections.emptyList(), Collections.singletonList(RelationshipResolution.of(relationship(),
                RelationshipStatus.UNRESOLVED_RECOVERABLE, Optional.of(fixture.firstEndpoint),
                Optional.<ProjectedEndpointKey>empty(), Collections.singleton(RecoverableReason.NODE_INACCESSIBLE))),
            Collections.emptyList());
        final GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.currentDocument()).thenReturn(documentWithRelationship());
        when(updates.currentProjection()).thenReturn(locked);
        when(updates.currentState()).thenReturn(stateFor(locked));
        when(updates.hasPendingChanges()).thenReturn(false);
        final DefaultPurgeCommandHandler purge = new DefaultPurgeCommandHandler(updates, store, new InlineEdt());

        assertRejected(purge.purge(GraphCommands.purge(7L, Collections.singleton(RELATIONSHIP))),
            "graph_workspace.purge.relationship_not_missing");
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void scenario22RejectsStalePendingAndChangedContributorRequestsBeforeMutation() {
        final SourceNodeKey source = source(MAP_ONE, "source");
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), false, true,
            "source", "middle", "target");
        final ContributorKey contributor = ContributorKey.nativeConnector(MAP_ONE, source, 0);
        final GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        final FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        when(store.currentDocument()).thenReturn(documentWithRelationship());
        final DefaultContributorDeletionHandler deletion = new DefaultContributorDeletionHandler(updates, store, maps,
            new InlineEdt());
        final GraphProjection stale = nativeContributorProjection(9L, descriptor);
        when(updates.currentProjection()).thenReturn(stale);
        when(updates.currentState()).thenReturn(stateFor(stale));
        when(updates.hasPendingChanges()).thenReturn(false);

        assertRejected(deletion.deleteOne(GraphCommands.deleteContributor(8L, contributor, descriptor)),
            "graph_workspace.contributor.stale");
        verifyNoInteractions(maps);

        final GraphProjection current = nativeContributorProjection(8L, descriptor);
        when(updates.currentProjection()).thenReturn(current);
        when(updates.currentState()).thenReturn(stateFor(current));
        when(updates.hasPendingChanges()).thenReturn(true);
        assertRejected(deletion.deleteOne(GraphCommands.deleteContributor(8L, contributor, descriptor)),
            "graph_workspace.contributor.pending");
        verifyNoInteractions(maps);

        when(updates.hasPendingChanges()).thenReturn(false);
        final ConnectorDescriptor changed = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), true, true,
            "source", "middle", "target");
        assertRejected(deletion.deleteOne(GraphCommands.deleteContributor(8L, contributor, changed)),
            "graph_workspace.contributor.changed");
        verifyNoInteractions(maps);
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void scenario22RejectsStaleAndPendingPurgeThenUndoesMissingPurge() throws Exception {
        final GraphProjection missing = missingProjection(8L);
        final GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        final GraphWorkspaceStore rejectedStore = mock(GraphWorkspaceStore.class);
        when(rejectedStore.currentDocument()).thenReturn(documentWithRelationship());
        when(updates.currentProjection()).thenReturn(missing);
        when(updates.currentState()).thenReturn(stateFor(missing));
        when(updates.hasPendingChanges()).thenReturn(true);
        final DefaultPurgeCommandHandler rejectedPurge = new DefaultPurgeCommandHandler(updates, rejectedStore,
            new InlineEdt());

        assertRejected(rejectedPurge.purge(GraphCommands.purge(7L, Collections.singleton(RELATIONSHIP))),
            "graph_workspace.purge.stale");
        assertRejected(rejectedPurge.purge(GraphCommands.purge(8L, Collections.singleton(RELATIONSHIP))),
            "graph_workspace.purge.pending");
        verify(rejectedStore, never()).execute(any(WorkspaceCommand.class));

        final StoreScope scope = newStore("scenario-22");
        try {
            final GraphUpdateCoordinator acceptingUpdates = mock(GraphUpdateCoordinator.class);
            when(acceptingUpdates.currentProjection()).thenReturn(missing);
            when(acceptingUpdates.currentState()).thenReturn(stateFor(missing));
            when(acceptingUpdates.hasPendingChanges()).thenReturn(false);
            final DefaultPurgeCommandHandler purge = new DefaultPurgeCommandHandler(acceptingUpdates, scope.store,
                new InlineEdt());
            final GraphWorkspaceHandle handle = handle(scope.store, mock(FreeplaneMapCommandExecutor.class),
                new WorkspaceSessionRegistry(), purge, mock(ContributorDeletionHandler.class));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertApplied(handle.execute(GraphCommands.createRelationship(RELATIONSHIP, node(MAP_ONE, "one"),
                node(MAP_TWO, "two"), RelationshipDirection.FORWARD)));
            assertApplied(handle.execute(GraphCommands.purge(8L, Collections.singleton(RELATIONSHIP))));
            assertThat(scope.store.currentDocument().relationships()).isEmpty();
            assertApplied(handle.execute(GraphCommands.undoWorkspace()));

            assertThat(scope.store.currentDocument().relationships()).extracting(GraphRelationshipRecord::id)
                .containsExactly(RELATIONSHIP);
        }
        finally {
            scope.close();
        }
    }

    @Test
    public void scenario24CreatesAtMostOneEditorViewPerMapAndReusesIt() {
        final ModeController mode = mock(ModeController.class);
        final MapController mapController = mock(MapController.class);
        final Controller application = mock(Controller.class);
        final IMapViewManager views = mock(IMapViewManager.class);
        final Set<MapModel> open = Collections.newSetFromMap(new java.util.IdentityHashMap<MapModel, Boolean>());
        when(mode.getMapController()).thenReturn(mapController);
        when(mode.getController()).thenReturn(application);
        when(application.getMapViewManager()).thenReturn(views);
        when(views.containsView(any(MapModel.class))).thenAnswer(
            invocation -> open.contains(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            open.add((MapModel) invocation.getArgument(0));
            return null;
        }).when(mapController).createMapView(any(MapModel.class));
        final ViewMaterializationTracker tracker = new ViewMaterializationTracker(mode);
        final MapModel one = mock(MapModel.class);
        final MapModel two = mock(MapModel.class);
        final MapModel three = mock(MapModel.class);

        final List<MapReferenceId> ids = Arrays.asList(MAP_ONE, MAP_TWO, MAP_THREE);
        final List<MapModel> maps = Arrays.asList(one, two, three);
        for (int index = 0; index < maps.size(); index++) {
            final MapModel map = maps.get(index);
            assertThat(tracker.materialize(ids.get(index), map)).isTrue();
            assertThat(tracker.materialize(ids.get(index), map)).isFalse();
        }

        assertThat(open).containsExactlyInAnyOrder(one, two, three);
        verify(mapController, org.mockito.Mockito.times(3)).createMapView(any(MapModel.class));
    }

    @Test
    public void scenario25ProvidesMultiplicityCueOrDuplicateNoOpReason() throws Exception {
        final CanvasFixture fixture = CanvasFixture.create();
        assertThat(fixture.edge.contributorCount()).isEqualTo(2);
        assertThat(fixture.edge.hasMultiplicityCue()).isTrue();

        final StoreScope scope = newStore("scenario-25");
        try {
            final GraphWorkspaceHandle handle = handle(scope.store, mock(FreeplaneMapCommandExecutor.class),
                new WorkspaceSessionRegistry(), mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertApplied(handle.execute(GraphCommands.createRelationship(RELATIONSHIP, node(MAP_ONE, "one"),
                node(MAP_TWO, "two"), RelationshipDirection.FORWARD)));
            final GraphCommandResult duplicate = handle.execute(GraphCommands.createRelationship(
                RelationshipId.of("00000000-0000-0000-0000-000000000022"), node(MAP_ONE, "one"),
                node(MAP_TWO, "two"), RelationshipDirection.FORWARD));

            assertThat(duplicate.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
            assertThat(duplicate.messageKey()).isEqualTo("graph_workspace.relationship.duplicate");
        }
        finally {
            scope.close();
        }
    }

    @Test
    public void rejectsSaveAsLiveTargetAndKeepsSeparateWorkspaceHistories() throws Exception {
        final Path current = temporaryFolder.newFolder("save-as-current").toPath().resolve("current.fpg");
        final Path target = temporaryFolder.newFolder("save-as-target").toPath().resolve("target.fpg");
        final WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        final WorkspaceSessionId occupying = WorkspaceSessionId.of("00000000-0000-0000-0000-000000000003");
        assertThat(sessions.register(SESSION, current)).isTrue();
        assertThat(sessions.register(occupying, target)).isTrue();
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.currentDocument()).thenReturn(WorkspaceDocument.createVersion1(WORKSPACE_ID));
        final GraphWorkspaceHandle handle = handle(store, mock(FreeplaneMapCommandExecutor.class), sessions,
            mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

        assertRejected(handle.execute(GraphCommands.saveAs(target)), "graph_workspace.workspace.save_as_failed");
        verify(store, never()).saveAs(any(Path.class));

        final StoreScope first = newStore("history-first");
        final StoreScope second = newStore("history-second");
        try {
            final GraphWorkspaceHandle firstHandle = handle(first.store, mock(FreeplaneMapCommandExecutor.class),
                new WorkspaceSessionRegistry(), mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));
            assertApplied(firstHandle.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertThat(first.store.canUndo()).isTrue();
            assertThat(second.store.canUndo()).isFalse();
        }
        finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void consumesRecordedStrictPerformanceAcceptanceResult() throws Exception {
        final String contents = new String(Files.readAllBytes(repositoryFile(
            "docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md")), StandardCharsets.UTF_8);

        assertThat(contents).contains("Status: PASS");
        assertThat(contents).contains("accepted-batch-first-frame p95 `131536204` ns");
        assertThat(contents).contains("strict `150000000` ns ceiling");
    }

    private StoreScope newStore(final String directory) throws IOException {
        final Path file = temporaryFolder.newFolder(directory).toPath().resolve("workspace.fpg");
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        return new StoreScope(GraphWorkspaceStore.create(file, codec(), new FileWriter(), scheduler), scheduler);
    }

    private static GraphWorkspaceHandle handle(final GraphWorkspaceStore store,
            final FreeplaneMapCommandExecutor maps, final WorkspaceSessionRegistry sessions,
            final PurgeCommandHandler purge, final ContributorDeletionHandler deletion) {
        final GraphCommandRouter router = new GraphCommandRouter(store, mock(WorkspaceMapCoordinator.class), maps,
            mock(SourceNavigation.class), mock(GraphUpdateCoordinator.class), sessions, SESSION, purge, deletion);
        return new DefaultGraphWorkspaceHandle(router, mock(GraphUpdateCoordinator.class),
            mock(WorkspaceCloseController.class));
    }

    private static WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.emptyList()));
    }

    private static GraphCommandResult applied(final WorkspaceDocument document, final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.applied(document, messageKey));
    }

    private static GraphCommandResult rejected(final WorkspaceDocument document, final String messageKey) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(document, messageKey));
    }

    private static void assertApplied(final GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    private static void assertRejected(final GraphCommandResult result, final String messageKey) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo(messageKey);
    }

    private static SourceNodeKey source(final MapReferenceId map, final String id) {
        return SourceNodeKey.persisted(node(map, id));
    }

    private static NodeReference node(final MapReferenceId map, final String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static GraphRelationshipRecord relationship() {
        return GraphRelationshipRecord.of(RELATIONSHIP, 1L, node(MAP_ONE, "one"), node(MAP_TWO, "two"),
            RelationshipDirection.FORWARD, Collections.emptyList());
    }

    private static GraphRelationshipRecord secondRelationship() {
        return GraphRelationshipRecord.of(RelationshipId.of("00000000-0000-0000-0000-000000000022"), 2L,
            node(MAP_ONE, "one"), node(MAP_TWO, "two"), RelationshipDirection.BIDIRECTIONAL,
            Collections.emptyList());
    }

    private static WorkspaceDocument documentWithRelationship() {
        return WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(map(MAP_ONE, 1L, "one.mm"), map(MAP_TWO, 2L, "two.mm")))
            .relationships(Collections.singletonList(relationship())).build();
    }

    private static MapReference map(final MapReferenceId id, final long sequence, final String uri) {
        return MapReference.of(id, sequence, URI.create(uri), true, "#4E79A7", Collections.emptyList());
    }

    private static MapReference mapReference(final WorkspaceDocument document, final MapReferenceId id) {
        for (MapReference reference : document.maps()) {
            if (reference.id().equals(id)) {
                return reference;
            }
        }
        throw new AssertionError("Missing map " + id);
    }

    private static GraphProjection missingProjection(final long generation) {
        return GraphProjection.resolved(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(RelationshipResolution.of(relationship(),
                RelationshipStatus.UNRESOLVED_MISSING_NODE, Optional.<ProjectedEndpointKey>empty(),
                Optional.<ProjectedEndpointKey>empty(), Collections.emptySet())),
            Collections.emptyList());
    }

    private static GraphProjection nativeContributorProjection(final long generation,
            final ConnectorDescriptor descriptor) {
        final ProjectedNodeKey sourceKey = ProjectedNodeKey.of(descriptor.source());
        final ProjectedNodeKey targetKey = ProjectedNodeKey.of(SourceNodeKey.persisted(descriptor.target()));
        final ProjectedEndpointKey sourceEndpoint = ProjectedEndpointKey.ofNode(sourceKey);
        final ProjectedEndpointKey targetEndpoint = ProjectedEndpointKey.ofNode(targetKey);
        final ProjectedNode source = ProjectedNode.of(sourceKey, SafeNodeLabel.of("source", "source"), "One", false);
        final ProjectedNode target = ProjectedNode.of(targetKey, SafeNodeLabel.of("target", "target"), "One", false);
        final ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
            Collections.singletonList(EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor),
                sourceEndpoint, targetEndpoint)));
        return GraphProjection.projected(generation, Arrays.asList(source, target), Collections.emptyList(),
            Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());
    }

    private static CanvasState stateFor(final GraphProjection projection) {
        return CanvasState.of(projection.generation(), projection,
            LayoutFrame.of(projection.generation(), LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()),
                false), GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.IDLE);
    }

    private static void dispatch(final GraphCanvas canvas, final java.awt.AWTEvent event) {
        canvas.dispatchEvent(event);
    }

    private static MouseEvent mouse(final GraphCanvas canvas, final int eventId, final int x, final int y,
            final int count, final int button) {
        return new MouseEvent(canvas, eventId, System.currentTimeMillis(), 0, x, y, count, false, button);
    }

    private static java.awt.event.MouseWheelEvent wheel(final GraphCanvas canvas, final int x, final int y,
            final int rotation) {
        return new java.awt.event.MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
            x, y, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    private static Path repositoryFile(final String relativePath) {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath);
    }

    private static final class StoreScope implements AutoCloseable {
        private final GraphWorkspaceStore store;
        private final ScheduledExecutorService scheduler;

        private StoreScope(final GraphWorkspaceStore store, final ScheduledExecutorService scheduler) {
            this.store = store;
            this.scheduler = scheduler;
        }

        @Override
        public void close() {
            store.discardAndClose();
            scheduler.shutdownNow();
        }
    }

    private static final class FileWriter implements AtomicWorkspaceWriter {
        @Override
        public void write(final Path target, final byte[] bytes) {
            try {
                Files.write(target, bytes);
            }
            catch (final IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class CanvasFixture {
        private final CanvasState state;
        private final GraphCanvas canvas;
        private final ProjectedEndpointKey firstEndpoint;
        private final ProjectedEdge edge;

        private CanvasFixture(final CanvasState state, final GraphCanvas canvas,
                final ProjectedEndpointKey firstEndpoint, final ProjectedEdge edge) {
            this.state = state;
            this.canvas = canvas;
            this.firstEndpoint = firstEndpoint;
            this.edge = edge;
        }

        private static CanvasFixture create() {
            final ProjectedNodeKey firstKey = ProjectedNodeKey.of(source(MAP_ONE, "LOCKED_SECRET_SENTINEL"));
            final ProjectedNodeKey secondKey = ProjectedNodeKey.of(source(MAP_TWO, "two"));
            final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(firstKey);
            final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(secondKey);
            final ProjectedNode first = ProjectedNode.of(firstKey, SafeNodeLabel.of("First full safe label", "First"),
                "One", false);
            final ProjectedNode second = ProjectedNode.of(secondKey,
                SafeNodeLabel.of("Second isolated label", "Second"),
                "Two", false);
            final ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(firstEndpoint, secondEndpoint),
                Arrays.asList(EdgeContributor.graphRelationship(relationship(), firstEndpoint, secondEndpoint),
                    EdgeContributor.graphRelationship(secondRelationship(), firstEndpoint, secondEndpoint)));
            final GraphProjection projection = GraphProjection.projected(7L, Arrays.asList(first, second),
                Collections.emptyList(), Collections.singletonList(edge), Collections.emptyList(),
                Collections.emptyList());
            final Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            geometry.put(firstKey, NodeGeometry.of(LayoutPoint.of(-40.0, 0.0), 10.0));
            geometry.put(secondKey, NodeGeometry.of(LayoutPoint.of(40.0, 0.0), 10.0));
            final Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            positions.put(firstKey, LayoutPoint.of(-40.0, 0.0));
            positions.put(secondKey, LayoutPoint.of(40.0, 0.0));
            final CanvasState state = CanvasState.of(7L, projection,
                LayoutFrame.of(7L, LayoutPositions.of(positions, Collections.emptyMap()), false),
                GraphGeometry.of(geometry, Collections.emptyMap()), OperationalStatus.IDLE);
            final GraphCanvas canvas = new GraphCanvas();
            canvas.setSize(new Dimension(400, 300));
            canvas.setCanvasState(state);
            return new CanvasFixture(state, canvas, firstEndpoint, edge);
        }
    }

    private static final class RecordingIntentListener implements GraphInteractionListener {
        private final List<GraphIntent> intents = new ArrayList<GraphIntent>();

        @Override
        public void onGraphIntent(final GraphIntent intent) {
            intents.add(intent);
        }
    }

    private static final class InlineEdt implements EdtExecutor {
        private boolean onEdt;

        @Override
        public <T> T call(final Callable<T> task) {
            final boolean previous = onEdt;
            onEdt = true;
            try {
                return task.call();
            }
            catch (final RuntimeException failure) {
                throw failure;
            }
            catch (final Exception failure) {
                throw new AssertionError(failure);
            }
            finally {
                onEdt = previous;
            }
        }

        @Override
        public void execute(final Runnable task) {
            task.run();
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }
    }
}

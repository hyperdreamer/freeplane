package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.event.ChangeListener;

import org.freeplane.core.io.WriteManager;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.link.ConnectorArrows;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.MapWriter;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.clipboard.MapClipboardController.CopiedNodeSet;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
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
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;
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

    private MockedStatic<ResourceController> resourceControllers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUpNativeModelStatics() {
        resourceControllers = org.mockito.Mockito.mockStatic(ResourceController.class);
        final ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(any(String.class))).thenReturn("");
        resourceControllers.when(ResourceController::getResourceController)
            .thenReturn(resourceController);
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDownNativeModelStatics() {
        if (textUtils != null) {
            textUtils.close();
        }
        if (resourceControllers != null) {
            resourceControllers.close();
        }
    }

    @Test
    public void scenario08RoutesSameMapNativeConnectorAndMapUndoThroughTheHandle() throws Exception {
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE_ID);
        final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.currentDocument()).thenReturn(document);
        final NativeFixture fixture = new NativeFixture(store,
            temporaryFolder.newFile("scenario-08-native.fpg").toPath());
        try {
            final NativeNodes nodes = fixture.addMap(MAP_ONE, "scenario 08");
            final GraphWorkspaceHandle handle = handle(store, fixture.executor, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

            final GraphCommandResult createResult = handle.execute(GraphCommands.connect(nodes.sourceKey,
                nodes.targetKey, RelationshipDirection.BIDIRECTIONAL));
            assertApplied(createResult);
            assertThat(createResult.dirtySourceMaps()).containsExactly(MAP_ONE);
            assertThat(createResult.editorViewActivated()).isTrue();
            assertThat(nodes.connectors()).hasSize(1);
            assertThat(nodes.map.isSaved()).isFalse();
            assertThat(fixture.executor.currentUndoTarget())
                .contains(new org.freeplane.plugin.graph.command.MapUndoTarget(MAP_ONE, "scenario 08", true));

            final GraphCommandResult undoResult = handle.execute(GraphCommands.undoSourceMap());
            assertApplied(undoResult);
            assertThat(undoResult.dirtySourceMaps()).containsExactly(MAP_ONE);
            assertThat(nodes.connectors()).isEmpty();
            assertThat(nodes.map.undo.undoCalls).isEqualTo(1);
        }
        finally {
            fixture.close();
        }
    }

    @Test
    public void scenario09RejectsCrossMapNativeConnectorsAndStoresOnlyFpgRelationships() throws Exception {
        final StoreScope scope = newStore("scenario-09");
        final NativeFixture fixture = new NativeFixture(scope.store,
            temporaryFolder.newFile("scenario-09-native.fpg").toPath());
        try {
            final NativeNodes one = fixture.addMap(MAP_ONE, "scenario 09 one");
            final NativeNodes two = fixture.addMap(MAP_TWO, "scenario 09 two");
            final GraphWorkspaceHandle handle = handle(scope.store, fixture.executor, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));

            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, one.map.getURL().toURI())));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, two.map.getURL().toURI())));
            assertRejected(handle.execute(GraphCommands.connect(one.sourceKey, two.targetKey,
                RelationshipDirection.FORWARD)), "graph_workspace.connector.same_map_required");
            assertThat(one.connectors()).isEmpty();
            assertThat(two.connectors()).isEmpty();
            assertThat(fixture.executor.currentUndoTarget()).isEmpty();
            assertApplied(handle.execute(GraphCommands.createRelationship(RELATIONSHIP,
                node(MAP_ONE, one.source.getID()), node(MAP_TWO, two.target.getID()),
                RelationshipDirection.FORWARD)));

            assertThat(scope.store.currentDocument().relationships()).extracting(GraphRelationshipRecord::id)
                .containsExactly(RELATIONSHIP);
        }
        finally {
            fixture.close();
            scope.close();
        }
    }

    @Test
    public void scenario11RoutesEndpointDeletionToMapUndoAndReactivatesTheMap() throws Exception {
        final StoreScope scope = newStore("scenario-11");
        final NativeFixture fixture = new NativeFixture(scope.store,
            temporaryFolder.newFile("scenario-11-native.fpg").toPath());
        try {
            final NativeNodes one = fixture.addMap(MAP_ONE, "scenario 11 one");
            final NativeNodes two = fixture.addMap(MAP_TWO, "scenario 11 two");
            final GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
            final DefaultContributorDeletionHandler deletion = new DefaultContributorDeletionHandler(updates,
                scope.store, fixture.executor, fixture.edt);
            final GraphWorkspaceHandle handle = handle(scope.store, fixture.executor, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), deletion);

            assertApplied(handle.execute(GraphCommands.addMap(MAP_ONE, one.map.getURL().toURI())));
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, two.map.getURL().toURI())));
            assertApplied(handle.execute(GraphCommands.connect(one.sourceKey, one.targetKey,
                RelationshipDirection.FORWARD)));
            assertThat(one.connectors()).hasSize(1);
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(one.sourceKey,
                node(MAP_ONE, one.target.getID()), false, true, "", "", "");
            final ContributorKey contributor = ContributorKey.nativeConnector(MAP_ONE, one.sourceKey, 0);
            final GraphProjection projection = nativeContributorProjection(8L, descriptor);
            when(updates.currentProjection()).thenReturn(projection);
            when(updates.currentState()).thenReturn(stateFor(projection));
            when(updates.hasPendingChanges()).thenReturn(false);

            final GraphCommandResult deleted = handle.execute(GraphCommands.deleteContributor(8L, contributor,
                descriptor));
            assertApplied(deleted);
            assertThat(deleted.dirtySourceMaps()).containsExactly(MAP_ONE);
            assertThat(one.connectors()).isEmpty();
            final GraphCommandResult undone = handle.execute(GraphCommands.undoSourceMap());
            assertApplied(undone);
            assertThat(undone.dirtySourceMaps()).containsExactly(MAP_ONE);
            assertApplied(handle.execute(GraphCommands.removeMap(MAP_TWO)));
            assertThat(one.connectors()).hasSize(1);
            assertThat(one.map.undo.undoCalls).isEqualTo(1);
            assertThat(mapReference(scope.store.currentDocument(), MAP_TWO).active()).isFalse();
            assertApplied(handle.execute(GraphCommands.addMap(MAP_TWO, two.map.getURL().toURI())));

            assertThat(mapReference(scope.store.currentDocument(), MAP_TWO).active()).isTrue();
        }
        finally {
            fixture.close();
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
    public void scenario16RejectsIdlessPersistentCommandAtomicallyThenAcceptsNormalSavedId() throws Exception {
        final StoreScope scope = newStore("scenario-16");
        final NativeFixture fixture = new NativeFixture(scope.store,
            temporaryFolder.newFile("scenario-16-native.fpg").toPath());
        try {
            final NativeNodes nodes = fixture.addMap(MAP_ONE, "scenario 16", false);
            final GraphWorkspaceHandle handle = handle(scope.store, fixture.executor, new WorkspaceSessionRegistry(),
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class));
            final WorkspaceDocument before = scope.store.currentDocument();

            final GraphCommandResult rejected = handle.execute(GraphCommands.connect(nodes.sourceKey,
                nodes.targetKey, RelationshipDirection.FORWARD));

            assertRejected(rejected, "graph_workspace.connector.target_requires_saved_id");
            final Properties translations = new Properties();
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(repositoryFile(
                    "freeplane/src/viewer/resources/translations/Resources_en.properties")),
                    StandardCharsets.ISO_8859_1)) {
                translations.load(reader);
            }
            assertThat(translations.getProperty("graph_workspace.connector.target_requires_saved_id"))
                .contains("Open and save the map once, then retry");
            assertThat(nodes.target.getID()).isNull();
            assertThat(nodes.connectors()).isEmpty();
            assertThat(nodes.map.isSaved()).isTrue();
            assertThat(nodes.map.undo.undoCalls).isZero();
            assertThat(nodes.map.undo.actorCount()).isZero();
            assertThat(nodes.map.undo.canUndo()).isFalse();
            assertThat(nodes.map.undo.getTransactionLevel()).isZero();
            assertThat(scope.store.currentDocument()).isSameAs(before);
            assertThat(scope.store.canUndo()).isFalse();

            final SourceNodeKey savedTarget = nodes.saveTargetNormally();
            assertThat(nodes.target.getID()).isNotNull();
            assertThat(new String(Files.readAllBytes(nodes.mapFile), StandardCharsets.UTF_8))
                .contains(nodes.target.getID());
            assertThat(nodes.map.isSaved()).isTrue();
            assertThat(savedTarget.persistent()).isTrue();
            final GraphCommandResult applied = handle.execute(GraphCommands.connect(nodes.sourceKey, savedTarget,
                RelationshipDirection.FORWARD));

            assertApplied(applied);
            assertThat(nodes.connectors()).extracting(ConnectorModel::getTargetID)
                .containsExactly(nodes.target.getID());
            assertThat(nodes.map.isSaved()).isFalse();
            assertThat(scope.store.canUndo()).isFalse();
        }
        finally {
            fixture.close();
            scope.close();
        }
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

        final GraphUpdateCoordinator changingUpdates = mock(GraphUpdateCoordinator.class);
        final GraphWorkspaceStore changingStore = mock(GraphWorkspaceStore.class);
        final FreeplaneMapCommandExecutor changingMaps = mock(FreeplaneMapCommandExecutor.class);
        when(changingStore.currentDocument()).thenReturn(documentWithRelationship());
        final DefaultContributorDeletionHandler changingDeletion = new DefaultContributorDeletionHandler(
            changingUpdates, changingStore, changingMaps, new InlineEdt());
        final ConnectorDescriptor changed = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), true, true,
            "source", "middle", "target");
        final GraphProjection displayed = nativeContributorProjection(8L, descriptor);
        final GraphProjection changedProjection = nativeContributorProjection(8L, changed);
        when(changingUpdates.currentProjection()).thenReturn(displayed, changedProjection);
        when(changingUpdates.currentState()).thenReturn(stateFor(displayed), stateFor(changedProjection));
        when(changingUpdates.hasPendingChanges()).thenReturn(false, false);
        assertRejected(changingDeletion.deleteOne(GraphCommands.deleteContributor(8L, contributor, descriptor)),
            "graph_workspace.contributor.changed");
        verify(changingUpdates, org.mockito.Mockito.times(2)).currentProjection();
        verify(changingUpdates, org.mockito.Mockito.times(2)).currentState();
        verify(changingUpdates, org.mockito.Mockito.times(2)).hasPendingChanges();
        verifyNoInteractions(changingMaps);
        verify(changingStore, never()).executeWithCompensation(any(WorkspaceCommand.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
        verify(store, never()).executeWithCompensation(any(WorkspaceCommand.class));
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

        final GraphProjection changed = recoverableProjection(8L);
        final GraphUpdateCoordinator changingUpdates = mock(GraphUpdateCoordinator.class);
        final GraphWorkspaceStore changingStore = mock(GraphWorkspaceStore.class);
        when(changingStore.currentDocument()).thenReturn(documentWithRelationship());
        when(changingUpdates.currentProjection()).thenReturn(missing, changed);
        when(changingUpdates.currentState()).thenReturn(stateFor(missing), stateFor(changed));
        when(changingUpdates.hasPendingChanges()).thenReturn(false, false);
        final DefaultPurgeCommandHandler changingPurge = new DefaultPurgeCommandHandler(changingUpdates,
            changingStore, new InlineEdt());

        assertRejected(changingPurge.purge(GraphCommands.purge(8L, Collections.singleton(RELATIONSHIP))),
            "graph_workspace.purge.relationship_not_missing");
        verify(changingUpdates, org.mockito.Mockito.times(2)).currentProjection();
        verify(changingUpdates, org.mockito.Mockito.times(2)).currentState();
        verify(changingUpdates, org.mockito.Mockito.times(2)).hasPendingChanges();
        verify(changingStore, never()).execute(any(WorkspaceCommand.class));

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

    private static GraphProjection recoverableProjection(final long generation) {
        return GraphProjection.resolved(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(RelationshipResolution.of(relationship(),
                RelationshipStatus.UNRESOLVED_RECOVERABLE, Optional.<ProjectedEndpointKey>empty(),
                Optional.<ProjectedEndpointKey>empty(), Collections.singleton(RecoverableReason.NODE_INACCESSIBLE))),
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

    private static final class NativeFixture implements AutoCloseable {
        private final InlineEdt edt = new InlineEdt();
        private final Map<MapReferenceId, MapLease> leases = new HashMap<MapReferenceId, MapLease>();
        private final Map<MapModel, Boolean> openViews = new java.util.IdentityHashMap<MapModel, Boolean>();
        private final Path mapDirectory;
        private final Controller application = mock(Controller.class);
        private final Controller previousController;
        private final ModeController mode;
        private final MMapController mapController;
        private final IMapViewManager views = mock(IMapViewManager.class);
        private final MapLeaseManager manager;
        private final FreeplaneMapCommandExecutor executor;
        private final MapWriter mapWriter;
        private final Map<URL, MMapModel> maps = new HashMap<URL, MMapModel>();

        private NativeFixture(final GraphWorkspaceStore store, final Path workspaceFile) {
            mapDirectory = workspaceFile.toAbsolutePath().getParent();
            previousController = Controller.getCurrentController();
            Controller.setCurrentController(application);
            mode = new NativeModeController(application);
            when(application.getModeController()).thenReturn(mode);
            mapController = mock(MMapController.class);
            final WriteManager writeManager = new WriteManager();
            when(mapController.getWriteManager()).thenReturn(writeManager);
            when(mapController.getModeController()).thenReturn(mode);
            mapWriter = new MapWriter(mapController);
            when(mapController.getMapWriter()).thenReturn(mapWriter);
            writeManager.addElementWriter("map", mapWriter);
            writeManager.addAttributeWriter("map", mapWriter);
            mode.setMapController(mapController);
            mode.addExtension(LinkController.class, new MLinkController(mode));
            when(mapController.getMap(any(URL.class))).thenAnswer(
                invocation -> maps.get(invocation.getArgument(0)));
            doAnswer(invocation -> {
                final NodeModel node = invocation.getArgument(0);
                node.getMap().setSaved(false);
                return null;
            }).when(mapController).nodeChanged(any(NodeModel.class), any(), any(), any());
            when(application.getMapViewManager()).thenReturn(views);
            when(views.containsView(any(MapModel.class))).thenAnswer(
                invocation -> openViews.containsKey(invocation.getArgument(0)));
            doAnswer(invocation -> {
                final MapModel map = invocation.getArgument(0);
                map.beforeViewCreated();
                openViews.put(map, Boolean.TRUE);
                return null;
            }).when(mapController).createMapView(any(MapModel.class));
            manager = new MapLeaseManager(workspaceFile, mode, edt);
            executor = new FreeplaneMapCommandExecutor(store, mapId -> Optional.ofNullable(leases.get(mapId)), mode,
                edt, new ViewMaterializationTracker(mode));
        }

        private NativeNodes addMap(final MapReferenceId mapId, final String title) throws Exception {
            return addMap(mapId, title, true);
        }

        private NativeNodes addMap(final MapReferenceId mapId, final String title,
                final boolean targetHasSavedId) throws Exception {
            final Path mapFile = Files.createTempFile(mapDirectory, "graph-command-acceptance-", ".mm");
            final URL url = mapFile.toUri().toURL();
            final NativeMapModel map = new NativeMapModel(title);
            map.setURL(url);
            map.setSaved(true);
            final NodeModel root = nativeNode(map, "root", "ID_ROOT_" + mapId.value());
            map.setRoot(root);
            final NodeModel source = nativeNode(map, "source", "ID_SOURCE_" + mapId.value());
            final NodeModel target = nativeNode(map, "target",
                targetHasSavedId ? "ID_TARGET_" + mapId.value() : null);
            root.insert(source);
            root.insert(target);
            maps.put(url, map);
            final MapReference reference = MapReference.of(mapId, 1L, url.toURI(), true, "#4E79A7",
                Collections.emptyList());
            final MapLease lease = manager.acquire(reference).toCompletableFuture().get(1L, TimeUnit.SECONDS);
            leases.put(mapId, lease);
            final SourceNodeKey targetKey = targetHasSavedId ? source(mapId, target.getID())
                : SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(1)));
            return new NativeNodes(this, map, mapFile, source, target, source(mapId, source.getID()), targetKey);
        }

        private void saveMapNormally(final NativeMapModel map, final Path mapFile) throws IOException {
            final BufferedWriter writer = Files.newBufferedWriter(mapFile, StandardCharsets.UTF_8);
            mapWriter.writeMapAsXml(map, writer, MapWriter.Mode.FILE, CopiedNodeSet.ALL_NODES, false);
        }

        @Override
        public void close() {
            for (final MapLease lease : leases.values()) {
                lease.close();
            }
            manager.close();
            Controller.setCurrentController(previousController);
        }
    }
    private static final class NativeNodes {
        private final NativeFixture fixture;
        private final NativeMapModel map;
        private final Path mapFile;
        private final NodeModel source;
        private final NodeModel target;
        private final SourceNodeKey sourceKey;
        private final SourceNodeKey targetKey;

        private NativeNodes(final NativeFixture fixture, final NativeMapModel map, final Path mapFile,
                final NodeModel source, final NodeModel target,
                final SourceNodeKey sourceKey, final SourceNodeKey targetKey) {
            this.fixture = fixture;
            this.map = map;
            this.mapFile = mapFile;
            this.source = source;
            this.target = target;
            this.sourceKey = sourceKey;
            this.targetKey = targetKey;
        }

        private SourceNodeKey saveTargetNormally() throws IOException {
            fixture.saveMapNormally(map, mapFile);
            assertThat(Files.size(mapFile)).isGreaterThan(0);
            assertThat(target.getID()).isNotNull();
            map.setSaved(true);
            return source(mapReferenceId(), target.getID());
        }

        private MapReferenceId mapReferenceId() {
            return sourceKey.mapReferenceId();
        }

        private List<ConnectorModel> connectors() {
            final List<ConnectorModel> result = new ArrayList<ConnectorModel>();
            final NodeLinks links = NodeLinks.getLinkExtension(source);
            if (links == null) {
                return result;
            }
            for (final NodeLinkModel link : links.getLinks()) {
                if (link instanceof ConnectorModel) {
                    result.add((ConnectorModel) link);
                }
            }
            return result;
        }
    }

    private static final class NativeMapModel extends MMapModel {
        private final String title;
        private final RecordingUndoHandler undo = new RecordingUndoHandler();

        private NativeMapModel(final String title) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(final NodeModel source, final MapModel targetMap,
                        final boolean withChildren) {
                    return null;
                }
            });
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public void beforeViewCreated() {
            if (getExtension(IUndoHandler.class) == null) {
                addExtension(IUndoHandler.class, undo);
            }
        }
    }

    private static final class NativeModeController extends ModeController {
        private NativeModeController(final Controller application) {
            super(application);
        }

        @Override
        public boolean canEdit(final MapModel map) {
            return map != null && !map.isReadOnly();
        }

        @Override
        public void execute(final IActor actor, final MapModel map) {
            final IUndoHandler handler = map.getExtension(IUndoHandler.class);
            if (handler != null && !handler.isUndoActionRunning()) {
                handler.addActor(actor);
            }
            actor.act();
        }
    }

    private static final class RecordingUndoHandler implements IUndoHandler {
        private final List<IActor> actors = new ArrayList<IActor>();
        private List<IActor> transaction;
        private int undoCalls;

        @Override
        public void addActor(final IActor actor) {
            if (transaction == null) {
                actors.add(actor);
            }
            else {
                transaction.add(actor);
            }
        }

        private int actorCount() {
            return actors.size();
        }

        @Override
        public boolean canRedo() {
            return false;
        }

        @Override
        public boolean canUndo() {
            return !actors.isEmpty();
        }

        @Override
        public void addChangeListener(final ChangeListener listener) {
        }

        @Override
        public void removeChangeListener(final ChangeListener listener) {
        }

        @Override
        public void commit() {
            if (transaction != null) {
                final List<IActor> completed = transaction;
                actors.add(new IActor() {
                    @Override
                    public void act() {
                        for (final IActor actor : completed) {
                            actor.act();
                        }
                    }

                    @Override
                    public String getDescription() {
                        return "nativeConnectorTransaction";
                    }

                    @Override
                    public void undo() {
                        for (int index = completed.size() - 1; index >= 0; index--) {
                            completed.get(index).undo();
                        }
                    }
                });
                transaction = null;
            }
        }

        @Override
        public String getLastDescription() {
            return null;
        }

        @Override
        public ActionListener getRedoAction() {
            return null;
        }

        @Override
        public ActionListener getUndoAction() {
            return null;
        }

        @Override
        public boolean isUndoActionRunning() {
            return false;
        }

        @Override
        public void redo() {
        }

        @Override
        public void resetRedo() {
        }

        @Override
        public void rollback() {
            if (transaction != null) {
                for (int index = transaction.size() - 1; index >= 0; index--) {
                    transaction.get(index).undo();
                }
                transaction = null;
            }
        }

        @Override
        public void startTransaction() {
            transaction = new ArrayList<IActor>();
        }

        @Override
        public void forceNewTransaction() {
        }

        @Override
        public void undo() {
            undoCalls++;
            if (!actors.isEmpty()) {
                actors.remove(actors.size() - 1).undo();
            }
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void delayedCommit() {
        }

        @Override
        public void delayedRollback() {
        }

        @Override
        public int getTransactionLevel() {
            return transaction == null ? 0 : 1;
        }
    }

    private static NodeModel nativeNode(final MapModel map, final String text, final String id) {
        final NodeModel node = new NodeModel(text, map);
        node.setID(id);
        return node;
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
            final EnclosureKey firstKey = EnclosureKey.of(source(MAP_ONE, "LOCKED_SECRET_SENTINEL"));
            final EnclosureKey secondKey = EnclosureKey.of(source(MAP_TWO, "two"));
            final ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofEnclosure(firstKey);
            final ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofEnclosure(secondKey);
            final EnclosureHullKey firstHull = EnclosureHullKey.of(Collections.singletonList(firstKey));
            final EnclosureHullKey secondHull = EnclosureHullKey.of(Collections.singletonList(secondKey));
            final ProjectedEnclosure first = ProjectedEnclosure.of(firstHull,
                Collections.singletonList(firstKey),
                Collections.singletonList(SafeNodeLabel.of("First full safe label", "First")), "One",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
            final ProjectedEnclosure second = ProjectedEnclosure.of(secondHull,
                Collections.singletonList(secondKey),
                Collections.singletonList(SafeNodeLabel.of("Second isolated label", "Second")), "Two",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
            final ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(firstEndpoint, secondEndpoint),
                Arrays.asList(EdgeContributor.graphRelationship(relationship(), firstEndpoint, secondEndpoint),
                    EdgeContributor.graphRelationship(secondRelationship(), firstEndpoint, secondEndpoint)));
            final GraphProjection projection = GraphProjection.projected(7L,
                Collections.<ProjectedNode>emptyList(), Arrays.asList(first, second),
                Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());
            final Map<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry> hulls =
                new LinkedHashMap<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>();
            hulls.put(firstHull, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(-50.0, -10.0), LayoutPoint.of(-30.0, -10.0),
                    LayoutPoint.of(-30.0, 10.0), LayoutPoint.of(-50.0, 10.0)),
                LayoutPoint.of(-40.0, 0.0)));
            hulls.put(secondHull, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(30.0, -10.0), LayoutPoint.of(50.0, -10.0),
                    LayoutPoint.of(50.0, 10.0), LayoutPoint.of(30.0, 10.0)),
                LayoutPoint.of(40.0, 0.0)));
            final Map<EnclosureHullKey, LayoutPoint> anchors =
                new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            anchors.put(firstHull, LayoutPoint.of(-40.0, 0.0));
            anchors.put(secondHull, LayoutPoint.of(40.0, 0.0));
            final CanvasState state = CanvasState.of(7L, projection,
                LayoutFrame.of(7L, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
                    anchors), false),
                GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls),
                OperationalStatus.IDLE);
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
            call(new Callable<Void>() {
                @Override
                public Void call() {
                    task.run();
                    return null;
                }
            });
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }
    }
}

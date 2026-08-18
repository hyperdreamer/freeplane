package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.control.WorkspaceSessionId;
import org.freeplane.plugin.graph.control.WorkspaceSessionRegistry;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommand;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphCommandRouterShould {
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    private static final WorkspaceSessionId SESSION =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000200");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final RelationshipId RELATIONSHIP =
        RelationshipId.of("00000000-0000-0000-0000-000000000301");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void routesEverySessionIntentToItsOwningService() {
        Fixture fixture = new Fixture();
        SourceNodeKey source = SourceNodeKey.persisted(node(MAP_ONE, "source"));
        SourceNodeKey target = SourceNodeKey.persisted(node(MAP_ONE, "target"));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), false, true,
            "source", "middle", "target");
        ContributorKey nativeContributor = ContributorKey.nativeConnector(MAP_ONE, source, 0);
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source)),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(SourceNodeKey.persisted(node(MAP_TWO, "other")))));

        assertApplied(fixture.router.execute(GraphCommands.addMap(MAP_ONE, URI.create("one.mm"))));
        assertApplied(fixture.router.execute(GraphCommands.retryMap(MAP_ONE)));
        assertApplied(fixture.router.execute(GraphCommands.removeMap(MAP_ONE)));
        assertApplied(fixture.router.execute(GraphCommands.locateMap(MAP_ONE, URI.create("moved.mm"))));
        assertApplied(fixture.router.execute(GraphCommands.createRelationship(RELATIONSHIP, node(MAP_ONE, "one"),
            node(MAP_TWO, "two"), RelationshipDirection.FORWARD)));
        assertApplied(fixture.router.execute(GraphCommands.updateRelationship(RELATIONSHIP, node(MAP_ONE, "one"),
            node(MAP_TWO, "two"), RelationshipDirection.BIDIRECTIONAL)));
        assertApplied(fixture.router.execute(GraphCommands.deleteRelationship(RELATIONSHIP)));
        assertApplied(fixture.router.execute(GraphCommands.pin(node(MAP_ONE, "one"), 2, 3)));
        assertApplied(fixture.router.execute(GraphCommands.unpin(node(MAP_ONE, "one"))));
        assertApplied(fixture.router.execute(GraphCommands.unpinAll()));
        assertApplied(fixture.router.execute(GraphCommands.display(DisplaySettings.defaults())));
        assertApplied(fixture.router.execute(GraphCommands.viewport(Viewport.of(1, 2, 3,
            Collections.emptyList()))));
        assertApplied(fixture.router.execute(GraphCommands.undoWorkspace()));
        assertApplied(fixture.router.execute(GraphCommands.redoWorkspace()));
        assertApplied(fixture.router.execute(GraphCommands.undoSourceMap()));
        assertApplied(fixture.router.execute(GraphCommands.save()));
        assertApplied(fixture.router.execute(GraphCommands.retrySave()));
        assertApplied(fixture.router.execute(GraphCommands.pauseLayout()));
        assertApplied(fixture.router.execute(GraphCommands.restartLayout()));
        assertApplied(fixture.router.execute(GraphCommands.resetLayout()));
        assertApplied(fixture.router.execute(GraphCommands.connect(source, target, RelationshipDirection.FORWARD)));
        assertApplied(fixture.router.execute(GraphCommands.openSource(source)));
        assertApplied(fixture.router.execute(GraphCommands.purge(4, Collections.singleton(RELATIONSHIP))));
        assertApplied(fixture.router.execute(GraphCommands.deleteContributor(4, nativeContributor, descriptor)));
        assertApplied(fixture.router.execute(GraphCommands.deleteAllContributors(4, edge,
            Collections.singletonList(nativeContributor), Collections.singletonMap(nativeContributor, descriptor))));

        verify(fixture.store, times(10)).execute(any(WorkspaceCommand.class));
        verify(fixture.store).updateViewport(any(Viewport.class));
        verify(fixture.store, times(2)).saveNow();
        verify(fixture.store).undo();
        verify(fixture.store).redo();
        verify(fixture.mapCommands).undoCurrentSourceMap();
        verify(fixture.mapCommands).createConnector(source, target, RelationshipDirection.FORWARD);
        verify(fixture.navigation).open(source);
        verify(fixture.updates).pauseLayout();
        verify(fixture.updates).restartLayout();
        verify(fixture.updates).resetLayout();
        verify(fixture.purgeHandler).purge(any(GraphCommands.Purge.class));
        verify(fixture.deletionHandler).deleteOne(any(GraphCommands.DeleteContributor.class));
        verify(fixture.deletionHandler).deleteAll(any(GraphCommands.DeleteAllContributors.class));
        assertThat(fixture.retriedMap.get().id()).isEqualTo(MAP_ONE);
    }

    @Test
    public void retriesOnlyAnActiveRegistrationAndPassesItsUnchangedIdentityToTheLeaseHandler() {
        Fixture fixture = new Fixture();
        MapReference active = map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7");
        MapReference inactive = map(MAP_TWO, 2, "maps/two.mm", false, "#F28E2B");
        fixture.document = WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(active, inactive)).build();
        when(fixture.store.currentDocument()).thenReturn(fixture.document);

        GraphCommandResult activeResult = fixture.router.execute(GraphCommands.retryMap(MAP_ONE));
        GraphCommandResult inactiveResult = fixture.router.execute(GraphCommands.retryMap(MAP_TWO));

        assertApplied(activeResult);
        assertThat(inactiveResult.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(inactiveResult.messageKey()).contains("inactive");
        assertThat(fixture.retriedMap.get()).isEqualTo(active);
    }

    @Test
    public void savesAtMostOnceWhenAnExplicitSaveIsRejected() {
        Fixture fixture = new Fixture();
        doThrow(new IllegalStateException("temporary save failure")).when(fixture.store).saveNow();

        GraphCommandResult result = fixture.router.execute(GraphCommands.save());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        verify(fixture.store, times(1)).saveNow();
    }

    @Test
    public void saveAsCannotWriteBeforeReservation() throws Exception {
        Fixture fixture = new Fixture();
        Path current = temporaryFolder.newFolder("current").toPath().resolve("workspace.fpg");
        Path target = temporaryFolder.newFolder("target").toPath().resolve("renamed.fpg");
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        assertThat(registry.register(SESSION, current)).isTrue();
        fixture = fixture.withRegistry(registry);
        final AtomicReference<Optional<WorkspaceSessionId>> ownerAtWrite =
            new AtomicReference<Optional<WorkspaceSessionId>>();
        doAnswer(invocation -> {
            ownerAtWrite.set(registry.owner(target));
            throw new IllegalStateException("write probe");
        }).when(fixture.store).saveAs(eq(target));

        GraphCommandResult result = fixture.router.execute(GraphCommands.saveAs(target));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(ownerAtWrite.get()).contains(SESSION);
        assertThat(registry.owner(target)).isEmpty();
        verify(fixture.store, times(1)).saveAs(eq(target));
    }

    @Test
    public void preservesTheCurrentMapUndoTargetWithoutLettingNullEscapeAsAnOptional() {
        Fixture fixture = new Fixture();
        MapUndoTarget target = new MapUndoTarget(MAP_ONE, "one", true);
        when(fixture.mapCommands.currentUndoTarget()).thenReturn(Optional.of(target));

        assertThat(fixture.router.currentMapUndoTarget()).contains(target);

        when(fixture.mapCommands.currentUndoTarget()).thenReturn(null);
        assertThat(fixture.router.currentMapUndoTarget()).isEmpty();
    }

    @Test
    public void freezesGenerationBoundCollectionsAndAllowsMissingConnectorExpectationsForRelationships() {
        SourceNodeKey source = SourceNodeKey.persisted(node(MAP_ONE, "source"));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), false, true,
            "source", "middle", "target");
        ContributorKey contributor = ContributorKey.graphRelationship(RELATIONSHIP);
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source)),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(SourceNodeKey.persisted(node(MAP_TWO, "other")))));
        java.util.Set<RelationshipId> relationshipIds = new java.util.LinkedHashSet<RelationshipId>();
        relationshipIds.add(RELATIONSHIP);
        java.util.List<ContributorKey> contributors = new java.util.ArrayList<ContributorKey>();
        contributors.add(contributor);
        java.util.Map<ContributorKey, ConnectorDescriptor> expected =
            new java.util.LinkedHashMap<ContributorKey, ConnectorDescriptor>();
        expected.put(ContributorKey.nativeConnector(MAP_ONE, source, 0), descriptor);

        GraphCommands.Purge purge = GraphCommands.purge(7, relationshipIds);
        GraphCommands.DeleteContributor delete = GraphCommands.deleteContributor(7, contributor, null);
        GraphCommands.DeleteAllContributors deleteAll = GraphCommands.deleteAllContributors(7, edge, contributors,
            expected);
        relationshipIds.clear();
        contributors.clear();
        expected.clear();

        assertThat(purge.relationships()).containsExactly(RELATIONSHIP);
        assertThat(delete.expectedConnector()).isEmpty();
        assertThat(deleteAll.contributors()).containsExactly(contributor);
        assertThat(deleteAll.expectedConnectors()).hasSize(1);
        assertThatThrownBy(() -> GraphCommands.purge(-1, Collections.singleton(RELATIONSHIP)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void saveAsCommitsTheReservationOnlyAfterTheStorePublishesTheNewIdentity() throws Exception {
        Path current = temporaryFolder.newFolder("real-current").toPath().resolve("workspace.fpg");
        Path target = temporaryFolder.newFolder("real-target").toPath().resolve("renamed.fpg");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(current,
            new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.emptyList())),
            new FileWriter(), scheduler);
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        assertThat(registry.register(SESSION, current)).isTrue();
        GraphCommandResult result;
        try {
            result = new GraphCommandRouter(store, new GraphCommandRouter.MapRetryHandler() {
                @Override
                public GraphCommandResult retry(MapReference reference) {
                    return GraphCommandResult.from(WorkspaceTransition.applied(store.currentDocument(), "retry"));
                }
            }, mock(FreeplaneMapCommandExecutor.class), mock(SourceNavigation.class),
                mock(org.freeplane.plugin.graph.control.GraphUpdateCoordinator.class), registry, SESSION,
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class))
                .execute(GraphCommands.saveAs(target));
        }
        finally {
            store.discardAndClose();
            scheduler.shutdownNow();
        }

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(result.identityChange()).isPresent();
        assertThat(registry.owner(current)).isEmpty();
        assertThat(registry.owner(target)).contains(SESSION);
        assertThat(Files.exists(target)).isTrue();
    }

    private static void assertApplied(GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    private static NodeReference node(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static MapReference map(MapReferenceId id, long sequence, String uri, boolean active, String color) {
        return MapReference.of(id, sequence, URI.create(uri), active, color, Collections.emptyList());
    }

    private static final class FileWriter implements AtomicWorkspaceWriter {
        @Override
        public void write(Path target, byte[] bytes) {
            try {
                Files.write(target, bytes);
            }
            catch (IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class Fixture {
        private WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(map(MAP_ONE, 1, "one.mm", true, "#4E79A7"),
                map(MAP_TWO, 2, "two.mm", true, "#F28E2B"))).build();
        private final GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        private final FreeplaneMapCommandExecutor mapCommands = mock(FreeplaneMapCommandExecutor.class);
        private final SourceNavigation navigation = mock(SourceNavigation.class);
        private final org.freeplane.plugin.graph.control.GraphUpdateCoordinator updates =
            mock(org.freeplane.plugin.graph.control.GraphUpdateCoordinator.class);
        private final PurgeCommandHandler purgeHandler = mock(PurgeCommandHandler.class);
        private final ContributorDeletionHandler deletionHandler = mock(ContributorDeletionHandler.class);
        private final AtomicReference<MapReference> retriedMap = new AtomicReference<MapReference>();
        private WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        private GraphCommandRouter router;

        private Fixture() {
            when(store.currentDocument()).thenReturn(document);
            GraphCommandResult result = GraphCommandResult.from(
                WorkspaceTransition.applied(document, "test.applied"));
            when(store.execute(any(WorkspaceCommand.class))).thenReturn(result);
            when(store.updateViewport(any(Viewport.class))).thenReturn(result);
            when(store.undo()).thenReturn(result);
            when(store.redo()).thenReturn(result);
            when(mapCommands.undoCurrentSourceMap()).thenReturn(result);
            when(mapCommands.createConnector(any(SourceNodeKey.class), any(SourceNodeKey.class),
                any(RelationshipDirection.class))).thenReturn(result);
            when(navigation.open(any(SourceNodeKey.class))).thenReturn(result);
            when(purgeHandler.purge(any(GraphCommands.Purge.class))).thenReturn(result);
            when(deletionHandler.deleteOne(any(GraphCommands.DeleteContributor.class))).thenReturn(result);
            when(deletionHandler.deleteAll(any(GraphCommands.DeleteAllContributors.class))).thenReturn(result);
            router = createRouter();
        }

        private Fixture withRegistry(WorkspaceSessionRegistry value) {
            registry = value;
            router = createRouter();
            return this;
        }

        private GraphCommandRouter createRouter() {
            return new GraphCommandRouter(store, new GraphCommandRouter.MapRetryHandler() {
                @Override
                public GraphCommandResult retry(MapReference reference) {
                    retriedMap.set(reference);
                    return GraphCommandResult.from(
                        WorkspaceTransition.applied(document, "graph_workspace.map.retried"));
                }
            }, mapCommands, navigation, updates, registry, SESSION, purgeHandler, deletionHandler);
        }
    }
}

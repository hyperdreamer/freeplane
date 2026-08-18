package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.namespace.QName;

import org.mockito.ArgumentCaptor;

import org.freeplane.plugin.graph.control.WorkspaceMapCoordinator;
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
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
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
    private static final WorkspaceSessionId OCCUPYING_SESSION =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000201");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000003");
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
        Viewport viewport = Viewport.of(1, 2, 3, Collections.emptyList());
        assertApplied(fixture.router.execute(GraphCommands.viewport(viewport)));
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
        GraphCommands.Purge purge = GraphCommands.purge(4, Collections.singleton(RELATIONSHIP));
        GraphCommands.DeleteContributor deleteContributor =
            GraphCommands.deleteContributor(4, nativeContributor, descriptor);
        GraphCommands.DeleteAllContributors deleteAll = GraphCommands.deleteAllContributors(4, edge,
            Collections.singletonList(nativeContributor), Collections.singletonMap(nativeContributor, descriptor));
        assertApplied(fixture.router.execute(purge));
        assertApplied(fixture.router.execute(deleteContributor));
        assertApplied(fixture.router.execute(deleteAll));

        verify(fixture.store, times(10)).execute(any(WorkspaceCommand.class));
        verify(fixture.store).updateViewport(same(viewport));
        verify(fixture.store, times(2)).saveNow();
        verify(fixture.store).undo();
        verify(fixture.store).redo();
        verify(fixture.mapCommands).undoCurrentSourceMap();
        verify(fixture.mapCommands).createConnector(source, target, RelationshipDirection.FORWARD);
        verify(fixture.navigation).open(same(source));
        verify(fixture.updates).pauseLayout();
        verify(fixture.updates).restartLayout();
        verify(fixture.updates).resetLayout();
        verify(fixture.purgeHandler).purge(same(purge));
        verify(fixture.deletionHandler).deleteOne(same(deleteContributor));
        verify(fixture.deletionHandler).deleteAll(same(deleteAll));
        verify(fixture.maps).retry(same(fixture.document.maps().get(0)));
    }

    @Test
    public void appliesRestartLayoutAndDelegatesItExactlyOnce() {
        Fixture fixture = new Fixture();

        GraphCommandResult result = fixture.router.execute(GraphCommands.restartLayout());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.layout.restarted");
        verify(fixture.updates, times(1)).restartLayout();
    }

    @Test
    public void preservesExactWorkspaceCommandPayloads() {
        Fixture fixture = new Fixture();
        NodeReference relationshipSource = node(MAP_ONE, "source");
        NodeReference relationshipTarget = node(MAP_TWO, "target");
        NodeReference pinnedNode = node(MAP_ONE, "pinned");
        DisplaySettings settings = DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, true,
            Collections.singletonList(UnknownXml.attribute(UnknownXml.Owner.RECORD,
                new QName("urn:test", "display-flag"), "on")));
        Viewport viewport = Viewport.of(11.5, -4.25, 2.75, Collections.emptyList());

        assertApplied(fixture.router.execute(GraphCommands.addMap(MAP_THREE, URI.create("three.mm"))));
        assertApplied(fixture.router.execute(GraphCommands.removeMap(MAP_ONE)));
        assertApplied(fixture.router.execute(GraphCommands.locateMap(MAP_TWO, URI.create("located.mm"))));
        assertApplied(fixture.router.execute(GraphCommands.createRelationship(RELATIONSHIP, relationshipSource,
            relationshipTarget, RelationshipDirection.FORWARD)));
        assertApplied(fixture.router.execute(GraphCommands.updateRelationship(RELATIONSHIP, relationshipSource,
            relationshipTarget, RelationshipDirection.BIDIRECTIONAL)));
        assertApplied(fixture.router.execute(GraphCommands.deleteRelationship(RELATIONSHIP)));
        assertApplied(fixture.router.execute(GraphCommands.pin(pinnedNode, 12.5, -4.25)));
        assertApplied(fixture.router.execute(GraphCommands.unpin(pinnedNode)));
        assertApplied(fixture.router.execute(GraphCommands.unpinAll()));
        assertApplied(fixture.router.execute(GraphCommands.display(settings)));
        assertApplied(fixture.router.execute(GraphCommands.viewport(viewport)));

        ArgumentCaptor<WorkspaceCommand> commandCaptor = ArgumentCaptor.forClass(WorkspaceCommand.class);
        verify(fixture.store, times(10)).execute(commandCaptor.capture());
        List<WorkspaceCommand> commands = commandCaptor.getAllValues();
        assertThat(commands).hasSize(10);

        WorkspaceDocument current = fixture.document;
        WorkspaceTransition added = commands.get(0).apply(current);
        assertThat(added.messageArguments()).containsExactly(MAP_THREE);
        assertThat(mapReference(added.after(), MAP_THREE).storedUri()).isEqualTo(URI.create("three.mm"));
        current = added.after();

        WorkspaceTransition removed = commands.get(1).apply(current);
        assertThat(removed.messageArguments()).containsExactly(MAP_ONE);
        assertThat(mapReference(removed.after(), MAP_ONE).active()).isFalse();
        current = removed.after();

        WorkspaceTransition located = commands.get(2).apply(current);
        assertThat(located.messageArguments()).containsExactly(MAP_TWO);
        assertThat(mapReference(located.after(), MAP_TWO).storedUri()).isEqualTo(URI.create("located.mm"));
        current = located.after();

        WorkspaceTransition created = commands.get(3).apply(current);
        GraphRelationshipRecord createdRelationship = created.after().relationships().get(0);
        assertThat(createdRelationship.id()).isEqualTo(RELATIONSHIP);
        assertThat(createdRelationship.source()).isEqualTo(relationshipSource);
        assertThat(createdRelationship.target()).isEqualTo(relationshipTarget);
        assertThat(createdRelationship.direction()).isEqualTo(RelationshipDirection.FORWARD);
        current = created.after();

        WorkspaceTransition updated = commands.get(4).apply(current);
        GraphRelationshipRecord updatedRelationship = updated.after().relationships().get(0);
        assertThat(updated.messageArguments()).containsExactly(RELATIONSHIP);
        assertThat(updatedRelationship.source()).isEqualTo(relationshipSource);
        assertThat(updatedRelationship.target()).isEqualTo(relationshipTarget);
        assertThat(updatedRelationship.direction()).isEqualTo(RelationshipDirection.BIDIRECTIONAL);
        current = updated.after();

        WorkspaceTransition deleted = commands.get(5).apply(current);
        assertThat(deleted.messageArguments()).containsExactly(RELATIONSHIP);
        assertThat(deleted.after().relationships()).isEmpty();
        current = deleted.after();

        WorkspaceTransition pinned = commands.get(6).apply(current);
        PinRecord pin = pinned.after().pins().get(0);
        assertThat(pin.node()).isEqualTo(pinnedNode);
        assertThat(pin.x()).isEqualTo(12.5);
        assertThat(pin.y()).isEqualTo(-4.25);
        current = pinned.after();

        WorkspaceTransition unpinned = commands.get(7).apply(current);
        assertThat(unpinned.messageArguments()).containsExactly(pinnedNode);
        assertThat(unpinned.after().pins()).isEmpty();
        current = unpinned.after();

        WorkspaceTransition allUnpinned = commands.get(8).apply(current);
        assertThat(allUnpinned.after().pins()).isEmpty();
        current = allUnpinned.after();

        WorkspaceTransition displayed = commands.get(9).apply(current);
        assertThat(displayed.after().displaySettings()).isEqualTo(settings);
        verify(fixture.store).updateViewport(same(viewport));
    }

    @Test
    public void delegatesSameMapCrossMapAndSelfConnectorPayloads() {
        Fixture fixture = new Fixture();
        SourceNodeKey sameSource = SourceNodeKey.persisted(node(MAP_ONE, "source"));
        SourceNodeKey sameTarget = SourceNodeKey.persisted(node(MAP_ONE, "target"));
        SourceNodeKey crossTarget = SourceNodeKey.persisted(node(MAP_TWO, "target"));
        GraphCommandResult applied = GraphCommandResult.from(
            WorkspaceTransition.applied(fixture.document, "same.applied"));
        GraphCommandResult crossMapRejected = GraphCommandResult.from(
            WorkspaceTransition.rejected(fixture.document, "cross.rejected", MAP_ONE, MAP_TWO));
        GraphCommandResult selfRejected = GraphCommandResult.from(
            WorkspaceTransition.rejected(fixture.document, "self.rejected", sameSource));
        when(fixture.mapCommands.createConnector(same(sameSource), same(sameTarget),
            eq(RelationshipDirection.FORWARD))).thenReturn(applied);
        when(fixture.mapCommands.createConnector(same(sameSource), same(crossTarget),
            eq(RelationshipDirection.BIDIRECTIONAL))).thenReturn(crossMapRejected);
        when(fixture.mapCommands.createConnector(same(sameSource), same(sameSource),
            eq(RelationshipDirection.UNDIRECTED))).thenReturn(selfRejected);

        assertThat(fixture.router.execute(GraphCommands.connect(sameSource, sameTarget,
            RelationshipDirection.FORWARD))).isSameAs(applied);
        assertThat(fixture.router.execute(GraphCommands.connect(sameSource, crossTarget,
            RelationshipDirection.BIDIRECTIONAL))).isSameAs(crossMapRejected);
        assertThat(fixture.router.execute(GraphCommands.connect(sameSource, sameSource,
            RelationshipDirection.UNDIRECTED))).isSameAs(selfRejected);

        verify(fixture.mapCommands).createConnector(sameSource, sameTarget, RelationshipDirection.FORWARD);
        verify(fixture.mapCommands).createConnector(sameSource, crossTarget, RelationshipDirection.BIDIRECTIONAL);
        verify(fixture.mapCommands).createConnector(sameSource, sameSource, RelationshipDirection.UNDIRECTED);
        verify(fixture.mapCommands, times(3)).createConnector(any(SourceNodeKey.class), any(SourceNodeKey.class),
            any(RelationshipDirection.class));
        verify(fixture.store, never()).saveNow();
    }

    @Test
    public void executesTransientSourceCommandRejectionOnceWithoutImplicitSave() {
        Fixture fixture = new Fixture();
        GraphCommandResult rejection = GraphCommandResult.from(
            WorkspaceTransition.rejected(fixture.document, "source.transient"));
        when(fixture.mapCommands.undoCurrentSourceMap()).thenReturn(rejection);

        assertThat(fixture.router.execute(GraphCommands.undoSourceMap())).isSameAs(rejection);

        verify(fixture.mapCommands, times(1)).undoCurrentSourceMap();
        verify(fixture.store, never()).saveNow();
    }

    @Test
    public void retriesOnlyAnActiveRegistrationAndPassesItsUnchangedIdentityToTheCoordinator() {
        Fixture fixture = new Fixture();
        UnknownXml unknown = UnknownXml.attribute(UnknownXml.Owner.RECORD,
            new QName("urn:test", "map-flag"), "preserve");
        MapReference active = MapReference.of(MAP_ONE, 17L, URI.create("maps/one.mm"), true, "#4E79A7",
            Collections.singletonList(unknown));
        MapReference inactive = map(MAP_TWO, 2, "maps/two.mm", false, "#F28E2B");
        fixture.document = WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(active, inactive)).build();
        when(fixture.store.currentDocument()).thenReturn(fixture.document);

        GraphCommandResult activeResult = fixture.router.execute(GraphCommands.retryMap(MAP_ONE));
        GraphCommandResult inactiveResult = fixture.router.execute(GraphCommands.retryMap(MAP_TWO));
        GraphCommandResult missingResult = fixture.router.execute(GraphCommands.retryMap(MAP_THREE));

        assertApplied(activeResult);
        assertThat(inactiveResult.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(inactiveResult.messageKey()).isEqualTo("graph_workspace.map.retry.inactive");
        assertThat(inactiveResult.messageArguments()).containsExactly(MAP_TWO);
        assertThat(missingResult.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(missingResult.messageKey()).isEqualTo("graph_workspace.map.not_found");
        assertThat(missingResult.messageArguments()).containsExactly(MAP_THREE);

        ArgumentCaptor<MapReference> referenceCaptor = ArgumentCaptor.forClass(MapReference.class);
        verify(fixture.maps).retry(referenceCaptor.capture());
        MapReference passed = referenceCaptor.getValue();
        assertThat(passed).isSameAs(active);
        assertThat(passed.id()).isEqualTo(MAP_ONE);
        assertThat(passed.storedUri()).isEqualTo(URI.create("maps/one.mm"));
        assertThat(passed.sequence()).isEqualTo(17L);
        assertThat(passed.active()).isTrue();
        assertThat(passed.color()).isEqualTo("#4E79A7");
        assertThat(passed.unknownXml()).containsExactly(unknown);
    }

    @Test
    public void rejectsRetryFailureWithoutSavingOrCallingTheCoordinatorTwice() {
        Fixture fixture = new Fixture();
        MapReference active = map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7");
        fixture.document = WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Collections.singletonList(active)).build();
        when(fixture.store.currentDocument()).thenReturn(fixture.document);
        doThrow(new IllegalStateException("retry unavailable")).when(fixture.maps).retry(same(active));

        GraphCommandResult result = fixture.router.execute(GraphCommands.retryMap(MAP_ONE));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.map.retry.failed");
        assertThat(result.messageArguments()).containsExactly(MAP_ONE);
        verify(fixture.maps, times(1)).retry(active);
        verify(fixture.store, never()).saveNow();
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
    public void leavesAnOccupiedSaveAsTargetBytesIdentityAndRegistryUnchanged() throws Exception {
        Path current = temporaryFolder.newFolder("occupied-current").toPath().resolve("workspace.fpg");
        Path target = temporaryFolder.newFolder("occupied-target").toPath().resolve("workspace.fpg");
        byte[] occupiedBytes = new byte[] { 7, 3, 1, 9 };
        Files.write(target, occupiedBytes);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(current,
            new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.emptyList())), new FileWriter(),
            scheduler);
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        assertThat(registry.register(SESSION, current)).isTrue();
        assertThat(registry.register(OCCUPYING_SESSION, target)).isTrue();
        WorkspaceDocument before = store.currentDocument();
        byte[] currentBytes = Files.readAllBytes(current);
        try {
            GraphCommandResult result = new GraphCommandRouter(store, mock(WorkspaceMapCoordinator.class),
                mock(FreeplaneMapCommandExecutor.class), mock(SourceNavigation.class),
                mock(org.freeplane.plugin.graph.control.GraphUpdateCoordinator.class), registry, SESSION,
                mock(PurgeCommandHandler.class), mock(ContributorDeletionHandler.class))
                .execute(GraphCommands.saveAs(target));

            assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
            assertThat(result.messageKey()).isEqualTo("graph_workspace.workspace.save_as_failed");
            assertThat(Files.readAllBytes(target)).isEqualTo(occupiedBytes);
            assertThat(Files.readAllBytes(current)).isEqualTo(currentBytes);
            assertThat(store.currentDocument()).isSameAs(before);
            assertThat(registry.owner(current)).contains(SESSION);
            assertThat(registry.owner(target)).contains(OCCUPYING_SESSION);
        }
        finally {
            store.discardAndClose();
            scheduler.shutdownNow();
        }
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
    public void preservesExactGenerationBoundDeletionPayloads() {
        Fixture fixture = new Fixture();
        SourceNodeKey source = SourceNodeKey.persisted(node(MAP_ONE, "source"));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, node(MAP_ONE, "target"), true, false,
            "source-label", "middle-label", "target-label");
        ContributorKey contributor = ContributorKey.nativeConnector(MAP_ONE, source, 2);
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source)),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(SourceNodeKey.persisted(node(MAP_TWO, "target")))));
        Set<RelationshipId> relationships = Collections.singleton(RELATIONSHIP);
        GraphCommands.Purge purge = GraphCommands.purge(19L, relationships);
        GraphCommands.DeleteContributor delete = GraphCommands.deleteContributor(19L, contributor, descriptor);
        GraphCommands.DeleteAllContributors deleteAll = GraphCommands.deleteAllContributors(19L, edge,
            Collections.singletonList(contributor), Collections.singletonMap(contributor, descriptor));

        assertApplied(fixture.router.execute(purge));
        assertApplied(fixture.router.execute(delete));
        assertApplied(fixture.router.execute(deleteAll));

        ArgumentCaptor<GraphCommands.Purge> purgeCaptor = ArgumentCaptor.forClass(GraphCommands.Purge.class);
        ArgumentCaptor<GraphCommands.DeleteContributor> deleteCaptor =
            ArgumentCaptor.forClass(GraphCommands.DeleteContributor.class);
        ArgumentCaptor<GraphCommands.DeleteAllContributors> deleteAllCaptor =
            ArgumentCaptor.forClass(GraphCommands.DeleteAllContributors.class);
        verify(fixture.purgeHandler).purge(purgeCaptor.capture());
        verify(fixture.deletionHandler).deleteOne(deleteCaptor.capture());
        verify(fixture.deletionHandler).deleteAll(deleteAllCaptor.capture());

        assertThat(purgeCaptor.getValue().displayedGeneration()).isEqualTo(19L);
        assertThat(purgeCaptor.getValue().relationships()).containsExactly(RELATIONSHIP);
        assertThat(deleteCaptor.getValue().displayedGeneration()).isEqualTo(19L);
        assertThat(deleteCaptor.getValue().contributor()).isEqualTo(contributor);
        assertThat(deleteCaptor.getValue().expectedConnector()).contains(descriptor);
        assertThat(deleteAllCaptor.getValue().displayedGeneration()).isEqualTo(19L);
        assertThat(deleteAllCaptor.getValue().edge()).isEqualTo(edge);
        assertThat(deleteAllCaptor.getValue().contributors()).containsExactly(contributor);
        assertThat(deleteAllCaptor.getValue().expectedConnectors()).containsEntry(contributor, descriptor);
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
            result = new GraphCommandRouter(store, mock(WorkspaceMapCoordinator.class),
                mock(FreeplaneMapCommandExecutor.class), mock(SourceNavigation.class),
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

    private static MapReference mapReference(WorkspaceDocument document, MapReferenceId id) {
        for (MapReference reference : document.maps()) {
            if (reference.id().equals(id)) {
                return reference;
            }
        }
        throw new AssertionError("Missing map reference: " + id);
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
        private final WorkspaceMapCoordinator maps = mock(WorkspaceMapCoordinator.class);
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
            return new GraphCommandRouter(store, maps, mapCommands, navigation, updates, registry, SESSION,
                purgeHandler, deletionHandler);
        }
    }
}

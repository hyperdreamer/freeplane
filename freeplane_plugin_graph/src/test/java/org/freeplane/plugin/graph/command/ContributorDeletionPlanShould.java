package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommand;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ContributorDeletionPlanShould {
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000401");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000402");
    private static final RelationshipId RELATIONSHIP =
        RelationshipId.of("00000000-0000-0000-0000-000000000403");

    @Test
    public void keepsNativeGroupsAndWorkspaceIdsImmutable() {
        SourceNodeKey source = source(MAP_ONE, "source");
        ContributorKey key = ContributorKey.nativeConnector(MAP_ONE, source, 0);
        ConnectorDescriptor descriptor = descriptor(source, MAP_ONE, "target");
        ContributorDeletionPlan.NativeEdit edit = ContributorDeletionPlan.NativeEdit.of(key, descriptor);
        Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> nativeEdits =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        nativeEdits.put(MAP_ONE, Arrays.asList(edit));
        Set<RelationshipId> relationships = new LinkedHashSet<RelationshipId>(Collections.singleton(RELATIONSHIP));

        ContributorDeletionPlan plan = ContributorDeletionPlan.of(nativeEdits, relationships);

        assertThat(plan.nativeEditsByMap().get(MAP_ONE)).containsExactly(edit);
        assertThat(plan.relationshipIds()).containsExactly(RELATIONSHIP);
        assertThatThrownBy(() -> plan.nativeEditsByMap().put(MAP_TWO, Collections.singletonList(edit)))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.nativeEditsByMap().get(MAP_ONE).clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.relationshipIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);

        nativeEdits.clear();
        relationships.clear();
        assertThat(plan.nativeEditsByMap().get(MAP_ONE)).containsExactly(edit);
        assertThat(plan.relationshipIds()).containsExactly(RELATIONSHIP);
    }

    @Test
    public void rejectsDuplicateNativeKeysAndInconsistentMapSourceDescriptors() {
        SourceNodeKey source = source(MAP_ONE, "source");
        ContributorKey key = ContributorKey.nativeConnector(MAP_ONE, source, 0);
        ConnectorDescriptor descriptor = descriptor(source, MAP_ONE, "target");
        ContributorDeletionPlan.NativeEdit edit = ContributorDeletionPlan.NativeEdit.of(key, descriptor);
        Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> duplicate =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        duplicate.put(MAP_ONE, Arrays.asList(edit, edit));

        assertThatThrownBy(() -> ContributorDeletionPlan.of(duplicate, Collections.<RelationshipId>emptySet()))
            .isInstanceOf(IllegalArgumentException.class);

        SourceNodeKey otherSource = source(MAP_TWO, "source");
        ConnectorDescriptor otherDescriptor = descriptor(otherSource, MAP_TWO, "target");
        Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> inconsistent =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        inconsistent.put(MAP_ONE, Collections.singletonList(
            ContributorDeletionPlan.NativeEdit.of(key, otherDescriptor)));

        assertThatThrownBy(() -> ContributorDeletionPlan.of(inconsistent, Collections.<RelationshipId>emptySet()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectsStaleGenerationBeforeBeginningNativeOrWorkspaceWork() {
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(projection(9L));
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document());

        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);
        ContributorKey relationship = ContributorKey.graphRelationship(RELATIONSHIP);

        GraphCommandResult result = handler.deleteOne(GraphCommands.deleteContributor(8L, relationship, null));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.stale");
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
        assertThat(edt.callCount).isEqualTo(1);
    }

    @Test
    public void rejectsPendingChangesBeforeBeginningEitherDeletionPhase() {
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(projection(9L));
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(true);
        when(store.currentDocument()).thenReturn(document());
        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);

        GraphCommandResult result = handler.deleteOne(GraphCommands.deleteContributor(9L,
            ContributorKey.graphRelationship(RELATIONSHIP), null));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.pending");
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void rejectsAnUnavailableCoordinatorBeforeReadingContributors() {
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(null);
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document());
        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);

        GraphCommandResult result = handler.deleteOne(GraphCommands.deleteContributor(9L,
            ContributorKey.graphRelationship(RELATIONSHIP), null));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.unavailable");
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void rejectsAConnectorThatChangesBetweenTheTwoProjectionValidations() {
        SourceNodeKey source = source(MAP_ONE, "source");
        NodeReference target = node(MAP_ONE, "target");
        ConnectorDescriptor beforeDescriptor = descriptor(source, MAP_ONE, "target");
        ConnectorDescriptor afterDescriptor = ConnectorDescriptor.of(source, target, false, true,
            "changed", "", "");
        EdgeContributor beforeContributor = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(0, beforeDescriptor),
            endpoint(source), endpoint(target));
        EdgeContributor afterContributor = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(0, afterDescriptor),
            endpoint(source), endpoint(target));
        GraphProjection before = edgeProjection(9L, ProjectedEdge.of(
            ProjectedEdgeKey.of(endpoint(source), endpoint(target)), Collections.singletonList(beforeContributor)));
        GraphProjection after = edgeProjection(9L, ProjectedEdge.of(
            ProjectedEdgeKey.of(endpoint(source), endpoint(target)), Collections.singletonList(afterContributor)));
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(before, after);
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document());
        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);

        GraphCommandResult result = handler.deleteOne(GraphCommands.deleteContributor(9L,
            beforeContributor.key(), beforeDescriptor));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.changed");
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void publishesOneExactWorkspacePurgeForAWorkspaceContributor() {
        GraphRelationshipRecord record = GraphRelationshipRecord.of(RELATIONSHIP, 1L,
            node(MAP_ONE, "source"), node(MAP_TWO, "target"), RelationshipDirection.FORWARD,
            Collections.<UnknownXml>emptyList());
        EdgeContributor contributor = EdgeContributor.graphRelationship(record,
            endpoint(SourceNodeKey.persisted(record.source())), endpoint(record.target()));
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(contributor.projectedSource(),
            contributor.projectedTarget()), Collections.singletonList(contributor));
        GraphProjection projection = edgeProjection(9L, edge);
        WorkspaceDocument document = document(record);
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(projection);
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document);
        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            mock(FreeplaneMapCommandExecutor.ContributorDeletionTransaction.class);
        when(transaction.outcome()).thenReturn(GraphCommandResult.from(
            WorkspaceTransition.applied(document, "native.prevalidated")));
        when(transaction.dirtySourceMaps()).thenReturn(Collections.<MapReferenceId>emptySet());
        when(transaction.editorViewActivated()).thenReturn(false);
        when(store.execute(any(WorkspaceCommand.class))).thenAnswer(invocation ->
            GraphCommandResult.from(WorkspaceTransition.applied(document, "graph_workspace.relationships.purged")));
        when(maps.beginContributorDeletion(any(ContributorDeletionPlan.class))).thenReturn(transaction);
        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);

        GraphCommandResult result = handler.deleteOne(GraphCommands.deleteContributor(9L,
            contributor.key(), null));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        ArgumentCaptor<WorkspaceCommand> command = ArgumentCaptor.forClass(WorkspaceCommand.class);
        verify(store).execute(command.capture());
        WorkspaceTransition transition = command.getValue().apply(document);
        assertThat(transition.after().relationships()).isEmpty();
        verify(transaction).commit();
        verify(transaction, never()).rollback();
    }

    @Test
    public void rollsBackNativeTransactionWhenTheSingleWorkspaceTransitionIsRejected() {
        SourceNodeKey source = source(MAP_ONE, "source");
        NodeReference target = node(MAP_TWO, "target");
        GraphRelationshipRecord record = GraphRelationshipRecord.of(RELATIONSHIP, 1L,
            node(MAP_ONE, "source"), target, RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
        NodeReference nativeTarget = node(MAP_ONE, "target");
        ProjectedEndpointKey sourceEndpoint = endpoint(source);
        ProjectedEndpointKey nativeTargetEndpoint = endpoint(nativeTarget);
        ConnectorDescriptor nativeDescriptor = descriptor(source, MAP_ONE, "target");
        ContributorKey nativeKey = ContributorKey.nativeConnector(MAP_ONE, source, 0);
        EdgeContributor nativeContributor = mock(EdgeContributor.class);
        when(nativeContributor.key()).thenReturn(nativeKey);
        when(nativeContributor.projectedSource()).thenReturn(sourceEndpoint);
        when(nativeContributor.projectedTarget()).thenReturn(nativeTargetEndpoint);
        when(nativeContributor.connectorDescriptor()).thenReturn(Optional.of(nativeDescriptor));
        when(nativeContributor.graphRelationship()).thenReturn(Optional.<GraphRelationshipRecord>empty());
        EdgeContributor relationshipContributor = mock(EdgeContributor.class);
        when(relationshipContributor.key()).thenReturn(ContributorKey.graphRelationship(RELATIONSHIP));
        when(relationshipContributor.projectedSource()).thenReturn(sourceEndpoint);
        when(relationshipContributor.projectedTarget()).thenReturn(nativeTargetEndpoint);
        when(relationshipContributor.connectorDescriptor()).thenReturn(Optional.<ConnectorDescriptor>empty());
        when(relationshipContributor.graphRelationship()).thenReturn(Optional.of(record));
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, nativeTargetEndpoint),
            Arrays.asList(nativeContributor, relationshipContributor));
        GraphProjection projection = GraphProjection.projected(9L, Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(edge), Collections.<RelationshipResolution>emptyList(), Collections.emptyList());

        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(projection);
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document(record));
        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            mock(FreeplaneMapCommandExecutor.ContributorDeletionTransaction.class);
        when(transaction.outcome()).thenReturn(applied("native"));
        when(transaction.dirtySourceMaps()).thenReturn(Collections.singleton(MAP_ONE));
        when(transaction.editorViewActivated()).thenReturn(false);
        when(store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.rejected(document(record), "workspace.rejected")));
        when(maps.beginContributorDeletion(any(ContributorDeletionPlan.class))).thenReturn(transaction);

        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);
        List<ContributorKey> requested = Arrays.asList(nativeContributor.key(), relationshipContributor.key());
        Map<ContributorKey, ConnectorDescriptor> expected =
            Collections.singletonMap(nativeContributor.key(), nativeContributor.connectorDescriptor().get());

        GraphCommandResult result = handler.deleteAll(GraphCommands.deleteAllContributors(9L, edge.key(), requested,
            expected));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(store).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void requiresDeleteAllToPrevalidateEveryContributorBeforeBeginningTheTransaction() {
        SourceNodeKey source = source(MAP_ONE, "source");
        NodeReference target = node(MAP_TWO, "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(0,
                descriptor(source, MAP_ONE, "target")), endpoint(source), endpoint(node(MAP_ONE, "target")));
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(endpoint(source), endpoint(node(MAP_ONE, "target"))),
            Collections.singletonList(contributor));
        GraphProjection projection = GraphProjection.projected(9L, Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(edge), Collections.<RelationshipResolution>emptyList(), Collections.emptyList());
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        FreeplaneMapCommandExecutor maps = mock(FreeplaneMapCommandExecutor.class);
        InlineEdt edt = new InlineEdt();
        when(updates.currentProjection()).thenReturn(projection);
        when(updates.currentState()).thenReturn(availableState());
        when(updates.hasPendingChanges()).thenReturn(false);
        when(store.currentDocument()).thenReturn(document());
        DefaultContributorDeletionHandler handler =
            new DefaultContributorDeletionHandler(updates, store, maps, edt);

        GraphCommandResult result = handler.deleteAll(GraphCommands.deleteAllContributors(9L, edge.key(),
            Collections.<ContributorKey>emptyList(), Collections.<ContributorKey, ConnectorDescriptor>emptyMap()));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
    }

    private static GraphCommandResult applied(String key) {
        return GraphCommandResult.from(WorkspaceTransition.applied(document(), key));
    }

    private static GraphProjection edgeProjection(long generation, ProjectedEdge edge) {
        return GraphProjection.projected(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(edge), Collections.<RelationshipResolution>emptyList(), Collections.emptyList());
    }

    private static GraphProjection projection(long generation) {
        return GraphProjection.projected(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.<RelationshipResolution>emptyList(), Collections.emptyList());
    }

    private static CanvasState availableState() {
        return CanvasState.of(0L, projection(0L),
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.IDLE);
    }

    private static WorkspaceDocument document(GraphRelationshipRecord... records) {
        return WorkspaceDocument.createVersion1(WorkspaceId.of("00000000-0000-0000-0000-000000000400"))
            .toBuilder()
            .maps(Arrays.asList(
                MapReference.of(MAP_ONE, 1L, URI.create("one.mm"), true, "#4E79A7",
                    Collections.<UnknownXml>emptyList()),
                MapReference.of(MAP_TWO, 2L, URI.create("two.mm"), true, "#F28E2B",
                    Collections.<UnknownXml>emptyList())))
            .relationships(Arrays.asList(records)).build();
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(node(map, id));
    }

    private static NodeReference node(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static ConnectorDescriptor descriptor(SourceNodeKey source, MapReferenceId map, String target) {
        return ConnectorDescriptor.of(source, node(map, target), false, true, "", "", "");
    }

    private static ProjectedEndpointKey endpoint(SourceNodeKey source) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source));
    }

    private static ProjectedEndpointKey endpoint(NodeReference node) {
        return endpoint(SourceNodeKey.persisted(node));
    }

    private static final class InlineEdt implements EdtExecutor {
        private int callCount;
        private boolean inCall;

        @Override
        public <T> T call(Callable<T> task) {
            callCount++;
            inCall = true;
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new AssertionError(failure);
            }
            finally {
                inCall = false;
            }
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isEdt() {
            return inCall;
        }
    }
}

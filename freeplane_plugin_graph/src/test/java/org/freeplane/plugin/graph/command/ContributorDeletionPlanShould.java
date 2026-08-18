package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    public void returnsNativeMapIdsInDeterministicOrder() {
        SourceNodeKey firstSource = source(MAP_ONE, "first-source");
        SourceNodeKey secondSource = source(MAP_TWO, "second-source");
        ContributorDeletionPlan.NativeEdit first = ContributorDeletionPlan.NativeEdit.of(
            ContributorKey.nativeConnector(MAP_ONE, firstSource, 0),
            descriptor(firstSource, MAP_ONE, "first-target"));
        ContributorDeletionPlan.NativeEdit second = ContributorDeletionPlan.NativeEdit.of(
            ContributorKey.nativeConnector(MAP_TWO, secondSource, 0),
            descriptor(secondSource, MAP_TWO, "second-target"));
        Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> grouped =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        grouped.put(MAP_TWO, Collections.singletonList(second));
        grouped.put(MAP_ONE, Collections.singletonList(first));

        ContributorDeletionPlan plan = ContributorDeletionPlan.of(grouped, Collections.<RelationshipId>emptySet());

        assertThat(plan.nativeMapIds()).containsExactly(MAP_ONE, MAP_TWO);
        assertThatThrownBy(() -> plan.nativeMapIds().clear())
            .isInstanceOf(UnsupportedOperationException.class);
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
    public void requiresDeleteAllToPrevalidateEveryNonemptyContributorBeforeBeginningTheTransaction() {
        SourceNodeKey source = source(MAP_ONE, "source");
        NodeReference firstTarget = node(MAP_ONE, "first-target");
        NodeReference secondTarget = firstTarget;
        ConnectorDescriptor firstDescriptor = descriptor(source, MAP_ONE, "first-target");
        ConnectorDescriptor secondDescriptor = descriptor(source, MAP_ONE, "first-target");
        ConnectorDescriptor changedSecondDescriptor = ConnectorDescriptor.of(source, secondTarget, true, false,
            "changed", "", "");
        EdgeContributor first = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(0, firstDescriptor), endpoint(source),
            endpoint(firstTarget));
        EdgeContributor secondBefore = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(1, secondDescriptor), endpoint(source),
            endpoint(secondTarget));
        EdgeContributor secondAfter = EdgeContributor.nativeConnector(
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(1, changedSecondDescriptor), endpoint(source),
            endpoint(secondTarget));
        ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(endpoint(source), endpoint(firstTarget));
        GraphProjection before = edgeProjection(9L, ProjectedEdge.of(edgeKey, Arrays.asList(first, secondBefore)));
        GraphProjection after = edgeProjection(9L, ProjectedEdge.of(edgeKey, Arrays.asList(first, secondAfter)));
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
        List<ContributorKey> requested = Arrays.asList(first.key(), secondBefore.key());
        Map<ContributorKey, ConnectorDescriptor> expected = new LinkedHashMap<ContributorKey, ConnectorDescriptor>();
        expected.put(first.key(), firstDescriptor);
        expected.put(secondBefore.key(), secondDescriptor);

        GraphCommandResult result = handler.deleteAll(GraphCommands.deleteAllContributors(9L, edgeKey, requested,
            expected));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.changed");
        verify(maps, never()).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(store, never()).execute(any(WorkspaceCommand.class));
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

    @Test
    public void retainsIncompleteRecoveryAcrossHandlerInvocationsBeforeStartingNewWork() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record), "workspace.rejected")));
        doThrow(new IllegalStateException("rollback failure")).when(fixture.transaction).rollback();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult first = handler.deleteAll(fixture.command());
        GraphCommandResult second = handler.deleteAll(fixture.command());

        assertThat(first.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(first.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(first.messageArguments()).containsExactly("native");
        assertThat(first.dirtySourceMaps()).containsExactly(MAP_ONE);
        assertThat(first.editorViewActivated()).isFalse();
        assertThat(second.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(second.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(second.messageArguments()).containsExactly("native");
        assertThat(second.dirtySourceMaps()).containsExactly(MAP_ONE);
        verify(fixture.transaction, times(2)).rollback();
        verify(fixture.maps, times(1)).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(fixture.store, times(1)).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void retriesRecoveryBeforeASecondCommandAndClearsItAfterSuccess() {
        MixedFixture fixture = mixedFixture();
        List<String> events = new java.util.ArrayList<String>();
        final boolean[] firstRollbackFailure = new boolean[] { true };
        doAnswer(invocation -> {
            events.add("recovery");
            if (firstRollbackFailure[0]) {
                firstRollbackFailure[0] = false;
                throw new IllegalStateException("rollback failure");
            }
            return null;
        }).when(fixture.transaction).rollback();
        FreeplaneMapCommandExecutor.ContributorDeletionTransaction recovered =
            mock(FreeplaneMapCommandExecutor.ContributorDeletionTransaction.class);
        when(recovered.outcome()).thenReturn(applied("native.recovered"));
        when(recovered.dirtySourceMaps()).thenReturn(Collections.singleton(MAP_ONE));
        when(recovered.editorViewActivated()).thenReturn(false);
        final int[] beginCalls = new int[] { 0 };
        when(fixture.maps.beginContributorDeletion(any(ContributorDeletionPlan.class)))
            .thenAnswer(invocation -> {
                beginCalls[0]++;
                events.add("begin");
                return beginCalls[0] == 1 ? fixture.transaction : recovered;
            });
        final int[] workspaceCalls = new int[] { 0 };
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenAnswer(invocation -> {
            events.add("workspace");
            workspaceCalls[0]++;
            return workspaceCalls[0] == 1
                ? GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record), "workspace.rejected"))
                : GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "relationships.purged"));
        });
        doAnswer(invocation -> {
            events.add("commit");
            return null;
        }).when(recovered).commit();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult first = handler.deleteAll(fixture.command());
        GraphCommandResult second = handler.deleteAll(fixture.command());

        assertThat(first.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(first.dirtySourceMaps()).containsExactly(MAP_ONE);
        assertThat(second.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(second.dirtySourceMaps()).containsExactly(MAP_ONE);
        assertThat(second.editorViewActivated()).isFalse();
        assertThat(events).containsExactly("begin", "workspace", "recovery", "recovery", "begin", "workspace",
            "commit");
        verify(fixture.transaction, times(2)).rollback();
        verify(fixture.transaction, never()).commit();
        verify(recovered).commit();
        verify(fixture.maps, times(2)).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(fixture.store, times(2)).execute(any(WorkspaceCommand.class));
    }

    @Test
    public void reportsIncompleteRecoveryWhenNativeRollbackFailsAfterWorkspaceRejection() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record), "workspace.rejected")));
        doThrow(new IllegalStateException("rollback failure")).when(fixture.transaction).rollback();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult result = handler.deleteAll(fixture.command());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(result.dirtySourceMaps()).containsExactly(MAP_ONE);
        verify(fixture.store).execute(any(WorkspaceCommand.class));
        verify(fixture.transaction).rollback();
        verify(fixture.transaction, never()).commit();
    }

    @Test
    public void compensatesPublishedWorkspaceTransitionWhenNativeCommitFails() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "relationships.purged")));
        when(fixture.store.undo()).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "history.undone")));
        doThrow(new IllegalStateException("native commit failure")).when(fixture.transaction).commit();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult result = handler.deleteAll(fixture.command());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(result.messageArguments()).contains("native");
        verify(fixture.store).execute(any(WorkspaceCommand.class));
        verify(fixture.store).undo();
        verify(fixture.transaction).commit();
        verify(fixture.transaction).rollback();
    }

    @Test
    public void reportsNativeCommitFailureAfterBothPhasesAreCompensated() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "relationships.purged")));
        when(fixture.store.undo()).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "history.undone")));
        final boolean[] nativeRecoveryComplete = new boolean[] { false };
        when(fixture.transaction.outcome()).thenAnswer(invocation -> nativeRecoveryComplete[0]
            ? GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record),
                "graph_workspace.contributor.native_commit_failed"))
            : applied("native"));
        doThrow(new IllegalStateException("native commit failure")).when(fixture.transaction).commit();
        doAnswer(invocation -> {
            nativeRecoveryComplete[0] = true;
            return null;
        }).when(fixture.transaction).rollback();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult result = handler.deleteAll(fixture.command());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.native_commit_failed");
        verify(fixture.store).undo();
        verify(fixture.transaction).commit();
        verify(fixture.transaction).rollback();
    }

    @Test
    public void reportsIncompleteRecoveryWhenWorkspaceCompensationFails() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "relationships.purged")));
        when(fixture.store.undo()).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record), "workspace.undo.rejected")));
        doThrow(new IllegalStateException("native commit failure")).when(fixture.transaction).commit();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult result = handler.deleteAll(fixture.command());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(result.messageArguments()).contains("native_and_workspace");
        assertThat(result.dirtySourceMaps()).containsExactly(MAP_ONE);
        verify(fixture.store).undo();
        verify(fixture.transaction).commit();
        verify(fixture.transaction).rollback();
    }

    @Test
    public void retriesWorkspaceCompensationBeforeStartingLaterDeletionAndRetainsItUntilSuccess() {
        MixedFixture fixture = mixedFixture();
        final List<String> events = new java.util.ArrayList<String>();
        final boolean[] nativeRolledBack = new boolean[] { false };
        final int[] beginCalls = new int[] { 0 };
        final int[] workspaceCalls = new int[] { 0 };
        final int[] undoCalls = new int[] { 0 };
        doAnswer(invocation -> {
            events.add("begin");
            beginCalls[0]++;
            return beginCalls[0] == 1 ? fixture.transaction : recoveredTransaction(events);
        }).when(fixture.maps).beginContributorDeletion(any(ContributorDeletionPlan.class));
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenAnswer(invocation -> {
            events.add("workspace");
            workspaceCalls[0]++;
            return GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record),
                "relationships.purged"));
        });
        doAnswer(invocation -> {
            events.add("native-commit");
            throw new IllegalStateException("native commit failure");
        }).when(fixture.transaction).commit();
        doAnswer(invocation -> {
            events.add("native-rollback");
            nativeRolledBack[0] = true;
            return null;
        }).when(fixture.transaction).rollback();
        when(fixture.transaction.outcome()).thenAnswer(invocation -> nativeRolledBack[0]
            ? GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record),
                "graph_workspace.contributor.native_commit_failed"))
            : applied("native"));
        doAnswer(invocation -> {
            events.add("workspace-undo");
            undoCalls[0]++;
            if (undoCalls[0] == 1) {
                throw new IllegalStateException("workspace undo failure");
            }
            if (undoCalls[0] == 2) {
                return GraphCommandResult.from(WorkspaceTransition.rejected(document(fixture.record),
                    "workspace.undo.rejected"));
            }
            return GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record),
                "history.undone"));
        }).when(fixture.store).undo();

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult first = handler.deleteAll(fixture.command());
        GraphCommandResult repeatedFailure = handler.deleteAll(fixture.command());
        GraphCommandResult recovered = handler.deleteAll(fixture.command());

        assertThat(first.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(first.messageArguments()).containsExactly("workspace");
        assertThat(repeatedFailure.messageKey()).isEqualTo("graph_workspace.contributor.undo_incomplete");
        assertThat(repeatedFailure.messageArguments()).containsExactly("workspace");
        assertThat(recovered.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(events).containsExactly("begin", "workspace", "native-commit", "native-rollback",
            "workspace-undo", "workspace-undo", "workspace-undo", "begin", "workspace",
            "native-recovered-commit");
        assertThat(undoCalls[0]).isEqualTo(3);
        assertThat(beginCalls[0]).isEqualTo(2);
        assertThat(workspaceCalls[0]).isEqualTo(2);
        verify(fixture.transaction, times(1)).rollback();
        verify(fixture.maps, times(2)).beginContributorDeletion(any(ContributorDeletionPlan.class));
        verify(fixture.store, times(2)).execute(any(WorkspaceCommand.class));
    }

    private static FreeplaneMapCommandExecutor.ContributorDeletionTransaction recoveredTransaction(
            final List<String> events) {
        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            mock(FreeplaneMapCommandExecutor.ContributorDeletionTransaction.class);
        when(transaction.outcome()).thenReturn(applied("native.recovered"));
        when(transaction.dirtySourceMaps()).thenReturn(Collections.singleton(MAP_ONE));
        when(transaction.editorViewActivated()).thenReturn(false);
        doAnswer(invocation -> {
            events.add("native-recovered-commit");
            return null;
        }).when(transaction).commit();
        return transaction;
    }

    @Test
    public void completesMixedDeletionWithOneWorkspacePurgeAndOneNativeCommit() {
        MixedFixture fixture = mixedFixture();
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document(fixture.record), "relationships.purged")));

        DefaultContributorDeletionHandler handler = fixture.handler();
        GraphCommandResult result = handler.deleteAll(fixture.command());

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        verify(fixture.store).execute(any(WorkspaceCommand.class));
        verify(fixture.store, never()).undo();
        verify(fixture.transaction).commit();
        verify(fixture.transaction, never()).rollback();
    }

    private static MixedFixture mixedFixture() {
        SourceNodeKey source = source(MAP_ONE, "source");
        NodeReference nativeTarget = node(MAP_ONE, "target");
        GraphRelationshipRecord record = GraphRelationshipRecord.of(RELATIONSHIP, 1L,
            node(MAP_ONE, "source"), node(MAP_TWO, "target"), RelationshipDirection.FORWARD,
            Collections.<UnknownXml>emptyList());
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
        ContributorKey relationshipKey = ContributorKey.graphRelationship(RELATIONSHIP);
        when(relationshipContributor.key()).thenReturn(relationshipKey);
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
        when(maps.beginContributorDeletion(any(ContributorDeletionPlan.class))).thenReturn(transaction);
        return new MixedFixture(updates, store, maps, edt, transaction, record, edge,
            nativeContributor, relationshipContributor);
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

    private static final class MixedFixture {
        private final GraphUpdateCoordinator updates;
        private final GraphWorkspaceStore store;
        private final FreeplaneMapCommandExecutor maps;
        private final InlineEdt edt;
        private final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction;
        private final GraphRelationshipRecord record;
        private final ProjectedEdge edge;
        private final EdgeContributor nativeContributor;
        private final EdgeContributor relationshipContributor;

        private MixedFixture(final GraphUpdateCoordinator updates, final GraphWorkspaceStore store,
                final FreeplaneMapCommandExecutor maps, final InlineEdt edt,
                final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction,
                final GraphRelationshipRecord record, final ProjectedEdge edge,
                final EdgeContributor nativeContributor, final EdgeContributor relationshipContributor) {
            this.updates = updates;
            this.store = store;
            this.maps = maps;
            this.edt = edt;
            this.transaction = transaction;
            this.record = record;
            this.edge = edge;
            this.nativeContributor = nativeContributor;
            this.relationshipContributor = relationshipContributor;
        }

        private DefaultContributorDeletionHandler handler() {
            return new DefaultContributorDeletionHandler(updates, store, maps, edt);
        }

        private GraphCommands.DeleteAllContributors command() {
            List<ContributorKey> requested = Arrays.asList(nativeContributor.key(), relationshipContributor.key());
            Map<ContributorKey, ConnectorDescriptor> expected =
                Collections.singletonMap(nativeContributor.key(), nativeContributor.connectorDescriptor().get());
            return GraphCommands.deleteAllContributors(9L, edge.key(), requested, expected);
        }
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

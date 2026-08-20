package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class WorkspaceDialogsShould {
    private static final MapReferenceId MAP_ONE = mapId(1L);
    private static final MapReferenceId MAP_TWO = mapId(2L);
    private static final RelationshipId MISSING_ID = RelationshipId.of("00000000-0000-0000-0000-000000000101");
    private static final RelationshipId RECOVERABLE_ID = RelationshipId.of("00000000-0000-0000-0000-000000000102");

    @Test
    public void focusesTheGraphOnceOnlyForAnEditorActivatingCommandResultAfterRouting() {
        final GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        final org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding binding =
            mock(org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding.class);
        when(binding.currentViewport()).thenReturn(org.freeplane.plugin.graph.workspace.model.Viewport.of(0.0, 0.0,
            1.0, Collections.<UnknownXml>emptyList()));
        when(binding.currentCanvasState()).thenReturn(stateWithCounts(0, 0, Collections.emptyList()));
        when(binding.currentMapRows()).thenReturn(Collections.emptyList());
        final GraphCommandResult activating = mock(GraphCommandResult.class);
        final GraphCommandResult nonActivating = mock(GraphCommandResult.class);
        when(activating.editorViewActivated()).thenReturn(true);
        when(nonActivating.editorViewActivated()).thenReturn(false);
        final List<String> events = new ArrayList<String>();
        when(handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
            events.add("route");
            return events.size() == 1 ? activating : nonActivating;
        });
        final AtomicInteger focusCount = new AtomicInteger();
        final GraphWorkspaceWindowModel[] holder = new GraphWorkspaceWindowModel[1];
        GraphWorkspaceWindow.runOnEdt(() -> holder[0] = new GraphWorkspaceWindowModel(handle, binding,
            mock(GraphWorkspaceController.class), () -> null, mock(WorkspaceCloseController.class), () -> { },
            () -> {
                events.add("focus");
                focusCount.incrementAndGet();
            }, () -> { }));

        holder[0].execute(GraphCommands.retrySave());
        holder[0].execute(GraphCommands.restartLayout());

        assertThat(events).containsExactly("route", "focus", "route");
        assertThat(focusCount).hasValue(1);
        holder[0].close();
    }
    @Test
    public void rendersOperationalStatusAndIndependentScaleWarnings() {
        CanvasState state = stateWithCounts(2, 1, Arrays.asList(
            missingResolution(MISSING_ID), recoverableResolution(RECOVERABLE_ID)));
        List<org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding.MapRegistration> maps = Arrays.asList(
            registration(MAP_ONE, "Source map", MapAvailability.AVAILABLE),
            registration(MAP_TWO, "Missing map", MapAvailability.MISSING));
        WorkspaceSessionStatus session = WorkspaceSessionStatus.of(true, true, false, true,
            Collections.singleton(MAP_ONE), Optional.empty());

        GraphStatusBar bar = new GraphStatusBar(command -> { });
        bar.setStatus(state, Optional.of("Safe endpoint"), maps, session, false);

        assertThat(bar.status().mapStatuses()).extracting(GraphStatusBar.MapStatus::availability)
            .containsExactly(MapAvailability.AVAILABLE, MapAvailability.MISSING);
        assertThat(bar.status().projectedNodeCount()).isEqualTo(2);
        assertThat(bar.status().projectedEdgeCount()).isEqualTo(1);
        assertThat(bar.status().selectedEndpointText()).isEqualTo("Safe endpoint");
        assertThat(bar.status().layoutStatus()).isEqualTo(OperationalStatus.IDLE);
        assertThat(bar.status().recoverableCount()).isEqualTo(1);
        assertThat(bar.status().missingNodeCount()).isEqualTo(1);
        assertThat(bar.status().workspaceDirty()).isTrue();
        assertThat(bar.status().saveFailed()).isTrue();
        assertThat(bar.status().dirtySourceMapNames()).containsExactly("Source map");
        assertThat(bar.status().dirtySourceMapCount()).isEqualTo(1);
        assertThat(bar.status().workspaceUndoAvailable()).isTrue();
        assertThat(bar.status().workspaceRedoAvailable()).isFalse();
        assertThat(bar.status().workspaceHistoryAvailable()).isTrue();
        assertThat(bar.nodeWarning().isVisible()).isFalse();
        assertThat(bar.edgeWarning().isVisible()).isFalse();

        bar.setStatus(stateWithCounts(2000, 1, Collections.emptyList()), Optional.empty(), maps, session, false);
        assertThat(bar.nodeWarning().isVisible()).isTrue();
        assertThat(bar.edgeWarning().isVisible()).isFalse();
        bar.setStatus(stateWithCounts(2, 5000, Collections.emptyList()), Optional.empty(), maps, session, false);
        assertThat(bar.nodeWarning().isVisible()).isFalse();
        assertThat(bar.edgeWarning().isVisible()).isTrue();
    }

    @Test
    public void routesStatusActionsAndKeepsLayoutRestartAvailableReadOnly() {
        List<GraphCommand> commands = new ArrayList<GraphCommand>();
        GraphStatusBar bar = new GraphStatusBar(commands::add);
        bar.setReadOnly(true);

        assertThat(bar.retrySaveButton().isEnabled()).isFalse();
        assertThat(bar.unpinAllButton().isEnabled()).isFalse();
        assertThat(bar.restartLayoutButton().isEnabled()).isTrue();
        bar.retrySaveButton().doClick();
        bar.unpinAllButton().doClick();
        bar.restartLayoutButton().doClick();

        assertThat(commands).extracting("class").containsExactly(GraphCommands.RestartLayout.class);
        bar.setReadOnly(false);
        bar.retrySaveButton().doClick();
        bar.unpinAllButton().doClick();
        assertThat(commands).extracting("class").containsExactly(GraphCommands.RestartLayout.class,
            GraphCommands.RetrySave.class, GraphCommands.UnpinAll.class);
    }

    @Test
    public void capturesContributorRowsAndEmitsExactDisplayedDeletionInputs() {
        SourceNodeKey source = SourceNodeKey.transientPath(MAP_ONE, Collections.singletonList(Integer.valueOf(0)));
        SourceNodeKey target = SourceNodeKey.transientPath(MAP_ONE, Collections.singletonList(Integer.valueOf(1)));
        ProjectedNodeKey sourceKey = ProjectedNodeKey.of(source);
        ProjectedNodeKey targetKey = ProjectedNodeKey.of(target);
        ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(sourceKey),
            ProjectedEndpointKey.ofNode(targetKey));
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, NodeReference.of(MAP_ONE,
            PersistedNodeId.of("target")), false, true, "source", "middle", "target");
        EdgeContributor nativeContributor = nativeContributor(0, source, targetKey, descriptor);
        EdgeContributor secondNativeContributor = nativeContributor(1, source, targetKey,
            ConnectorDescriptor.of(source, NodeReference.of(MAP_ONE, PersistedNodeId.of("target")), true, false,
                "source-2", "middle-2", "target-2"));
        ProjectedEdge edge = ProjectedEdge.of(edgeKey, Arrays.asList(nativeContributor, secondNativeContributor));
        GraphProjection projection = GraphProjection.projected(17L,
            Arrays.asList(ProjectedNode.of(sourceKey, SafeNodeLabel.of("secret source", "Source"), "Map one", false),
                ProjectedNode.of(targetKey, SafeNodeLabel.of("secret target", "Target"), "Map one", false)),
            Collections.emptyList(), Collections.singletonList(edge), Collections.emptyList(), Collections.emptyList());
        List<GraphWorkspaceViewBinding.MapRegistration> maps = Arrays.asList(
            registration(MAP_ONE, "Source map", MapAvailability.AVAILABLE),
            registration(MAP_TWO, "Target map", MapAvailability.AVAILABLE));
        List<GraphCommand> commands = new ArrayList<GraphCommand>();
        ContributorInspector inspector = new ContributorInspector(17L, projection, edge, maps, commands::add);

        assertThat(inspector.displayedGeneration()).isEqualTo(17L);
        assertThat(inspector.edgeKey()).isEqualTo(edgeKey);
        assertThat(inspector.rows()).hasSize(2);
        assertThat(inspector.rows().get(0).sourceLabel()).isEqualTo("Source");
        assertThat(inspector.rows().get(0).targetLabel()).isEqualTo("Target");
        assertThat(inspector.rows().get(0).middleLabel()).isEqualTo("middle");
        assertThat(inspector.rows().get(0).ownerDisplayName()).isEqualTo("Source map");
        assertThat(inspector.rows().get(0).connectorDescriptor()).contains(descriptor);
        assertThat(inspector.rows().get(1).connectorDescriptor()).contains(secondNativeContributor.connectorDescriptor().get());

        inspector.deleteOne(nativeContributor.key());
        assertThat(commands.get(0)).isInstanceOf(GraphCommands.DeleteContributor.class);
        GraphCommands.DeleteContributor one = (GraphCommands.DeleteContributor) commands.get(0);
        assertThat(one.displayedGeneration()).isEqualTo(17L);
        assertThat(one.contributor()).isEqualTo(nativeContributor.key());
        assertThat(one.expectedConnector()).contains(descriptor);

        inspector.deleteAll();
        GraphCommands.DeleteAllContributors all = (GraphCommands.DeleteAllContributors) commands.get(1);
        assertThat(all.displayedGeneration()).isEqualTo(17L);
        assertThat(all.edge()).isEqualTo(edgeKey);
        assertThat(all.contributors()).containsExactly(nativeContributor.key(), secondNativeContributor.key());
        assertThat(all.expectedConnectors()).containsEntry(nativeContributor.key(), descriptor);
        assertThat(all.expectedConnectors()).containsEntry(secondNativeContributor.key(),
            secondNativeContributor.connectorDescriptor().get());
    }

    @Test
    public void purgesOnlyMissingRowsWithTheGenerationAndPrecomputedDescriptions() {
        List<PurgeConfirmationDialog.MissingRow> rows = Arrays.asList(
            PurgeConfirmationDialog.MissingRow.of(MISSING_ID, "Source map / Missing source",
                "Target map / Missing target"));
        List<GraphCommand> commands = new ArrayList<GraphCommand>();
        PurgeConfirmationDialog dialog = new PurgeConfirmationDialog(29L, rows, commands::add);

        assertThat(dialog.rows()).containsExactlyElementsOf(rows);
        assertThat(dialog.rows().get(0).sourceDescription()).isEqualTo("Source map / Missing source");
        assertThat(dialog.rows().get(0).targetDescription()).isEqualTo("Target map / Missing target");
        assertThat(dialog.purgeButton().isEnabled()).isTrue();
        dialog.purge();
        GraphCommands.Purge purge = (GraphCommands.Purge) commands.get(0);
        assertThat(purge.displayedGeneration()).isEqualTo(29L);
        assertThat(purge.relationships()).containsExactly(MISSING_ID);

        PurgeConfirmationDialog empty = new PurgeConfirmationDialog(99L, Collections.emptyList(), commands::add);
        assertThat(empty.purgeButton().isEnabled()).isFalse();
        empty.purge();
        assertThat(commands).hasSize(1);
    }

    @Test
    public void closeActionsCompleteOnlyAfterTheCloseControllerSucceeds() {
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        when(close.retrySaveAndClose()).thenReturn(false, true);
        when(close.discardAndClose()).thenReturn(false, true);
        List<String> completed = new ArrayList<String>();
        WorkspaceCloseDialog dialog = new WorkspaceCloseDialog(close, () -> completed.add("closed"));

        dialog.retryButton().doClick();
        assertThat(completed).isEmpty();
        dialog.retryButton().doClick();
        assertThat(completed).containsExactly("closed");
        dialog.discardButton().doClick();
        dialog.discardButton().doClick();
        dialog.cancelButton().doClick();

        verify(close, org.mockito.Mockito.times(2)).retrySaveAndClose();
        verify(close, org.mockito.Mockito.times(2)).discardAndClose();
        verify(close).cancelClose();
        assertThat(completed).containsExactly("closed", "closed");
    }

    private static CanvasState stateWithCounts(int nodes, int edges, List<RelationshipResolution> resolutions) {
        List<ProjectedNode> projectedNodes = new ArrayList<ProjectedNode>();
        for (int index = 0; index < nodes; index++) {
            MapReferenceId map = index % 2 == 0 ? MAP_ONE : MAP_TWO;
            SourceNodeKey source = SourceNodeKey.transientPath(map, Collections.singletonList(Integer.valueOf(index)));
            ProjectedNodeKey key = ProjectedNodeKey.of(source);
            projectedNodes.add(ProjectedNode.of(key, SafeNodeLabel.of("full-" + index, "Node " + index),
                map.equals(MAP_ONE) ? "Map one" : "Map two", false));
        }
        List<ProjectedEdge> projectedEdges = new ArrayList<ProjectedEdge>();
        if (edges > 0) {
            ProjectedNodeKey first = projectedNodes.get(0).key();
            ProjectedNodeKey second = projectedNodes.size() > 1 ? projectedNodes.get(1).key() : first;
            if (!first.equals(second)) {
                EdgeContributor contributor = EdgeContributor.graphRelationship(relationship(3L),
                    ProjectedEndpointKey.ofNode(first), ProjectedEndpointKey.ofNode(second));
                ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(first),
                    ProjectedEndpointKey.ofNode(second)), Collections.singletonList(contributor));
                for (int index = 0; index < edges; index++) {
                    projectedEdges.add(edge);
                }
            }
        }
        GraphProjection projection = GraphProjection.projected(7L, projectedNodes, Collections.emptyList(),
            projectedEdges, resolutions, Collections.emptyList());
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode node : projectedNodes) {
            LayoutPoint point = LayoutPoint.of(0.0, 0.0);
            geometry.put(node.key(), NodeGeometry.of(point, 10.0));
            positions.put(node.key(), point);
        }
        return CanvasState.of(7L, projection, LayoutFrame.of(7L,
            LayoutPositions.of(positions, Collections.emptyMap()), false), GraphGeometry.of(geometry,
                Collections.emptyMap()), OperationalStatus.IDLE);
    }

    private static RelationshipResolution missingResolution(RelationshipId id) {
        GraphRelationshipRecord record = relationship(id.value().getLeastSignificantBits(),
            NodeReference.of(MAP_ONE, PersistedNodeId.of("missing-source")),
            NodeReference.of(MAP_TWO, PersistedNodeId.of("missing-target")));
        return RelationshipResolution.of(record, RelationshipStatus.UNRESOLVED_MISSING_NODE,
            Optional.empty(), Optional.empty(), Collections.emptySet());
    }

    private static RelationshipResolution recoverableResolution(RelationshipId id) {
        GraphRelationshipRecord record = relationship(id.value().getLeastSignificantBits(),
            NodeReference.of(MAP_ONE, PersistedNodeId.of("recoverable-source")),
            NodeReference.of(MAP_TWO, PersistedNodeId.of("recoverable-target")));
        return RelationshipResolution.of(record, RelationshipStatus.UNRESOLVED_RECOVERABLE,
            Optional.empty(), Optional.empty(), Collections.singleton(
                org.freeplane.plugin.graph.projection.RecoverableReason.MAP_MISSING));
    }

    private static EdgeContributor nativeContributor(int occurrence, SourceNodeKey source, ProjectedNodeKey target,
            ConnectorDescriptor descriptor) {
        org.freeplane.plugin.graph.projection.input.ConnectorSnapshot snapshot =
            org.freeplane.plugin.graph.projection.input.ConnectorSnapshot.of(occurrence, descriptor);
        return EdgeContributor.nativeConnector(snapshot, ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source)),
            ProjectedEndpointKey.ofNode(target));
    }

    private static GraphRelationshipRecord relationship(long sequence) {
        return relationship(sequence, NodeReference.of(MAP_ONE, PersistedNodeId.of("source-" + sequence)),
            NodeReference.of(MAP_TWO, PersistedNodeId.of("target-" + sequence)));
    }

    private static GraphRelationshipRecord relationship(long sequence, NodeReference source, NodeReference target) {
        return GraphRelationshipRecord.of(RelationshipId.of(UUID.nameUUIDFromBytes(
            ("relationship-" + sequence).getBytes(java.nio.charset.StandardCharsets.UTF_8))), sequence,
            source, target, RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
    }

    private static MapReferenceId mapId(long value) {
        return MapReferenceId.of(UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value)));
    }

    private static GraphWorkspaceViewBinding.MapRegistration registration(MapReferenceId id, String name,
            MapAvailability availability) {
        return GraphWorkspaceViewBinding.MapRegistration.of(id, name, availability);
    }
}

package org.freeplane.plugin.ai.tools.edit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.junit.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BatchEditToolTest {
    @Test
    public void rejectOnAnyIncompatible_returnsOnlyIncompatibleRejectedTargetsAndSkipsWrites() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AvailableMaps.MapAccessListener mapAccessListener = mock(AvailableMaps.MapAccessListener.class);
        NodeContentEditor nodeContentEditor = mock(NodeContentEditor.class);
        BatchEditTool tool = new BatchEditTool(availableMaps, mapAccessListener, nodeContentEditor);
        String mapIdentifier = UUID.randomUUID().toString();
        MapModel mapModel = mock(MapModel.class);
        NodeModel nodeA = mock(NodeModel.class);
        NodeModel nodeB = mock(NodeModel.class);
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), mapAccessListener)).thenReturn(mapModel);
        when(mapModel.getNodeForID("A")).thenReturn(nodeA);
        when(mapModel.getNodeForID("B")).thenReturn(nodeB);

        NodeContentEditItem item = new NodeContentEditItem(
            Arrays.asList("A", "B"), EditedElement.STYLE, null, null, null, EditOperation.DELETE, null);
        EditRequest request = new EditRequest(
            mapIdentifier,
            "summary",
            EditCompatibilityPolicy.REJECT_ON_ANY_INCOMPATIBLE,
            Collections.singletonList(item));

        doThrow(new IllegalArgumentException("Missing style name."))
            .when(nodeContentEditor).validate(eq(nodeA), any(List.class));

        List<EditResultItem> result = tool.edit(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(EditTargetStatus.REJECTED);
        assertThat(result.get(0).getNodeIdentifier()).isEqualTo("A");
        assertThat(result.get(0).getIncompatibleFieldReasons()).containsExactly("Missing style name.");

        verify(nodeContentEditor).validate(eq(nodeA), any(List.class));
        verify(nodeContentEditor).validate(eq(nodeB), any(List.class));
        verifyNoMoreInteractions(nodeContentEditor);
    }

    @Test
    public void rejectOnAnyIncompatible_continuesAfterApplyFailuresAndReportsFailedStatus() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AvailableMaps.MapAccessListener mapAccessListener = mock(AvailableMaps.MapAccessListener.class);
        NodeContentEditor nodeContentEditor = mock(NodeContentEditor.class);
        BatchEditTool tool = new BatchEditTool(availableMaps, mapAccessListener, nodeContentEditor);
        String mapIdentifier = UUID.randomUUID().toString();
        MapModel mapModel = mock(MapModel.class);
        NodeModel nodeA = mock(NodeModel.class);
        NodeModel nodeB = mock(NodeModel.class);
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), mapAccessListener)).thenReturn(mapModel);
        when(mapModel.getNodeForID("A")).thenReturn(nodeA);
        when(mapModel.getNodeForID("B")).thenReturn(nodeB);

        NodeContentEditItem item = new NodeContentEditItem(
            Arrays.asList("A", "B"), EditedElement.STYLE, null, null, null, EditOperation.DELETE, null);
        EditRequest request = new EditRequest(
            mapIdentifier,
            "summary",
            EditCompatibilityPolicy.REJECT_ON_ANY_INCOMPATIBLE,
            Collections.singletonList(item));

        NodeContentItem contentA = mock(NodeContentItem.class);
        when(nodeContentEditor.edit(eq(nodeA), any(List.class))).thenReturn(contentA);
        when(nodeContentEditor.edit(eq(nodeB), any(List.class))).thenThrow(new RuntimeException("apply failed"));

        List<EditResultItem> result = tool.edit(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(EditTargetStatus.APPLIED);
        assertThat(result.get(1).getStatus()).isEqualTo(EditTargetStatus.FAILED);
        assertThat(result.get(1).getErrorMessage()).contains("apply failed");

        InOrder inOrder = inOrder(nodeContentEditor);
        inOrder.verify(nodeContentEditor).validate(eq(nodeA), any(List.class));
        inOrder.verify(nodeContentEditor).validate(eq(nodeB), any(List.class));
        inOrder.verify(nodeContentEditor).edit(eq(nodeA), any(List.class));
        inOrder.verify(nodeContentEditor).edit(eq(nodeB), any(List.class));
    }

    @Test
    public void defaultPolicy_skipsIncompatibleTargets() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        AvailableMaps.MapAccessListener mapAccessListener = mock(AvailableMaps.MapAccessListener.class);
        NodeContentEditor nodeContentEditor = mock(NodeContentEditor.class);
        BatchEditTool tool = new BatchEditTool(availableMaps, mapAccessListener, nodeContentEditor);
        String mapIdentifier = UUID.randomUUID().toString();
        MapModel mapModel = mock(MapModel.class);
        NodeModel nodeA = mock(NodeModel.class);
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), mapAccessListener)).thenReturn(mapModel);
        when(mapModel.getNodeForID("A")).thenReturn(nodeA);

        NodeContentEditItem item = new NodeContentEditItem(
            Collections.singletonList("A"), EditedElement.STYLE, null, null, null, EditOperation.DELETE, null);
        EditRequest request = new EditRequest(
            mapIdentifier,
            "summary",
            null,
            Collections.singletonList(item));

        doThrow(new IllegalArgumentException("Incompatible style."))
            .when(nodeContentEditor).validate(eq(nodeA), any(List.class));

        List<EditResultItem> result = tool.edit(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(EditTargetStatus.SKIPPED);
        assertThat(result.get(0).getIncompatibleFieldReasons()).containsExactly("Incompatible style.");
        verify(nodeContentEditor).validate(eq(nodeA), any(List.class));
        verifyNoMoreInteractions(nodeContentEditor);
    }
}

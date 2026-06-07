package org.freeplane.plugin.ai.tools.connectors;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.edits.AIEdits;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ConnectorItem;
import org.freeplane.plugin.ai.tools.edit.EditOperation;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectorEditToolTest {
    @Test
    public void editConnectors_addsConnectorAndAppliesLabels() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MLinkController linkController = mock(MLinkController.class);
        ConnectorEditTool uut = new ConnectorEditTool(availableMaps, null, linkController);
        UUID mapIdentifier = UUID.fromString("8fd6a4f3-2297-4ac1-8a2e-1e7406228d5e");
        MapModel mapModel = mock(MapModel.class);
        NodeModel sourceNode = new NodeModel("source", mapModel);
        NodeModel targetNode = new NodeModel("target", mapModel);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_source")).thenReturn(sourceNode);
        when(mapModel.getNodeForID("ID_target")).thenReturn(targetNode);
        when(mapModel.registryNode(sourceNode)).thenReturn("ID_source");
        ConnectorModel connector = mock(ConnectorModel.class);
        when(connector.getSource()).thenReturn(sourceNode);
        when(connector.getTargetID()).thenReturn("ID_target");
        when(connector.getSourceLabel()).thenReturn(Optional.empty());
        when(connector.getMiddleLabel()).thenReturn(Optional.empty());
        when(connector.getTargetLabel()).thenReturn(Optional.empty());
        when(linkController.addConnector(sourceNode, targetNode)).thenReturn(connector);

        ConnectorEditRequest request = new ConnectorEditRequest(
            mapIdentifier.toString(),
            Collections.singletonList(new ConnectorEditRequestItem(
                "ID_source",
                "ID_target",
                EditOperation.ADD,
                "source-label",
                "middle-label",
                "target-label",
                null,
                null,
                null)));

        ConnectorEditResponse response = uut.editConnectors(request);

        ConnectorEditResultItem result = response.getItems().get(0);
        assertThat(result.getAction()).isEqualTo("added");
        assertThat(result.getIgnoredAmbiguousConnectorCount()).isEqualTo(0);
        ConnectorItem connectorItem = result.getConnector();
        assertThat(connectorItem.getSourceNodeIdentifier()).isEqualTo("ID_source");
        assertThat(connectorItem.getTargetNodeIdentifier()).isEqualTo("ID_target");
        verify(linkController).setSourceLabel(connector, "source-label");
        verify(linkController).setMiddleLabel(connector, "middle-label");
        verify(linkController).setTargetLabel(connector, "target-label");
        assertThat(sourceNode.getExtension(AIEdits.class)).isNotNull();
    }

    @Test
    public void editConnectors_updatesFirstMatchingConnectorAndReportsIgnoredCount() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MLinkController linkController = mock(MLinkController.class);
        ConnectorEditTool uut = new ConnectorEditTool(availableMaps, null, linkController);
        UUID mapIdentifier = UUID.fromString("c4b69b9e-7f9b-4fc8-9f2b-5e8aa1b3b7c1");
        MapModel mapModel = mock(MapModel.class);
        NodeModel sourceNode = new NodeModel("source", mapModel);
        NodeModel targetNode = new NodeModel("target", mapModel);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_source")).thenReturn(sourceNode);
        when(mapModel.getNodeForID("ID_target")).thenReturn(targetNode);
        when(mapModel.registryNode(sourceNode)).thenReturn("ID_source");
        ConnectorModel firstConnector = mock(ConnectorModel.class);
        when(firstConnector.getSource()).thenReturn(sourceNode);
        when(firstConnector.getTargetID()).thenReturn("ID_target");
        when(firstConnector.getSourceLabel()).thenReturn(Optional.empty());
        when(firstConnector.getMiddleLabel()).thenReturn(Optional.empty());
        when(firstConnector.getTargetLabel()).thenReturn(Optional.empty());
        when(firstConnector.cloneForSource(sourceNode)).thenReturn(firstConnector);
        ConnectorModel secondConnector = mock(ConnectorModel.class);
        when(secondConnector.getSource()).thenReturn(sourceNode);
        when(secondConnector.getTargetID()).thenReturn("ID_target");
        when(secondConnector.getSourceLabel()).thenReturn(Optional.empty());
        when(secondConnector.getMiddleLabel()).thenReturn(Optional.empty());
        when(secondConnector.getTargetLabel()).thenReturn(Optional.empty());
        when(secondConnector.cloneForSource(sourceNode)).thenReturn(secondConnector);
        NodeLinks nodeLinks = NodeLinks.createLinkExtension(sourceNode);
        nodeLinks.addArrowlink(firstConnector);
        nodeLinks.addArrowlink(secondConnector);

        ConnectorEditRequest request = new ConnectorEditRequest(
            mapIdentifier.toString(),
            Collections.singletonList(new ConnectorEditRequestItem(
                "ID_source",
                "ID_target",
                EditOperation.REPLACE,
                "updated-source",
                null,
                null,
                null,
                null,
                null)));

        ConnectorEditResponse response = uut.editConnectors(request);

        ConnectorEditResultItem result = response.getItems().get(0);
        assertThat(result.getAction()).isEqualTo("updated");
        assertThat(result.getIgnoredAmbiguousConnectorCount()).isEqualTo(1);
        verify(linkController).setSourceLabel(firstConnector, "updated-source");
        assertThat(sourceNode.getExtension(AIEdits.class)).isNotNull();
    }

    @Test
    public void editConnectors_deletesFirstMatchingConnectorAndReportsIgnoredCount() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        MLinkController linkController = mock(MLinkController.class);
        ConnectorEditTool uut = new ConnectorEditTool(availableMaps, null, linkController);
        UUID mapIdentifier = UUID.fromString("3c5b2ec7-49f6-4e9d-9ad4-0c8cb44b9c3c");
        MapModel mapModel = mock(MapModel.class);
        NodeModel sourceNode = new NodeModel("source", mapModel);
        NodeModel targetNode = new NodeModel("target", mapModel);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_source")).thenReturn(sourceNode);
        when(mapModel.getNodeForID("ID_target")).thenReturn(targetNode);
        when(mapModel.registryNode(sourceNode)).thenReturn("ID_source");
        ConnectorModel firstConnector = mock(ConnectorModel.class);
        when(firstConnector.getSource()).thenReturn(sourceNode);
        when(firstConnector.getTargetID()).thenReturn("ID_target");
        when(firstConnector.getSourceLabel()).thenReturn(Optional.empty());
        when(firstConnector.getMiddleLabel()).thenReturn(Optional.empty());
        when(firstConnector.getTargetLabel()).thenReturn(Optional.empty());
        when(firstConnector.cloneForSource(sourceNode)).thenReturn(firstConnector);
        ConnectorModel secondConnector = mock(ConnectorModel.class);
        when(secondConnector.getSource()).thenReturn(sourceNode);
        when(secondConnector.getTargetID()).thenReturn("ID_target");
        when(secondConnector.getSourceLabel()).thenReturn(Optional.empty());
        when(secondConnector.getMiddleLabel()).thenReturn(Optional.empty());
        when(secondConnector.getTargetLabel()).thenReturn(Optional.empty());
        when(secondConnector.cloneForSource(sourceNode)).thenReturn(secondConnector);
        NodeLinks nodeLinks = NodeLinks.createLinkExtension(sourceNode);
        nodeLinks.addArrowlink(firstConnector);
        nodeLinks.addArrowlink(secondConnector);

        ConnectorEditRequest request = new ConnectorEditRequest(
            mapIdentifier.toString(),
            Collections.singletonList(new ConnectorEditRequestItem(
                "ID_source",
                "ID_target",
                EditOperation.DELETE,
                null,
                null,
                null,
                null,
                null,
                null)));

        ConnectorEditResponse response = uut.editConnectors(request);

        ConnectorEditResultItem result = response.getItems().get(0);
        assertThat(result.getAction()).isEqualTo("deleted");
        assertThat(result.getIgnoredAmbiguousConnectorCount()).isEqualTo(1);
        verify(linkController).removeArrowLink(firstConnector);
        assertThat(sourceNode.getExtension(AIEdits.class)).isNotNull();
    }
}

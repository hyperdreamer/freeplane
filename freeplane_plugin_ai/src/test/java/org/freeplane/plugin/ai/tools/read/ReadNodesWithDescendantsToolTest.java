package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.MapLinks;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.Clones;
import org.freeplane.features.map.FirstGroupNodeFlag;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.CloneMetadata;
import org.freeplane.plugin.ai.tools.content.ConnectorItem;
import org.freeplane.plugin.ai.tools.content.EditableContent;
import org.freeplane.plugin.ai.tools.content.EditableContentField;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.TextualContent;
import org.freeplane.plugin.ai.tools.search.OmissionReason;
import org.freeplane.plugin.ai.tools.search.Omissions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReadNodesWithDescendantsToolTest {
    private static class TestNodeLinks extends NodeLinks {
        private TestNodeLinks(List<NodeLinkModel> links) {
            super(links);
        }
    }

    @Test
    public void readNodesWithDescendants_returnsFocusAndSummaryChildrenWithBreadcrumbPath() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel parentNode = mock(NodeModel.class);
        NodeModel firstChildNode = mock(NodeModel.class);
        NodeModel secondChildNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("c33dd4d4-25f0-4bcb-8b57-f6d59cfb57f2");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.getParentNode()).thenReturn(parentNode);
        when(parentNode.getParentNode()).thenReturn(null);
        when(focusNode.getChildren()).thenReturn(Arrays.asList(firstChildNode, secondChildNode));
        when(focusNode.createID()).thenReturn("ID_focus");
        when(firstChildNode.createID()).thenReturn("ID_child_1");
        when(secondChildNode.createID()).thenReturn("ID_child_2");
        NodeContentResponse focusContent = new NodeContentResponse(
            null, new TextualContent("Focus full", null, null), null, null, null, null, null, null);
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL))).thenReturn(focusContent);
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(focusNode)).thenReturn("Focus");
        when(textController.getShortPlainText(parentNode)).thenReturn("Parent");
        when(textController.getShortPlainText(firstChildNode)).thenReturn("Child 1");
        when(textController.getShortPlainText(secondChildNode)).thenReturn("Child 2");
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController, objectMapper);

        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            Collections.singletonList(ContextSection.BREADCRUMB_PATH),
            null,
            null,
            null);
        ReadNodesWithDescendantsResponse response = readTool.readNodesWithDescendants(request);

        assertThat(response.getMapIdentifier()).isEqualTo(mapIdentifier.toString());
        assertThat(response.getItems()).hasSize(1);
        ReadNodesWithDescendantsItem item = response.getItems().get(0);
        assertThat(item.getBreadcrumbPath()).isEqualTo("Parent/Focus");
        List<NodeDepthItem> nodes = item.getNodes();
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).getNodeIdentifier()).isEqualTo("ID_focus");
        assertThat(nodes.get(0).getDepth()).isEqualTo(0);
        assertThat(nodes.get(0).getUnformattedText()).isEqualTo("Text: Focus full");
        assertThat(nodes.get(1).getNodeIdentifier()).isEqualTo("ID_child_1");
        assertThat(nodes.get(1).getDepth()).isEqualTo(1);
        assertThat(nodes.get(1).getUnformattedText()).isEqualTo("Child 1");
        assertThat(nodes.get(2).getNodeIdentifier()).isEqualTo("ID_child_2");
        assertThat(nodes.get(2).getDepth()).isEqualTo(1);
        assertThat(nodes.get(2).getUnformattedText()).isEqualTo("Child 2");
        assertThat(nodes.get(0).getQualifiers()).isNull();
    }

    @Test
    public void readNodesWithDescendants_throwsOnDuplicateNodeIdentifiers() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        UUID mapIdentifier = UUID.fromString("2e8d84f0-75b4-4c76-9c25-46863b02cdde");
        MapModel mapModel = mock(MapModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(availableMaps, null,
            nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Arrays.asList("ID_dup", "ID_dup"),
            null,
            0,
            0,
            null);

        assertThatThrownBy(() -> readTool.readNodesWithDescendants(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duplicate node identifiers");
    }

    @Test
    public void readNodesWithDescendants_throwsOnUnknownNodeIdentifiers() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        UUID mapIdentifier = UUID.fromString("1b661e53-7049-4e84-9509-1e7d6e7c9e49");
        MapModel mapModel = mock(MapModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_missing")).thenReturn(null);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(availableMaps, null,
            nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_missing"),
            null,
            0,
            0,
            null);

        assertThatThrownBy(() -> readTool.readNodesWithDescendants(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown node identifiers: ID_missing");
    }

    @Test
    public void readNodesWithDescendants_omitsAdditionalFocusNodesWhenBudgetExceeded() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        UUID mapIdentifier = UUID.fromString("7733f5aa-6722-431e-a0ed-17ef0e67d8e1");
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel secondFocusNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(mapModel.getNodeForID("ID_focus_2")).thenReturn(secondFocusNode);
        when(focusNode.createID()).thenReturn("ID_focus");
        when(secondFocusNode.createID()).thenReturn("ID_focus_2");
        when(focusNode.getChildren()).thenReturn(Collections.emptyList());
        when(secondFocusNode.getChildren()).thenReturn(Collections.emptyList());
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("Focus", null, null), null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(eq(secondFocusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("Focus 2", null, null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController, objectMapper);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Arrays.asList("ID_focus", "ID_focus_2"),
            null,
            0,
            0,
            150);

        ReadNodesWithDescendantsResponse response = readTool.readNodesWithDescendants(request);

        assertThat(response.getItems()).hasSize(1);
        Omissions omissions = response.getOmissions();
        assertThat(omissions).isNotNull();
        assertThat(omissions.getOmittedFocusNodeCount()).isEqualTo(1);
        assertThat(omissions.getOmissionReasons()).containsExactly(OmissionReason.TEXT_BUDGET);
    }

    @Test
    public void readNodesWithDescendants_returnsFullContentWithinFullContentDepth() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("bd2f43b2-f1b4-4a3a-b41b-259d5c3427bf");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.getParentNode()).thenReturn(null);
        when(focusNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(focusNode.createID()).thenReturn("ID_focus");
        when(childNode.createID()).thenReturn("ID_child");
        NodeContentResponse focusFullContent = new NodeContentResponse(
            null, new TextualContent("Focus full", null, null), null, null, null, null, null, null);
        NodeContentResponse childFullContent = new NodeContentResponse(
            null, new TextualContent("Child full", null, null), null, null, null, null, null, null);
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL))).thenReturn(focusFullContent);
        when(nodeContentItemReader.readNodeContent(eq(childNode), any(), eq(NodeContentPreset.FULL))).thenReturn(childFullContent);
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(childNode)).thenReturn("Child");
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController, objectMapper);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            null,
            1,
            0,
            null);

        ReadNodesWithDescendantsResponse response = readTool.readNodesWithDescendants(request);

        ReadNodesWithDescendantsItem item = response.getItems().get(0);
        assertThat(item.getNodes()).hasSize(2);
        assertThat(item.getNodes().get(1).getUnformattedText()).isEqualTo("Text: Child full");
    }

    @Test
    public void readNodesWithDescendants_includesLinkAndCloneMetadataWhenRequested() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        UUID mapIdentifier = UUID.fromString("41c2e9d7-ff86-4d64-b81c-7a6cddc69fe7");
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel targetNode = mock(NodeModel.class);
        NodeModel incomingSource = mock(NodeModel.class);
        NodeModel cloneNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.getChildren()).thenReturn(Collections.emptyList());
        when(focusNode.createID()).thenReturn("ID_focus");
        when(focusNode.getID()).thenReturn("ID_focus");
        when(focusNode.hasID()).thenReturn(true);
        when(focusNode.getMap()).thenReturn(mapModel);
        when(targetNode.createID()).thenReturn("ID_target");
        when(incomingSource.createID()).thenReturn("ID_source");
        when(cloneNode.createID()).thenReturn("ID_clone");
        when(focusNode.isCloneTreeRoot()).thenReturn(true);
        when(focusNode.isCloneTreeNode()).thenReturn(false);
        when(focusNode.allClones()).thenReturn(new TestClones(Arrays.asList(focusNode, cloneNode)));
        NodeContentResponse focusContent = new NodeContentResponse(null,
            new TextualContent("Focus full", null, null), null, null, null, null, null, null);
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(focusContent);
        TextController textController = mock(TextController.class);

        ConnectorModel outgoingConnector = mock(ConnectorModel.class);
        when(outgoingConnector.getSource()).thenReturn(focusNode);
        when(outgoingConnector.getTargetID()).thenReturn("ID_target");
        when(outgoingConnector.getSourceLabel()).thenReturn(Optional.of("out-source"));
        when(outgoingConnector.getMiddleLabel()).thenReturn(Optional.of("out-middle"));
        when(outgoingConnector.getTargetLabel()).thenReturn(Optional.of("out-target"));
        when(outgoingConnector.cloneForSource(focusNode)).thenReturn(outgoingConnector);
        List<NodeLinkModel> outgoingLinks = Arrays.asList(outgoingConnector);
        NodeLinks nodeLinks = new TestNodeLinks(outgoingLinks);
        nodeLinks.setHyperLink(new Hyperlink(URI.create("https://example.com")));
        when(focusNode.getExtension(NodeLinks.class)).thenReturn(nodeLinks);

        ConnectorModel incomingConnector = mock(ConnectorModel.class);
        when(incomingConnector.getSource()).thenReturn(incomingSource);
        when(incomingConnector.getTargetID()).thenReturn("ID_focus");
        when(incomingConnector.getSourceLabel()).thenReturn(Optional.of("in-source"));
        when(incomingConnector.getMiddleLabel()).thenReturn(Optional.of("in-middle"));
        when(incomingConnector.getTargetLabel()).thenReturn(Optional.of("in-target"));
        MapLinks mapLinks = new MapLinks();
        mapLinks.add(incomingConnector);
        when(mapModel.getExtension(MapLinks.class)).thenReturn(mapLinks);

        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController, objectMapper);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            Arrays.asList(ContextSection.HYPERLINK, ContextSection.OUTGOING_CONNECTORS,
                ContextSection.INCOMING_CONNECTORS, ContextSection.CLONE_METADATA),
            0,
            0,
            null);

        ReadNodesWithDescendantsResponse response = readTool.readNodesWithDescendants(request);

        NodeDepthItem node = response.getItems().get(0).getNodes().get(0);
        assertThat(node.getHyperlink()).isEqualTo("https://example.com");
        assertThat(node.getOutgoingConnectors()).hasSize(1);
        ConnectorItem outgoingItem = node.getOutgoingConnectors().get(0);
        assertThat(outgoingItem.getSourceNodeIdentifier()).isEqualTo("ID_focus");
        assertThat(outgoingItem.getTargetNodeIdentifier()).isEqualTo("ID_target");
        assertThat(outgoingItem.getSourceLabel()).isEqualTo("out-source");
        assertThat(outgoingItem.getMiddleLabel()).isEqualTo("out-middle");
        assertThat(outgoingItem.getTargetLabel()).isEqualTo("out-target");
        assertThat(node.getIncomingConnectors()).hasSize(1);
        ConnectorItem incomingItem = node.getIncomingConnectors().get(0);
        assertThat(incomingItem.getSourceNodeIdentifier()).isEqualTo("ID_source");
        assertThat(incomingItem.getTargetNodeIdentifier()).isEqualTo("ID_focus");
        assertThat(incomingItem.getSourceLabel()).isEqualTo("in-source");
        assertThat(incomingItem.getMiddleLabel()).isEqualTo("in-middle");
        assertThat(incomingItem.getTargetLabel()).isEqualTo("in-target");
        CloneMetadata cloneMetadata = node.getCloneMetadata();
        assertThat(cloneMetadata).isNotNull();
        assertThat(cloneMetadata.getCloneNodeIdentifiers()).containsExactly("ID_clone");
        assertThat(cloneMetadata.isCloneTreeRoot()).isTrue();
        assertThat(cloneMetadata.isCloneTreeNode()).isFalse();
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_usesRootDefaultAndRendersIndentedMultilineTree() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("8d0eebc3-6b82-4e7e-bb67-4e6f5f1f6f5d");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn("ID_root");
        when(mapModel.getNodeForID("ID_root")).thenReturn(rootNode);
        when(rootNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(rootNode.createID()).thenReturn("ID_root");
        when(childNode.createID()).thenReturn("ID_child");
        when(nodeContentItemReader.readNodeContent(eq(rootNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("Root", "Details", null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(childNode)).thenReturn("Child");
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            null,
            null,
            null,
            null,
            null);

        ReadNodesWithDescendantsAsPlainTextResponse response =
            readTool.readNodesWithDescendantsAsPlainText(request);

        assertThat(response.getMapIdentifier()).isEqualTo(mapIdentifier.toString());
        assertThat(response.getPlainText()).isEqualTo("- Text: Root\n  Details: Details\n  - Child");
        assertThat(response.getOmissions()).isNull();
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_rendersMultipleFocusBlocksInRequestOrder() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        MapModel mapModel = mock(MapModel.class);
        NodeModel firstFocusNode = mock(NodeModel.class);
        NodeModel secondFocusNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("53f65376-e4b4-4b8d-b4c1-318fd7146b4c");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_first")).thenReturn(firstFocusNode);
        when(mapModel.getNodeForID("ID_second")).thenReturn(secondFocusNode);
        when(firstFocusNode.getChildren()).thenReturn(Collections.emptyList());
        when(secondFocusNode.getChildren()).thenReturn(Collections.emptyList());
        when(firstFocusNode.createID()).thenReturn("ID_first");
        when(secondFocusNode.createID()).thenReturn("ID_second");
        when(nodeContentItemReader.readNodeContent(eq(firstFocusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("First", null, null), null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(eq(secondFocusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("Second", null, null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Arrays.asList("ID_first", "ID_second"),
            null,
            0,
            0,
            null);

        ReadNodesWithDescendantsAsPlainTextResponse response =
            readTool.readNodesWithDescendantsAsPlainText(request);

        assertThat(response.getPlainText()).isEqualTo("- Text: First\n\n- Text: Second");
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_rendersRequestedContextSections() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        UUID mapIdentifier = UUID.fromString("77e4ed9e-2859-4ec7-93a7-e80d9d0e57b0");
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel parentNode = mock(NodeModel.class);
        NodeModel targetNode = mock(NodeModel.class);
        NodeModel incomingSource = mock(NodeModel.class);
        NodeModel cloneNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.getChildren()).thenReturn(Collections.emptyList());
        when(focusNode.getParentNode()).thenReturn(parentNode);
        when(parentNode.getParentNode()).thenReturn(null);
        when(focusNode.createID()).thenReturn("ID_focus");
        when(focusNode.getID()).thenReturn("ID_focus");
        when(focusNode.getText()).thenReturn("Focus");
        when(focusNode.hasID()).thenReturn(true);
        when(focusNode.getMap()).thenReturn(mapModel);
        when(focusNode.containsExtension(SummaryNodeFlag.class)).thenReturn(true);
        when(focusNode.containsExtension(FirstGroupNodeFlag.class)).thenReturn(true);
        when(targetNode.createID()).thenReturn("ID_target");
        when(incomingSource.createID()).thenReturn("ID_source");
        when(cloneNode.createID()).thenReturn("ID_clone");
        when(focusNode.isCloneTreeRoot()).thenReturn(true);
        when(focusNode.isCloneTreeNode()).thenReturn(false);
        when(focusNode.allClones()).thenReturn(new TestClones(Arrays.asList(focusNode, cloneNode)));
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("Focus full", null, null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(focusNode)).thenReturn("Focus");
        when(textController.getShortPlainText(parentNode)).thenReturn("Parent");

        ConnectorModel outgoingConnector = mock(ConnectorModel.class);
        when(outgoingConnector.getSource()).thenReturn(focusNode);
        when(outgoingConnector.getTargetID()).thenReturn("ID_target");
        when(outgoingConnector.getSourceLabel()).thenReturn(Optional.of("out-source"));
        when(outgoingConnector.getMiddleLabel()).thenReturn(Optional.of("out-middle"));
        when(outgoingConnector.getTargetLabel()).thenReturn(Optional.of("out-target"));
        when(outgoingConnector.cloneForSource(focusNode)).thenReturn(outgoingConnector);
        NodeLinks nodeLinks = new TestNodeLinks(Arrays.asList(outgoingConnector));
        nodeLinks.setHyperLink(new Hyperlink(URI.create("https://example.com")));
        when(focusNode.getExtension(NodeLinks.class)).thenReturn(nodeLinks);

        ConnectorModel incomingConnector = mock(ConnectorModel.class);
        when(incomingConnector.getSource()).thenReturn(incomingSource);
        when(incomingConnector.getTargetID()).thenReturn("ID_focus");
        when(incomingConnector.getSourceLabel()).thenReturn(Optional.of("in-source"));
        when(incomingConnector.getMiddleLabel()).thenReturn(Optional.of("in-middle"));
        when(incomingConnector.getTargetLabel()).thenReturn(Optional.of("in-target"));
        MapLinks mapLinks = new MapLinks();
        mapLinks.add(incomingConnector);
        when(mapModel.getExtension(MapLinks.class)).thenReturn(mapLinks);

        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            Arrays.asList(ContextSection.BREADCRUMB_PATH, ContextSection.PARENT_SUMMARY,
                ContextSection.QUALIFIERS, ContextSection.HYPERLINK,
                ContextSection.OUTGOING_CONNECTORS, ContextSection.INCOMING_CONNECTORS,
                ContextSection.CLONE_METADATA),
            0,
            0,
            null);

        ReadNodesWithDescendantsAsPlainTextResponse response =
            readTool.readNodesWithDescendantsAsPlainText(request);

        assertThat(response.getPlainText()).isEqualTo(
            "breadcrumbPath: Parent/Focus\n"
                + "parentSummary:\n"
                + "  - Parent\n"
                + "- Text: Focus full [summary_node, first_group_node]\n"
                + "  hyperlink: https://example.com\n"
                + "  outgoingConnector: ID_focus -> ID_target "
                + "[sourceLabel=out-source, middleLabel=out-middle, targetLabel=out-target]\n"
                + "  incomingConnector: ID_source -> ID_focus "
                + "[sourceLabel=in-source, middleLabel=in-middle, targetLabel=in-target]\n"
                + "  cloneMetadata: cloneTreeRoot=true, cloneTreeNode=false, cloneNodeIdentifiers=ID_clone");
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_countsRenderedCharactersInsteadOfJsonSize() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("b8a3c82a-fbb1-4b8f-8d0d-6b2b38ab6766");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(focusNode.createID()).thenReturn("ID_focus");
        when(childNode.createID()).thenReturn("ID_child");
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("A", null, null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(childNode)).thenReturn("B");
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController, objectMapper);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            null,
            0,
            1,
            15);

        ReadNodesWithDescendantsResponse jsonResponse = readTool.readNodesWithDescendants(request);
        ReadNodesWithDescendantsAsPlainTextResponse plainTextResponse =
            readTool.readNodesWithDescendantsAsPlainText(request);

        assertThat(jsonResponse.getItems()).isEmpty();
        assertThat(jsonResponse.getOmissions()).isNotNull();
        assertThat(jsonResponse.getOmissions().getOmittedFocusNodeCount()).isEqualTo(1);
        assertThat(plainTextResponse.getPlainText()).isEqualTo("- Text: A\n  - B");
        assertThat(plainTextResponse.getOmissions()).isNull();
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_aggregatesChildAndFocusOmissions() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        MapModel mapModel = mock(MapModel.class);
        NodeModel firstFocusNode = mock(NodeModel.class);
        NodeModel secondFocusNode = mock(NodeModel.class);
        NodeModel firstChildNode = mock(NodeModel.class);
        NodeModel secondChildNode = mock(NodeModel.class);
        UUID mapIdentifier = UUID.fromString("7e70e42c-72df-42ea-866b-3dc602da6330");
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_first")).thenReturn(firstFocusNode);
        when(mapModel.getNodeForID("ID_second")).thenReturn(secondFocusNode);
        when(firstFocusNode.getChildren()).thenReturn(Arrays.asList(firstChildNode, secondChildNode));
        when(secondFocusNode.getChildren()).thenReturn(Collections.emptyList());
        when(firstChildNode.getChildren()).thenReturn(Collections.emptyList());
        when(secondChildNode.getChildren()).thenReturn(Collections.emptyList());
        when(firstFocusNode.createID()).thenReturn("ID_first");
        when(secondFocusNode.createID()).thenReturn("ID_second");
        when(firstChildNode.createID()).thenReturn("ID_child_1");
        when(secondChildNode.createID()).thenReturn("ID_child_2");
        when(nodeContentItemReader.readNodeContent(eq(firstFocusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("A", null, null), null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(eq(secondFocusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(new NodeContentResponse(
                null, new TextualContent("D", null, null), null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(firstChildNode)).thenReturn("B");
        when(textController.getShortPlainText(secondChildNode)).thenReturn("C");
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            mapIdentifier.toString(),
            Arrays.asList("ID_first", "ID_second"),
            null,
            0,
            1,
            15);

        ReadNodesWithDescendantsAsPlainTextResponse response =
            readTool.readNodesWithDescendantsAsPlainText(request);

        assertThat(response.getPlainText()).isEqualTo("- Text: A\n  - B");
        assertThat(response.getOmissions()).isNotNull();
        assertThat(response.getOmissions().getOmittedChildCount()).isEqualTo(1);
        assertThat(response.getOmissions().getOmittedFocusNodeCount()).isEqualTo(1);
        assertThat(response.getOmissions().getOmissionReasons()).containsExactly(OmissionReason.TEXT_BUDGET);
    }

    @Test
    public void fetchNodesForEditing_returnsEditableContentOnly() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        UUID mapIdentifier = UUID.fromString("bb7f2976-43e0-4bf7-9cc1-77a0949f4f30");
        MapModel mapModel = mock(MapModel.class);
        NodeModel focusNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getNodeForID("ID_focus")).thenReturn(focusNode);
        when(focusNode.createID()).thenReturn("ID_focus");
        EditableContent editableContent = mock(EditableContent.class);
        NodeContentResponse content = new NodeContentResponse(null, null, null, null, null, null, null, editableContent);
        when(nodeContentItemReader.readNodeContent(eq(focusNode), any(), eq(NodeContentPreset.FULL)))
            .thenReturn(content);
        NodeContentItem item = new NodeContentItem("ID_focus", content, null, null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(focusNode, content, true, false)).thenReturn(item);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(availableMaps, null,
            nodeContentItemReader, textController);
        FetchNodesForEditingRequest request = new FetchNodesForEditingRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            Collections.singletonList(EditableContentField.TEXT));

        FetchNodesForEditingResponse response = readTool.fetchNodesForEditing(request);

        assertThat(response.getMapIdentifier()).isEqualTo(mapIdentifier.toString());
        assertThat(response.getItems()).containsExactly(item);
    }

    @Test
    public void fetchNodesForEditing_throwsOnMissingEditableContentFields() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(availableMaps, null,
            nodeContentItemReader, textController);
        UUID mapIdentifier = UUID.fromString("58d1888f-151a-4b12-ac54-7f6ff94c876f");
        FetchNodesForEditingRequest request = new FetchNodesForEditingRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            null);

        assertThatThrownBy(() -> readTool.fetchNodesForEditing(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing editableContentFields");
    }

    @Test
    public void fetchNodesForEditing_throwsOnEmptyEditableContentFields() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(availableMaps, null,
            nodeContentItemReader, textController);
        UUID mapIdentifier = UUID.fromString("ebf35466-149f-472f-95f8-13b07bd634cb");
        FetchNodesForEditingRequest request = new FetchNodesForEditingRequest(
            mapIdentifier.toString(),
            Collections.singletonList("ID_focus"),
            Collections.<EditableContentField>emptyList());

        assertThatThrownBy(() -> readTool.fetchNodesForEditing(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing editableContentFields");
    }

    private static class TestClones implements Clones {
        private final List<NodeModel> nodes;

        private TestClones(List<NodeModel> nodes) {
            this.nodes = nodes;
        }

        @Override
        public int size() {
            return nodes.size();
        }

        @Override
        public void attach() {
        }

        @Override
        public void detach(NodeModel nodeModel) {
        }

        @Override
        public Clones add(NodeModel clone) {
            return this;
        }

        @Override
        public Collection<NodeModel> toCollection() {
            return nodes;
        }

        @Override
        public boolean contains(NodeModel node) {
            return nodes.contains(node);
        }

        @Override
        public NodeModel head() {
            return nodes.isEmpty() ? null : nodes.get(0);
        }

        @Override
        public NodeModel.CloneType getCloneType() {
            return NodeModel.CloneType.CONTENT;
        }

        @Override
        public Iterator<NodeModel> iterator() {
            return nodes.iterator();
        }
    }
}

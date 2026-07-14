package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.IconsContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.NodeContentValueMatcher;
import org.freeplane.plugin.ai.tools.content.TextualContentRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SearchNodesToolTest {
    @Test
    public void searchNodes_returnsMatchesWithBreadcrumbPath() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        UUID mapIdentifier = UUID.fromString("4c5f3c3c-bd0e-4b3e-85ab-1c5f0b2f2a13");
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        NodeModel otherNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getChildren()).thenReturn(Arrays.asList(childNode, otherNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(otherNode.getChildren()).thenReturn(Collections.emptyList());
        when(childNode.getParentNode()).thenReturn(rootNode);
        when(rootNode.getParentNode()).thenReturn(null);
        when(rootNode.createID()).thenReturn("ID_root");
        when(childNode.createID()).thenReturn("ID_child");
        when(otherNode.createID()).thenReturn("ID_other");
        NodeContentResponse rootBriefContent = new NodeContentResponse("Root", null, null, null, null, null, null, null);
        NodeContentResponse childBriefContent = new NodeContentResponse("Alpha", null, null, null, null, null, null, null);
        when(nodeContentItemReader.matchesNodeContent(eq(childNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class)))
            .thenReturn(true);
        when(nodeContentItemReader.matchesNodeContent(eq(otherNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class)))
            .thenReturn(false);
        when(nodeContentItemReader.readNodeContent(rootNode, null, NodeContentPreset.BRIEF)).thenReturn(rootBriefContent);
        when(nodeContentItemReader.readNodeContent(childNode, null, NodeContentPreset.BRIEF)).thenReturn(childBriefContent);
        TextController textController = mock(TextController.class);
        when(textController.getShortPlainText(childNode, 20, "")).thenReturn("Alpha preview");
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController),
            objectMapper);
        SearchNodesRequest request = new SearchNodesRequest(
            mapIdentifier.toString(),
            "alp",
            null,
            null,
            SearchMatchingMode.CONTAINS,
            SearchCaseSensitivity.CASE_INSENSITIVE,
            Collections.singletonList(SearchResultSection.BREADCRUMB_PATH),
            0,
            200,
            1000);

        SearchNodesResponse response = uut.searchNodes(request);

        assertThat(response.getResults()).hasSize(1);
        SearchResultItem result = response.getResults().get(0);
        assertThat(result.getNodeIdentifier()).isEqualTo("ID_child");
        assertThat(result.getBriefText()).isEqualTo("Alpha");
        assertThat(result.getBreadcrumbPath()).isEqualTo("Root/Alpha");
        assertThat(response.getResultPreviewTexts()).containsExactly("Alpha preview");
    }

    @Test
    public void searchNodes_throwsOnDuplicateSubtreeRoots() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        UUID mapIdentifier = UUID.fromString("f605a3ed-c8a9-4f9a-a001-d2f0c2f92d21");
        MapModel mapModel = mock(MapModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        TextController textController = mock(TextController.class);
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController));
        SearchNodesRequest request = new SearchNodesRequest(
            mapIdentifier.toString(),
            "alpha",
            Arrays.asList("ID_root", "ID_root"),
            null,
            SearchMatchingMode.CONTAINS,
            SearchCaseSensitivity.CASE_INSENSITIVE,
            null,
            0,
            200,
            null);

        assertThatThrownBy(() -> uut.searchNodes(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duplicate subtree root node identifiers");
    }

    @Test
    public void searchNodes_respectsCaseSensitivity() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        UUID mapIdentifier = UUID.fromString("8e7a4a4a-bad1-4d1d-bcf8-7f0b2d899b2c");
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(nodeContentItemReader.matchesNodeContent(eq(childNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class)))
            .thenAnswer(invocation -> {
                NodeContentValueMatcher matcher = invocation.getArgument(2);
                return matcher.matchesValue("Alpha");
            });
        TextController textController = mock(TextController.class);
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController),
            objectMapper);
        SearchNodesRequest request = new SearchNodesRequest(
            mapIdentifier.toString(),
            "alp",
            null,
            null,
            SearchMatchingMode.CONTAINS,
            SearchCaseSensitivity.CASE_SENSITIVE,
            null,
            0,
            200,
            1000);

        SearchNodesResponse response = uut.searchNodes(request);

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    public void searchNodes_matchesIconDescriptionsWhenRequested() throws Exception {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[100]);
        UUID mapIdentifier = UUID.fromString("b97e91ba-4b3b-49d9-9e7b-2d5ef542a1ab");
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        when(availableMaps.findMapModel(eq(mapIdentifier), any())).thenReturn(mapModel);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(rootNode.createID()).thenReturn("ID_root");
        when(childNode.createID()).thenReturn("ID_child");
        when(nodeContentItemReader.matchesNodeContent(eq(childNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class)))
            .thenAnswer(invocation -> {
                NodeContentValueMatcher matcher = invocation.getArgument(2);
                return matcher.matchesValue("Priority 3");
            });
        when(nodeContentItemReader.readNodeContent(rootNode, null, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Root", null, null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(childNode, null, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Alpha", null, null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController),
            objectMapper);
        SearchNodesRequest request = new SearchNodesRequest(
            mapIdentifier.toString(),
            "priority",
            null,
            new NodeContentRequest(
                new TextualContentRequest(true, false, false),
                null,
                null,
                new IconsContentRequest(true),
                null),
            SearchMatchingMode.CONTAINS,
            SearchCaseSensitivity.CASE_INSENSITIVE,
            Collections.singletonList(SearchResultSection.BREADCRUMB_PATH),
            0,
            200,
            1000);

        SearchNodesResponse response = uut.searchNodes(request);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getNodeIdentifier()).isEqualTo("ID_child");
    }
}

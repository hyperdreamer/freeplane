package org.freeplane.plugin.ai.tools.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.maps.MapModelProvider;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentRequest;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.NodeContentValueMatcher;
import org.freeplane.plugin.ai.tools.search.SearchCaseSensitivity;
import org.freeplane.plugin.ai.tools.search.SearchMatchingMode;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.freeplane.plugin.ai.tools.search.SearchNodesResponse;
import org.freeplane.plugin.ai.tools.search.SearchNodesTool;
import org.junit.Test;

public class GetApiDocumentationToolTest {
    @Test
    public void getApiDocumentation_returnsResponseAndRegistersMapIdentifier() {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn(null);
        when(rootNode.createID()).thenReturn("ID_root");
        ApiDocumentationMapLoader mapLoader = mock(ApiDocumentationMapLoader.class);
        when(mapLoader.loadInstalledApiMap()).thenReturn(
            new ApiDocumentationMapLoader.LoadedApiDocumentationMap(new File("/tmp/freeplane-api.mm"), mapModel));
        ApiDocumentationStructureSummaryReader summaryReader = mock(ApiDocumentationStructureSummaryReader.class);
        when(summaryReader.readStructureSummary(mapModel, new File("/tmp/freeplane-api.mm")))
            .thenReturn("How to use this map\n  Use API groups.\n  Use Packages.");
        GetApiDocumentationTool uut = new GetApiDocumentationTool(availableMaps, mapLoader, summaryReader);

        GetApiDocumentationResponse response = uut.getApiDocumentation();

        assertThat(response.getRootNodeIdentifier()).isEqualTo("ID_root");
        assertThat(response.getStructureSummary()).isEqualTo("How to use this map\n  Use API groups.\n  Use Packages.");
        UUID mapIdentifier = UUID.fromString(response.getMapIdentifier());
        assertThat(availableMaps.findMapModel(mapIdentifier)).isSameAs(mapModel);
    }

    @Test
    public void returnedMapIdentifier_isUsableBySearchNodesTool() throws Exception {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel childNode = mock(NodeModel.class);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn("ID_root");
        when(rootNode.getChildren()).thenReturn(Collections.singletonList(childNode));
        when(rootNode.getParentNode()).thenReturn(null);
        when(childNode.getChildren()).thenReturn(Collections.emptyList());
        when(childNode.getParentNode()).thenReturn(rootNode);
        when(childNode.createID()).thenReturn("ID_child");
        ApiDocumentationMapLoader mapLoader = mock(ApiDocumentationMapLoader.class);
        when(mapLoader.loadInstalledApiMap()).thenReturn(
            new ApiDocumentationMapLoader.LoadedApiDocumentationMap(new File("/tmp/freeplane-api.mm"), mapModel));
        ApiDocumentationStructureSummaryReader summaryReader = mock(ApiDocumentationStructureSummaryReader.class);
        when(summaryReader.readStructureSummary(mapModel, new File("/tmp/freeplane-api.mm")))
            .thenReturn("How to use this map\n  Use API groups.\n  Use Packages.");
        GetApiDocumentationTool documentationTool = new GetApiDocumentationTool(availableMaps, mapLoader, summaryReader);
        GetApiDocumentationResponse response = documentationTool.getApiDocumentation();

        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        when(nodeContentItemReader.matchesNodeContent(eq(childNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class))).thenReturn(true);
        when(nodeContentItemReader.readNodeContent(eq(rootNode), isNull(), eq(NodeContentPreset.BRIEF)))
            .thenReturn(new NodeContentResponse("Freeplane scripting API", null, null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(eq(childNode), isNull(), eq(NodeContentPreset.BRIEF)))
            .thenReturn(new NodeContentResponse("Purpose", null, null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        SearchNodesTool searchNodesTool = new SearchNodesTool(availableMaps, null, nodeContentItemReader, textController);

        SearchNodesResponse searchResponse = searchNodesTool.searchNodes(new SearchNodesRequest(
            response.getMapIdentifier(),
            "Purpose",
            null,
            null,
            SearchMatchingMode.EQUALS,
            SearchCaseSensitivity.CASE_SENSITIVE,
            Arrays.asList(org.freeplane.plugin.ai.tools.search.SearchResultSection.BREADCRUMB_PATH),
            0,
            20,
            4096));

        assertThat(searchResponse.getResults()).hasSize(1);
        assertThat(searchResponse.getResults().get(0).getNodeIdentifier()).isNotNull();
        assertThat(searchResponse.getResults().get(0).getBriefText()).isEqualTo("Purpose");
    }

    @Test
    public void buildToolCallSummary_reportsIdentifiers() {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        GetApiDocumentationTool uut = new GetApiDocumentationTool(
            availableMaps,
            mock(ApiDocumentationMapLoader.class),
            mock(ApiDocumentationStructureSummaryReader.class));

        assertThat(uut.buildToolCallSummary(new GetApiDocumentationResponse(
            "map-identifier", "root-node", "How to use this map" )).getSummaryText())
            .isEqualTo("getApiDocumentation: mapIdentifier=\"map-identifier\", rootNodeIdentifier=\"root-node\"");
    }

    private static final class EmptyMapModelProvider implements MapModelProvider {
        @Override
        public MapModel getCurrentMapModel() {
            return null;
        }

        @Override
        public java.util.List<MapModel> getOpenMapModels() {
            return Collections.emptyList();
        }

        @Override
        public NodeModel getCurrentSelectedNodeModel() {
            return null;
        }
    }
}

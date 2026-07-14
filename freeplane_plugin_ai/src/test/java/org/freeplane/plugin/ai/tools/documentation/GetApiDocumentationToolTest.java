package org.freeplane.plugin.ai.tools.documentation;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetApiDocumentationToolTest {
    @Test
    public void getApiDocumentation_returnsResponseAndRegistersMapIdentifier() {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        MapModel mapModel = mock(MapModel.class);
        NodeModel rootNode = mock(NodeModel.class);
        NodeModel packagesNode = mock(NodeModel.class);
        NodeModel apiGroupsNode = mock(NodeModel.class);
        when(mapModel.getRootNode()).thenReturn(rootNode);
        when(rootNode.getID()).thenReturn(null);
        when(rootNode.createID()).thenReturn("ID_root");
        when(packagesNode.getID()).thenReturn("ID_packages");
        when(apiGroupsNode.getID()).thenReturn(null);
        when(apiGroupsNode.createID()).thenReturn("ID_api_groups");
        File mapFile = new File("/tmp/freeplane-api.mm");
        ApiDocumentationMapLoader mapLoader = mock(ApiDocumentationMapLoader.class);
        when(mapLoader.loadInstalledApiMap()).thenReturn(
            new ApiDocumentationMapLoader.LoadedApiDocumentationMap(mapFile, mapModel));
        ApiDocumentationStructureSummaryReader summaryReader = mock(ApiDocumentationStructureSummaryReader.class);
        when(summaryReader.findRequiredTopLevelSection(mapModel, mapFile, "Packages")).thenReturn(packagesNode);
        when(summaryReader.findRequiredTopLevelSection(mapModel, mapFile, "API groups")).thenReturn(apiGroupsNode);
        when(summaryReader.readStructureSummary(mapModel, mapFile))
            .thenReturn("How to use this map\n  Search first.");
        GetApiDocumentationTool uut = new GetApiDocumentationTool(availableMaps, mapLoader, summaryReader);

        GetApiDocumentationResponse response = uut.getApiDocumentation();

        assertThat(response.getRootNodeIdentifier()).isEqualTo("ID_root");
        assertThat(response.getPackagesRootNodeIdentifier()).isEqualTo("ID_packages");
        assertThat(response.getApiGroupsRootNodeIdentifier()).isEqualTo("ID_api_groups");
        assertThat(response.getStructureSummary()).isEqualTo("How to use this map\n  Search first.");
        assertThat(response.getMapIdentifier())
            .isEqualTo(AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString());
        UUID mapIdentifier = UUID.fromString(response.getMapIdentifier());
        assertThat(availableMaps.findMapModel(mapIdentifier)).isSameAs(mapModel);
    }

    @Test
    public void reservedMapIdentifier_isUsableBySearchNodesToolWithoutPriorDiscovery() throws Exception {
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
        when(mapLoader.loadInstalledApiMapModel()).thenReturn(mapModel);
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider(), mapLoader);

        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        when(nodeContentItemReader.matchesNodeContent(eq(childNode), any(NodeContentRequest.class),
            any(NodeContentValueMatcher.class))).thenReturn(true);
        when(nodeContentItemReader.readNodeContent(eq(rootNode), isNull(), eq(NodeContentPreset.BRIEF)))
            .thenReturn(new NodeContentResponse("Freeplane scripting API", null, null, null, null, null, null, null));
        when(nodeContentItemReader.readNodeContent(eq(childNode), isNull(), eq(NodeContentPreset.BRIEF)))
            .thenReturn(new NodeContentResponse("Purpose", null, null, null, null, null, null, null));
        TextController textController = mock(TextController.class);
        SearchNodesTool searchNodesTool = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController));

        SearchNodesResponse searchResponse = searchNodesTool.searchNodes(new SearchNodesRequest(
            AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString(),
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
        verify(mapLoader).loadInstalledApiMapModel();
    }

    @Test
    public void formatToolResponse_returnsStructureSummaryAndIdentifiersJson() {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        GetApiDocumentationTool uut = new GetApiDocumentationTool(
            availableMaps,
            mock(ApiDocumentationMapLoader.class),
            mock(ApiDocumentationStructureSummaryReader.class));

        String formatted = uut.formatToolResponse(new GetApiDocumentationResponse(
            "map-identifier", "root-node", "packages-node", "api-groups-node", "How to use this map\n  Search first."));

        assertThat(formatted).isEqualTo(
            "How to use this map\n  Search first.\n\n"
                + "{\"mapIdentifier\":\"map-identifier\",\"rootNodeIdentifier\":\"root-node\","
                + "\"packagesRootNodeIdentifier\":\"packages-node\",\"apiGroupsRootNodeIdentifier\":\"api-groups-node\"}");
    }

    @Test
    public void buildToolCallSummary_reportsIdentifiers() {
        AvailableMaps availableMaps = new AvailableMaps(new EmptyMapModelProvider());
        GetApiDocumentationTool uut = new GetApiDocumentationTool(
            availableMaps,
            mock(ApiDocumentationMapLoader.class),
            mock(ApiDocumentationStructureSummaryReader.class));

        assertThat(uut.buildToolCallSummary(new GetApiDocumentationResponse(
            "map-identifier", "root-node", "packages-node", "api-groups-node", "How to use this map")).getSummaryText())
            .isEqualTo("getApiDocumentation: mapIdentifier=\"map-identifier\", rootNodeIdentifier=\"root-node\", "
                + "packagesRootNodeIdentifier=\"packages-node\", apiGroupsRootNodeIdentifier=\"api-groups-node\"");
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

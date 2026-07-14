package org.freeplane.plugin.ai.tools.search;

import java.util.Arrays;
import java.util.List;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SearchNodesToolSummaryTest {
    @Test
    public void buildToolCallSummary_usesDefaultsAndResultCount() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController));
        SearchNodesRequest request = new SearchNodesRequest(
            "map-identifier",
            "alpha\nbeta",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
        List<SearchResultItem> results = Arrays.asList(
            new SearchResultItem("node-identifier-1", "Alpha", null),
            new SearchResultItem("node-identifier-2", "Beta", null));
        SearchNodesResponse response = new SearchNodesResponse(
            "map-identifier", results, null, Arrays.asList("Alpha", "Beta"));

        ToolCallSummary summary = uut.buildToolCallSummary(request, response);

        assertThat(summary.getToolName()).isEqualTo("searchNodes");
        assertThat(summary.hasError()).isFalse();
        assertThat(summary.getSummaryText()).isEqualTo(
            "searchNodes: query=\"alpha beta\", results=2, resultTexts=\"Alpha; Beta\"");
    }

    @Test
    public void buildToolCallErrorSummary_sanitizesMessage() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        SearchNodesTool uut = new SearchNodesTool(availableMaps, null, nodeContentItemReader, new NodeTextPreviewFormatter(textController));

        ToolCallSummary summary = uut.buildToolCallErrorSummary(null, new IllegalArgumentException("Bad\nquery"));

        assertThat(summary.getToolName()).isEqualTo("searchNodes");
        assertThat(summary.hasError()).isTrue();
        assertThat(summary.getSummaryText()).isEqualTo("searchNodes error: Bad query");
    }
}

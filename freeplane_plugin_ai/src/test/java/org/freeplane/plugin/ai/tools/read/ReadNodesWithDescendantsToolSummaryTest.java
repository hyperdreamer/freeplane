package org.freeplane.plugin.ai.tools.read;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ReadNodesWithDescendantsToolSummaryTest {
    @Test
    public void buildToolCallSummary_usesDefaultDepthsAndSections() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            "map-identifier",
            null,
            null,
            null,
            null,
            null);
        List<ReadNodesWithDescendantsItem> items = Arrays.asList(
            new ReadNodesWithDescendantsItem(Collections.emptyList(), null, null, null),
            new ReadNodesWithDescendantsItem(Collections.emptyList(), null, null, null));
        ReadNodesWithDescendantsResponse response = new ReadNodesWithDescendantsResponse(
            "map-identifier", items, null, Arrays.asList("Alpha", "Beta"));

        ToolCallSummary summary = readTool.buildToolCallSummary(request, response);

        assertThat(summary.getToolName()).isEqualTo("readNodesWithDescendants");
        assertThat(summary.hasError()).isFalse();
        assertThat(summary.getSummaryText()).isEqualTo(
            "readNodesWithDescendants: items=2, focusNodeTexts=\"Alpha; Beta\"");
    }

    @Test
    public void buildToolCallSummary_includesExplicitSectionsAndDepths() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            "map-identifier",
            null,
            Arrays.asList(ContextSection.PARENT_SUMMARY, ContextSection.QUALIFIERS),
            2,
            3,
            null);
        ReadNodesWithDescendantsResponse response = new ReadNodesWithDescendantsResponse(
            "map-identifier", Collections.emptyList(), null);

        ToolCallSummary summary = readTool.buildToolCallSummary(request, response);

        assertThat(summary.getSummaryText()).isEqualTo(
            "readNodesWithDescendants: items=0, fullContentDepth=2, summaryDepth=3, "
                + "sections=PARENT_SUMMARY,QUALIFIERS");
    }

    @Test
    public void buildToolCallErrorSummary_sanitizesMessage() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextController textController = mock(TextController.class);
        ReadNodesWithDescendantsTool readTool = new ReadNodesWithDescendantsTool(
            availableMaps, null, nodeContentItemReader, textController);

        ToolCallSummary summary = readTool.buildToolCallErrorSummary(null, new IllegalStateException("Missing\nmap"));

        assertThat(summary.getToolName()).isEqualTo("readNodesWithDescendants");
        assertThat(summary.hasError()).isTrue();
        assertThat(summary.getSummaryText()).isEqualTo("readNodesWithDescendants error: Missing map");
    }
}

package org.freeplane.plugin.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.selection.SelectedNodeSummary;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiPromptRequestComposerTest {

    @Test
    public void compose_prependsSelectedIdentifiersJsonAndPromptTextForEditing() {
        SelectionIdentifiersResponse response = new SelectionIdentifiersResponse(
            "map-1",
            "node-1",
            "root-1",
            Arrays.asList(
                new SelectedNodeSummary("node-1", "Alpha"),
                new SelectedNodeSummary("node-2", "Beta")),
            2,
            1);
        AiPromptRequestComposer uut = new AiPromptRequestComposer(() -> response, new ObjectMapper());

        String composed = uut.compose(
            new AiPrompt("Rewrite", "Rewrite the selected nodes.", false),
            ToolAvailabilityLevel.EDITING);

        assertThat(composed).isEqualTo(
            "Selected map and node identifiers:\n"
                + "{\"mapIdentifier\":\"map-1\",\"nodeIdentifier\":\"node-1\","
                + "\"rootNodeIdentifier\":\"root-1\",\"selectedNodes\":[{\"nodeIdentifier\":\"node-1\",\"shortText\":\"Alpha\"},"
                + "{\"nodeIdentifier\":\"node-2\",\"shortText\":\"Beta\"}],\"selectedNodeCount\":2,\"selectedUniqueSubtreeCount\":1}"
                + "\n\nRewrite the selected nodes.");
    }

    @Test
    public void compose_prependsSelectedIdentifiersJsonAndPromptTextForReading() {
        SelectionIdentifiersResponse response = new SelectionIdentifiersResponse(
            "map-1",
            "node-1",
            "root-1",
            Arrays.asList(
                new SelectedNodeSummary("node-1", "Alpha"),
                new SelectedNodeSummary("node-2", "Beta")),
            2,
            1);
        AiPromptRequestComposer uut = new AiPromptRequestComposer(() -> response, new ObjectMapper());

        String composed = uut.compose(
            new AiPrompt("Rewrite", "Rewrite the selected nodes.", false),
            ToolAvailabilityLevel.READING);

        assertThat(composed).isEqualTo(
            "Selected map and node identifiers:\n"
                + "{\"mapIdentifier\":\"map-1\",\"nodeIdentifier\":\"node-1\","
                + "\"rootNodeIdentifier\":\"root-1\",\"selectedNodes\":[{\"nodeIdentifier\":\"node-1\",\"shortText\":\"Alpha\"},"
                + "{\"nodeIdentifier\":\"node-2\",\"shortText\":\"Beta\"}],\"selectedNodeCount\":2,\"selectedUniqueSubtreeCount\":1}"
                + "\n\nRewrite the selected nodes.");
    }

    @Test
    public void compose_usesSelectionOverrideForReadingAndEditingPromptContext() {
        SelectionIdentifiersResponse response = new SelectionIdentifiersResponse(
            "map-current",
            "node-current",
            "root-current",
            Arrays.asList(new SelectedNodeSummary("node-current", "Current")),
            1,
            1);
        SelectionIdentifiersResponse override = new SelectionIdentifiersResponse(
            "map-override",
            "node-override",
            "root-override",
            Arrays.asList(new SelectedNodeSummary("node-override", "Override")),
            1,
            1);
        AiPromptRequestComposer uut = new AiPromptRequestComposer(() -> response, new ObjectMapper());

        String composed = uut.compose(
            "Rewrite the selected nodes.",
            ToolAvailabilityLevel.READING,
            override);

        assertThat(composed).contains("\"mapIdentifier\":\"map-override\"")
            .doesNotContain("map-current");
    }

    @Test
    public void compose_returnsOnlyPromptTextWhenToolsAreDisabled() {
        SelectionIdentifiersResponse response = new SelectionIdentifiersResponse(
            "map-1",
            "node-1",
            "root-1",
            Arrays.asList(
                new SelectedNodeSummary("node-1", "Alpha"),
                new SelectedNodeSummary("node-2", "Beta")),
            2,
            1);
        AiPromptRequestComposer uut = new AiPromptRequestComposer(() -> response, new ObjectMapper());

        String composed = uut.compose(
            new AiPrompt("Rewrite", "Rewrite the selected nodes.", false),
            ToolAvailabilityLevel.DISABLED);

        assertThat(composed).isEqualTo("Rewrite the selected nodes.");
    }
}

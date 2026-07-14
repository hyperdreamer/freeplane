package org.freeplane.plugin.ai.tools.content;

import java.util.Collections;
import java.util.List;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModifiedNodeSummaryBuilderTest {
    @Test
    public void buildSummaries_usesCompactModifiedNodeSummaryText() {
        TextController textController = mock(TextController.class);
        NodeModel node = mock(NodeModel.class);
        when(node.createID()).thenReturn("ID_node");
        when(textController.getShortPlainText(node, 20, " ...")).thenReturn("Modified node");
        ModifiedNodeSummaryBuilder uut = new ModifiedNodeSummaryBuilder(new NodeTextPreviewFormatter(textController));

        List<ModifiedNodeSummary> summaries = uut.buildSummaries(Collections.singletonList(node), false);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getNodeIdentifier()).isEqualTo("ID_node");
        assertThat(summaries.get(0).getShortText()).isEqualTo("Modified node");
    }
}

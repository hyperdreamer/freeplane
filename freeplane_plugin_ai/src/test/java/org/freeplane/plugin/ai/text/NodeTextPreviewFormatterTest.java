package org.freeplane.plugin.ai.text;

import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NodeTextPreviewFormatterTest {
    @Test
    public void shortText_usesFortyCharactersAndContinuationMark() {
        TextController textController = mock(TextController.class);
        NodeModel node = mock(NodeModel.class);
        when(textController.getShortPlainText(node, 40, " ...")).thenReturn("Short text");
        NodeTextPreviewFormatter uut = new NodeTextPreviewFormatter(textController);

        String result = uut.shortText(node);

        assertThat(result).isEqualTo("Short text");
        verify(textController).getShortPlainText(node, 40, " ...");
    }

    @Test
    public void modifiedNodeSummaryText_usesTwentyCharactersAndContinuationMark() {
        TextController textController = mock(TextController.class);
        NodeModel node = mock(NodeModel.class);
        when(textController.getShortPlainText(node, 20, " ...")).thenReturn("Modified node");
        NodeTextPreviewFormatter uut = new NodeTextPreviewFormatter(textController);

        String result = uut.modifiedNodeSummaryText(node);

        assertThat(result).isEqualTo("Modified node");
        verify(textController).getShortPlainText(node, 20, " ...");
    }

    @Test
    public void toolCallPreviewText_usesTwentyCharactersWithoutContinuationMark() {
        TextController textController = mock(TextController.class);
        NodeModel node = mock(NodeModel.class);
        when(textController.getShortPlainText(node, 20, "")).thenReturn("Tool preview");
        NodeTextPreviewFormatter uut = new NodeTextPreviewFormatter(textController);

        String result = uut.toolCallPreviewText(node);

        assertThat(result).isEqualTo("Tool preview");
        verify(textController).getShortPlainText(node, 20, "");
    }
}

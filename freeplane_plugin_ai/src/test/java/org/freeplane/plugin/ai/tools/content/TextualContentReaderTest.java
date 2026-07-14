package org.freeplane.plugin.ai.tools.content;

import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TextualContentReaderTest {
    @Test
    public void readBriefText_usesShortPlainText() {
        TextController textController = mock(TextController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        when(textController.getShortPlainText(nodeModel, 40, " ...")).thenReturn("Short text");
        TextualContentReader uut = new TextualContentReader(textController, new NodeTextPreviewFormatter(textController));

        String briefText = uut.readBriefText(nodeModel);

        assertThat(briefText).isEqualTo("Short text");
        verify(textController).getShortPlainText(nodeModel, 40, " ...");
    }

    @Test
    public void readTextualContent_usesTransformedTextForFullPreset() {
        TextController textController = mock(TextController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        Object userObject = "Raw text";
        when(nodeModel.getUserObject()).thenReturn(userObject);
        when(textController.getTransformedTextForClipboard(nodeModel, nodeModel, userObject))
            .thenReturn("Transformed text");
        TextualContentReader uut = new TextualContentReader(textController, new NodeTextPreviewFormatter(textController));

        TextualContent content = uut.readTextualContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getText()).isEqualTo("Transformed text");
        assertThat(content.getDetails()).isNull();
        assertThat(content.getNote()).isNull();
        verify(textController).getTransformedTextForClipboard(nodeModel, nodeModel, userObject);
    }

    @Test
    public void readTextualContent_usesTransformedTextForDetailsAndNote() {
        TextController textController = mock(TextController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        DetailModel detailModel = new DetailModel(false);
        detailModel.setText("Raw details");
        NoteModel noteModel = new NoteModel();
        noteModel.setText("Raw note");
        when(nodeModel.getExtension(DetailModel.class)).thenReturn(detailModel);
        when(nodeModel.getExtension(NoteModel.class)).thenReturn(noteModel);
        when(textController.getTransformedTextForClipboard(nodeModel, detailModel, "Raw details"))
            .thenReturn("Details");
        when(textController.getTransformedTextForClipboard(nodeModel, noteModel, "Raw note"))
            .thenReturn("Note");
        TextualContentReader uut = new TextualContentReader(textController, new NodeTextPreviewFormatter(textController));

        TextualContent content = uut.readTextualContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getText()).isNull();
        assertThat(content.getDetails()).isEqualTo("Details");
        assertThat(content.getNote()).isEqualTo("Note");
        verify(textController).getTransformedTextForClipboard(nodeModel, detailModel, "Raw details");
        verify(textController).getTransformedTextForClipboard(nodeModel, noteModel, "Raw note");
    }
}

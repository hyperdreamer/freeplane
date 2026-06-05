package org.freeplane.plugin.ai.tools.edit;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.content.NodeContentWriteRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TextualContentEditorTest {
    @Test
    public void setInitialContent_setsTextDetailsAndNote() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeContentWriteRequest content = new NodeContentWriteRequest(
            "text",
            null,
            "details",
            null,
            "note",
            null,
            null,
            null,
            null,
            null);
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class));

        uut.setInitialContent(nodeModel, content);

        assertThat(nodeModel.getText()).isEqualTo("text");
        assertThat(HtmlUtils.htmlToPlain(DetailModel.getDetailText(nodeModel))).isEqualTo("details");
        assertThat(HtmlUtils.htmlToPlain(NoteModel.getNoteText(nodeModel))).isEqualTo("note");
    }

    @Test
    public void editExistingTextualContent_updatesNodeTextThroughController() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextualContentEditor uut = new TextualContentEditor(textContentWriteController, noteContentWriteController);
        NodeModel nodeModel = mock(NodeModel.class);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT, "value",
            textController);

        verify(textContentWriteController).setNodeText(nodeModel, "value");
    }

    @Test
    public void editExistingTextualContent_throwsOnFormulaContentType() {
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class));
        NodeModel nodeModel = mock(NodeModel.class);
        TextController textController = mock(TextController.class);

        assertThatThrownBy(() -> uut.editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.FORMULA, "value", textController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Formula content edits are not allowed.");
    }

    @Test
    public void editExistingTextualContent_allowsDetailsEditWhenContentTypeMatches() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextualContentEditor uut = new TextualContentEditor(textContentWriteController, noteContentWriteController);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.editExistingTextualContent(nodeModel, EditedElement.DETAILS, ContentType.PLAIN_TEXT,
            "value", textController);

        verify(textContentWriteController).setDetails(nodeModel, "value");
    }

    @Test
    public void editExistingTextualContent_rejectsHtmlForMarkdownText() {
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class));
        NodeModel nodeModel = mock(NodeModel.class);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        when(textController.getNodeFormat(nodeModel)).thenReturn("markdown");

        assertThatThrownBy(() -> uut.editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.MARKDOWN, "<html><body>value</body></html>", textController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Markdown content does not allow html; use markdown syntax.");
    }

    @Test
    public void editExistingTextualContent_allowsTextHtmlUpdateWhenContentTypeIsPlain() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextualContentEditor uut = new TextualContentEditor(textContentWriteController, noteContentWriteController);
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("plain");
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT,
            "<html><body>value</body></html>", textController);

        verify(textContentWriteController).setNodeText(nodeModel, "<html><body>value</body></html>");
    }

    @Test
    public void editExistingTextualContent_allowsLatexTextEditsWithPrefix() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextualContentEditor uut = new TextualContentEditor(textContentWriteController, noteContentWriteController);
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("\\latex x+1");
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.LATEX, "x+2", textController);

        verify(textContentWriteController).setNodeText(nodeModel, "\\latex x+2");
    }

    @Test
    public void editExistingTextualContent_rejectsHtmlLatexTextEdits() {
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class));
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("\\latex x+1");
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        assertThatThrownBy(() -> uut.editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.LATEX, "<html><body>x+2</body></html>", textController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Latex content does not allow html.");
    }
}

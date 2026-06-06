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
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class), textController);

        uut.setInitialContent(nodeModel, content);

        assertThat(nodeModel.getText()).isEqualTo("text");
        assertThat(HtmlUtils.htmlToPlain(DetailModel.getDetailText(nodeModel))).isEqualTo("details");
        assertThat(HtmlUtils.htmlToPlain(NoteModel.getNoteText(nodeModel))).isEqualTo("note");
    }

    @Test
    public void setInitialContent_rejectsFormulaValue() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeContentWriteRequest content = new NodeContentWriteRequest(
            "=1+1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(true);
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class), textController);

        assertThatThrownBy(() -> uut.setInitialContent(nodeModel, content))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Formula values are not allowed in createNodes or createSummary");
    }

    @Test
    public void editExistingTextualContent_updatesNodeTextThroughController() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            textContentWriteController, noteContentWriteController, textController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT, "value",
            textController);

        verify(textContentWriteController).setNodeText(nodeModel, "value");
    }

    @Test
    public void editExistingTextualContent_rejectsFormulaValues() {
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return "=1+1".equals(value);
        });
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class), textController);
        NodeModel nodeModel = mock(NodeModel.class);

        assertThatThrownBy(() -> uut.editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT, "=1+1", textController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("previewFormulaUpdates and applyFormulaUpdates");
    }

    @Test
    public void editExistingTextualContent_allowsDetailsEditWhenContentTypeMatches() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            textContentWriteController, noteContentWriteController, textController);
        NodeModel nodeModel = mock(NodeModel.class);

        uut.editExistingTextualContent(nodeModel, EditedElement.DETAILS, ContentType.PLAIN_TEXT,
            "value", textController);

        verify(textContentWriteController).setDetails(nodeModel, "value");
    }

    @Test
    public void editExistingTextualContent_rejectsHtmlForMarkdownText() {
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class), textController);
        NodeModel nodeModel = mock(NodeModel.class);
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
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            textContentWriteController, noteContentWriteController, textController);
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("plain");
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT,
            "<html><body>value</body></html>", textController);

        verify(textContentWriteController).setNodeText(nodeModel, "<html><body>value</body></html>");
    }

    @Test
    public void editExistingTextualContent_allowsLatexTextEditsWithPrefix() {
        TextContentWriteController textContentWriteController = mock(TextContentWriteController.class);
        NoteContentWriteController noteContentWriteController = mock(NoteContentWriteController.class);
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            textContentWriteController, noteContentWriteController, textController);
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("\\latex x+1");
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        uut.editExistingTextualContent(nodeModel, EditedElement.TEXT, ContentType.LATEX, "x+2", textController);

        verify(textContentWriteController).setNodeText(nodeModel, "\\latex x+2");
    }

    @Test
    public void editExistingTextualContent_rejectsHtmlLatexTextEdits() {
        TextController textController = mock(TextController.class);
        when(textController.isFormula(any())).thenReturn(false);
        TextualContentEditor uut = new TextualContentEditor(
            mock(TextContentWriteController.class), mock(NoteContentWriteController.class), textController);
        NodeModel nodeModel = mock(NodeModel.class);
        when(nodeModel.getUserObject()).thenReturn("\\latex x+1");
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);

        assertThatThrownBy(() -> uut.editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.LATEX, "<html><body>x+2</body></html>", textController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Latex content does not allow html.");
    }
}

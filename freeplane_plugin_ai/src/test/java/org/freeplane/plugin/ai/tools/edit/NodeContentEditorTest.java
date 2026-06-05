package org.freeplane.plugin.ai.tools.edit;

import java.util.Collections;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.edits.AIEdits;
import org.freeplane.plugin.ai.tools.content.ContentType;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class NodeContentEditorTest {
    @Test
    public void edit_returnsFullContentWhenNoEdits() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("text", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL)).thenReturn(contentItem);

        NodeContentItem result = editor.edit(nodeModel, Collections.emptyList());

        assertThat(result).isSameAs(contentItem);
        verify(nodeContentItemReader).readNodeContentItem(nodeModel, NodeContentPreset.FULL);
        verifyNoInteractions(textualContentEditor, attributesContentEditor, tagsContentEditor, iconsContentEditor,
            nodeStyleContentEditor, hyperlinkContentEditor);
    }

    @Test
    public void edit_throwsWhenOperationIsNotReplaceForText() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", null, EditOperation.ADD, null);

        assertThatThrownBy(() -> editor.edit(nodeModel, Collections.singletonList(editItem)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only REPLACE operations are supported for this element.");
    }

    @Test
    public void edit_throwsWhenOriginalContentTypeMissingForText() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TEXT, null, "updated", null, EditOperation.REPLACE, null);

        assertThatThrownBy(() -> editor.edit(nodeModel, Collections.singletonList(editItem)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing originalContentType for textual content edits.");
    }

    @Test
    public void validate_delegatesTextValidationToTextualContentEditor() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", null,
            EditOperation.REPLACE, null);

        editor.validate(nodeModel, Collections.singletonList(editItem));

        verify(textualContentEditor).validateExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", textController);
        verifyNoInteractions(nodeContentItemReader);
    }

    @Test
    public void edit_delegatesTextEditsToTextualContentEditor() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", null, EditOperation.REPLACE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        NodeContentItem result = editor.edit(nodeModel, Collections.singletonList(editItem));

        assertThat(result).isSameAs(contentItem);
        verify(textualContentEditor).editExistingTextualContent(
            nodeModel, EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", textController);
    }

    @Test
    public void edit_allowsDeleteForDetails() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.DETAILS, ContentType.PLAIN_TEXT, null, null, EditOperation.DELETE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        editor.edit(nodeModel, Collections.singletonList(editItem));

        verify(textualContentEditor).editExistingTextualContent(
            nodeModel, EditedElement.DETAILS, ContentType.PLAIN_TEXT, "", textController);
    }

    @Test
    public void edit_allowsDeleteForNote() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.NOTE, ContentType.PLAIN_TEXT, null, null, EditOperation.DELETE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        editor.edit(nodeModel, Collections.singletonList(editItem));

        verify(textualContentEditor).editExistingTextualContent(
            nodeModel, EditedElement.NOTE, ContentType.PLAIN_TEXT, "", textController);
    }

    @Test
    public void edit_delegatesHyperlinkEditsToHyperlinkContentEditor() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.HYPERLINK, null, "https://example.com", null, EditOperation.REPLACE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        editor.edit(nodeModel, Collections.singletonList(editItem));

        verify(hyperlinkContentEditor).editHyperlink(nodeModel, EditOperation.REPLACE, "https://example.com");
    }

    @Test
    public void edit_delegatesStyleEditsToNodeStyleContentEditor() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor editor = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.STYLE, null, "default", null, EditOperation.REPLACE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        editor.edit(nodeModel, Collections.singletonList(editItem));

        verify(nodeStyleContentEditor).editMainStyle(nodeModel, EditOperation.REPLACE, "default");
    }

    @Test
    public void edit_addsAiEditsMarkerWhenEditsAreApplied() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor uut = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TEXT, ContentType.PLAIN_TEXT, "updated", null, EditOperation.REPLACE, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("updated", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        uut.edit(nodeModel, Collections.singletonList(editItem));

        assertThat(nodeModel.getExtension(AIEdits.class)).isNotNull();
    }

    @Test
    public void edit_skipsAiEditsMarkerForHiddenSummaryNodes() {
        TextController textController = mock(TextController.class);
        NodeContentItemReader nodeContentItemReader = mock(NodeContentItemReader.class);
        TextualContentEditor textualContentEditor = mock(TextualContentEditor.class);
        AttributesContentEditor attributesContentEditor = mock(AttributesContentEditor.class);
        TagsContentEditor tagsContentEditor = mock(TagsContentEditor.class);
        IconsContentEditor iconsContentEditor = mock(IconsContentEditor.class);
        NodeStyleContentEditor nodeStyleContentEditor = mock(NodeStyleContentEditor.class);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentEditor uut = new NodeContentEditor(textController, nodeContentItemReader, textualContentEditor,
            attributesContentEditor, tagsContentEditor, iconsContentEditor, nodeStyleContentEditor,
            hyperlinkContentEditor);
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel summaryNode = new NodeModel("", mapModel);
        summaryNode.addExtension(SummaryNodeFlag.SUMMARY);
        summaryNode.insert(new NodeModel("child", mapModel), 0);
        NodeContentEditItem editItem = new NodeContentEditItem(
            Collections.singletonList("node-identifier"), EditedElement.TAGS, ContentType.PLAIN_TEXT, "tag", null, EditOperation.ADD, null);
        NodeContentItem contentItem = new NodeContentItem("node-identifier",
            new NodeContentResponse("", null, null, null, null, null, null, null), Collections.emptyList(),
            null, null, null, null);
        when(nodeContentItemReader.readNodeContentItem(summaryNode, NodeContentPreset.FULL, true, true, true))
            .thenReturn(contentItem);

        uut.edit(summaryNode, Collections.singletonList(editItem));

        assertThat(summaryNode.getExtension(AIEdits.class)).isNull();
    }
}

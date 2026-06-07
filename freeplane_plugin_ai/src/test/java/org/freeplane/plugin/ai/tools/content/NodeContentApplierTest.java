package org.freeplane.plugin.ai.tools.content;

import java.util.Collections;
import javax.swing.tree.DefaultMutableTreeNode;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.TagReference;
import org.freeplane.features.icon.Tags;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.HyperlinkContentEditor;
import org.freeplane.plugin.ai.tools.edit.IconsContentEditor;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TagsContentEditor;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;
import org.freeplane.plugin.ai.tools.text.DefaultEnglishTextProvider;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NodeContentApplierTest {
    @Test
    public void apply_outputsContentOnNode() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel node = new NodeModel("parent", mapModel);

        NamedIcon sampleIcon = new MindIcon("node-icon", "/images/node.svg", "node", 0);
        NodeContentWriteRequest content = new NodeContentWriteRequest(
            "root",
            null,
            "details",
            null,
            "note",
            null,
            Collections.singletonList(new AttributeEntry("key", "value")),
            Collections.singletonList("tag"),
            Collections.singletonList(sampleIcon.getName()),
            null);

        IconDescriptionResolver resolver = new IconDescriptionResolver(new DefaultEnglishTextProvider());
        NodeContentApplier uut = new NodeContentApplier(
            new TextualContentEditor(
                mock(TextContentWriteController.class), mock(NoteContentWriteController.class)),
            new AttributesContentEditor(mock(MAttributeController.class)),
            new TagsContentEditor(mock(MIconController.class)),
            new IconsContentEditor(resolver, Collections.singletonList(sampleIcon), mock(MIconController.class)),
            mock(HyperlinkContentEditor.class));

        uut.apply(node, content);

        assertThat(node.getText()).isEqualTo("root");
        assertThat(HtmlUtils.htmlToPlain(DetailModel.getDetailText(node))).isEqualTo("details");
        assertThat(HtmlUtils.htmlToPlain(NoteModel.getNoteText(node))).isEqualTo("note");
        NodeAttributeTableModel attributesModel = NodeAttributeTableModel.getModel(node);
        assertThat(attributesModel.getRowCount()).isEqualTo(1);
        assertThat(attributesModel.getName(0)).isEqualTo("key");
        assertThat(attributesModel.getValue(0)).isEqualTo("value");
        assertThat(Tags.getTagReferences(node)).hasSize(1);
        TagReference tagReference = Tags.getTagReferences(node).get(0);
        assertThat(tagReference.getContent()).isEqualTo("tag");
        assertThat(node.getIcons()).hasSize(1);
        assertThat(node.getIcons().get(0)).isSameAs(sampleIcon);
    }

    @Test
    public void apply_setsContentTypesWhenProvided() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel node = new NodeModel("root", mapModel);

        NodeContentWriteRequest content = new NodeContentWriteRequest(
            "Title",
            ContentType.MARKDOWN,
            "x^2",
            ContentType.LATEX,
            "note *value*",
            ContentType.MARKDOWN,
            null,
            null,
            null,
            null);
        IconDescriptionResolver resolver = new IconDescriptionResolver(new DefaultEnglishTextProvider());
        NodeContentApplier applier = new NodeContentApplier(
            new TextualContentEditor(
                mock(TextContentWriteController.class), mock(NoteContentWriteController.class)),
            new AttributesContentEditor(mock(MAttributeController.class)),
            new TagsContentEditor(mock(MIconController.class)),
            new IconsContentEditor(resolver, Collections.emptyList(), mock(MIconController.class)),
            mock(HyperlinkContentEditor.class));

        applier.apply(node, content);

        assertThat(NodeStyleModel.getNodeFormat(node)).isEqualTo("markdown");
        assertThat(DetailModel.getDetailContentType(node)).isEqualTo("latex");
        assertThat(NoteModel.getNoteContentType(node)).isEqualTo("markdown");
        assertThat(node.getText()).isEqualTo("Title");
        assertThat(DetailModel.getDetailText(node)).isEqualTo("x^2");
        assertThat(NoteModel.getNoteText(node)).isEqualTo("note *value*");
    }

    @Test
    public void apply_delegatesHyperlinkToEditor() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel node = new NodeModel("parent", mapModel);
        HyperlinkContentEditor hyperlinkContentEditor = mock(HyperlinkContentEditor.class);
        NodeContentWriteRequest content = new NodeContentWriteRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "https://example.com");
        NodeContentApplier applier = new NodeContentApplier(
            new TextualContentEditor(
                mock(TextContentWriteController.class), mock(NoteContentWriteController.class)),
            new AttributesContentEditor(mock(MAttributeController.class)),
            new TagsContentEditor(mock(MIconController.class)),
            new IconsContentEditor(new IconDescriptionResolver(new DefaultEnglishTextProvider()),
                Collections.emptyList(), mock(MIconController.class)),
            hyperlinkContentEditor);

        applier.apply(node, content);

        verify(hyperlinkContentEditor).setInitialHyperlink(node, "https://example.com");
    }

    private static IconRegistry iconRegistry() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("tags");
        DefaultMutableTreeNode uncategorized = new DefaultMutableTreeNode("uncategorized");
        return new IconRegistry(new TagCategories(root, uncategorized, "/"));
    }
}

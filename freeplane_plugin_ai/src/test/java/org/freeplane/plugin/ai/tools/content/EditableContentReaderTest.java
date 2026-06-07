package org.freeplane.plugin.ai.tools.content;

import java.util.Arrays;
import java.util.Collections;
import javax.swing.tree.DefaultMutableTreeNode;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.Tag;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.TagReference;
import org.freeplane.features.icon.Tags;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EditableContentReaderTest {
    @Test
    public void readEditableContent_includesTextRepresentations() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("raw", mapModel);
        nodeModel.setUserObject("<html><body><p>raw</p></body></html>");
        TextController textController = mock(TextController.class);
        when(textController.getTransformedTextForClipboard(eq(nodeModel), eq(nodeModel), any()))
            .thenReturn("<html><body><p>Transformed</p></body></html>");
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);
        when(textController.isFormula(any())).thenReturn(false);
        EditableContentReader reader = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter());
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.TEXT));

        EditableContent editableContent = reader.readEditableContent(nodeModel, request);

        EditableText editableText = editableContent.getEditableText();
        assertThat(editableText.getRaw()).contains("raw");
        assertThat(editableText.getTransformed()).contains("Transformed");
        assertThat(editableText.getPlain()).isEqualTo("Transformed");
        assertThat(editableText.getContentType()).isEqualTo(ContentType.HTML);
        assertThat(editableText.getIsFormula()).isFalse();
        assertThat(editableText.getIsEditable()).isTrue();
    }

    @Test
    public void readEditableContent_usesNodeFormatForMarkdownText() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("raw", mapModel);
        nodeModel.setUserObject("raw");
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(nodeModel)).thenReturn("markdown");
        when(textController.isFormula(any())).thenReturn(false);
        EditableContentReader reader = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter());
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.TEXT));

        EditableContent editableContent = reader.readEditableContent(nodeModel, request);

        assertThat(editableContent.getEditableText().getContentType()).isEqualTo(ContentType.MARKDOWN);
    }

    @Test
    public void readEditableContent_includesAttributes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        attributeTableModel.silentlyAddRowNoUndo(nodeModel, new Attribute(
            "key", "<html><body><p>value</p></body></html>"));
        TextController textController = mock(TextController.class);
        when(textController.getTransformedObjectNoFormattingNoThrow(eq(nodeModel), any(NodeAttributeTableModel.class), any()))
            .thenReturn("<html><body><p>Transformed</p></body></html>");
        when(textController.isFormula(any())).thenReturn(false);
        EditableContentReader reader = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter());
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.ATTRIBUTES));

        EditableContent editableContent = reader.readEditableContent(nodeModel, request);

        EditableAttribute attribute = editableContent.getEditableAttributes().get(0);
        assertThat(attribute.getName()).isEqualTo("key");
        assertThat(attribute.getRawValue()).contains("value");
        assertThat(attribute.getTransformedValue()).contains("Transformed");
        assertThat(attribute.getPlainValue()).isEqualTo("Transformed");
        assertThat(attribute.getIsFormula()).isFalse();
        assertThat(attribute.getIsEditable()).isTrue();
        assertThat(attribute.getIndex()).isEqualTo(0);
    }

    @Test
    public void readEditableContent_reportsFormulaStateAndAvailabilityForFormulaBackedAttributes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        attributeTableModel.silentlyAddRowNoUndo(nodeModel, new Attribute("key", "=1+1"));
        TextController textController = mock(TextController.class);
        when(textController.getTransformedObjectNoFormattingNoThrow(eq(nodeModel), any(NodeAttributeTableModel.class), any()))
            .thenReturn("2");
        when(textController.isFormula(any())).thenReturn(true);
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.ATTRIBUTES));

        EditableContent editingWithoutFormulaPermission = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter(),
            () -> org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel.EDITING,
            () -> Boolean.FALSE)
            .readEditableContent(nodeModel, request);
        EditableContent editingWithFormulaPermission = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter(),
            () -> org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel.EDITING,
            () -> Boolean.TRUE)
            .readEditableContent(nodeModel, request);

        assertThat(editingWithoutFormulaPermission.getEditableAttributes().get(0).getIsFormula()).isTrue();
        assertThat(editingWithoutFormulaPermission.getEditableAttributes().get(0).getIsEditable()).isFalse();
        assertThat(editingWithFormulaPermission.getEditableAttributes().get(0).getIsFormula()).isTrue();
        assertThat(editingWithFormulaPermission.getEditableAttributes().get(0).getIsEditable()).isTrue();
    }

    @Test
    public void readEditableContent_reportsFormulaStateAndAvailabilityForFormulaBackedText() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        nodeModel.setUserObject("=1+1");
        TextController textController = mock(TextController.class);
        when(textController.getNodeFormat(nodeModel)).thenReturn(null);
        when(textController.isFormula(any())).thenReturn(true);
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.TEXT));

        EditableContent editingWithoutFormulaPermission = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter(),
            () -> org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel.EDITING,
            () -> Boolean.FALSE)
            .readEditableContent(nodeModel, request);
        EditableContent editingWithFormulaPermission = new EditableContentReader(
            textController,
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter(),
            () -> org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel.EDITING,
            () -> Boolean.TRUE)
            .readEditableContent(nodeModel, request);

        assertThat(editingWithoutFormulaPermission.getEditableText().getIsFormula()).isTrue();
        assertThat(editingWithoutFormulaPermission.getEditableText().getIsEditable()).isFalse();
        assertThat(editingWithFormulaPermission.getEditableText().getIsFormula()).isTrue();
        assertThat(editingWithFormulaPermission.getEditableText().getIsEditable()).isTrue();
        assertThat(editingWithFormulaPermission.getEditableText().getContentType()).isEqualTo(ContentType.PLAIN_TEXT);
    }

    @Test
    public void readEditableContent_includesTagsAndIconsWithIndexes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TagCategories tagCategories = mapModel.getIconRegistry().getTagCategories();
        TagReference reference = tagCategories.createTagReference("flag");
        Tags.setTagReferences(nodeModel, Collections.singletonList(reference));
        NamedIcon icon = new MindIcon("test", "/images/test.svg", "test", 0);
        nodeModel.addIcon(icon);
        EditableContentReader reader = new EditableContentReader(
            mock(TextController.class),
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter());
        EditableContentRequest request = new EditableContentRequest(
            Arrays.asList(EditableContentField.TAGS, EditableContentField.ICONS));

        EditableContent editableContent = reader.readEditableContent(nodeModel, request);

        EditableTag tag = editableContent.getEditableTags().get(0);
        assertThat(tag.getValue()).isEqualTo("flag");
        assertThat(tag.getIndex()).isEqualTo(0);
        EditableIcon editableIcon = editableContent.getEditableIcons().get(0);
        assertThat(editableIcon.getDescription()).isEqualTo("Test");
        assertThat(editableIcon.getIndex()).isEqualTo(0);
    }

    @Test
    public void readEditableContent_omitsRemovedTagsAndRecalculatesVisibleIndexes() {
        MapModel mapModel = new MapModel((source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TagCategories tagCategories = mapModel.getIconRegistry().getTagCategories();
        Tags.setTagReferences(nodeModel, Arrays.asList(
            new TagReference(Tag.REMOVED_TAG),
            tagCategories.createTagReference("flag")));
        EditableContentReader uut = new EditableContentReader(
            mock(TextController.class),
            new IconDescriptionResolver(key -> null),
            new ContentTypeConverter());
        EditableContentRequest request = new EditableContentRequest(
            Collections.singletonList(EditableContentField.TAGS));

        EditableContent editableContent = uut.readEditableContent(nodeModel, request);

        assertThat(editableContent.getEditableTags())
            .extracting(EditableTag::getValue, EditableTag::getIndex)
            .containsExactly(tuple("flag", 0));
    }

    private static IconRegistry iconRegistry() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("tags");
        DefaultMutableTreeNode uncategorized = new DefaultMutableTreeNode("uncategorized");
        return new IconRegistry(new TagCategories(root, uncategorized, "/"));
    }
}

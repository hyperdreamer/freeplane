package org.freeplane.plugin.ai.tools.edit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.Tag;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.TagReference;
import org.freeplane.features.icon.Tags;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.TagsContent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TagsContentEditorTest {
    @Test
    public void setInitialContent_addsTags() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TagsContent tagsContent = new TagsContent(Collections.singletonList("flag"));
        TagsContentEditor uut = new TagsContentEditor(mock(MIconController.class));

        uut.setInitialContent(nodeModel, tagsContent);

        assertThat(Tags.getTagReferences(nodeModel)).hasSize(1);
        TagReference reference = Tags.getTagReferences(nodeModel).get(0);
        assertThat(reference.getContent()).isEqualTo("flag");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void editExistingTagsContent_addsTagsThroughController() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        MIconController iconController = mock(MIconController.class);
        TagsContentEditor uut = new TagsContentEditor(iconController);

        uut.editExistingTagsContent(nodeModel, EditOperation.ADD, null, null, "tag");

        ArgumentCaptor<List<TagReference>> referencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(iconController).setTagReferences(eq(nodeModel), referencesCaptor.capture());
        List<TagReference> references = referencesCaptor.getValue();
        assertThat(references).hasSize(1);
        assertThat(references.get(0).getContent()).isEqualTo("tag");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void editExistingTagsContent_replacesTagsThroughController() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TagCategories tagCategories = mapModel.getIconRegistry().getTagCategories();
        Tags.setTagReferences(nodeModel, Collections.singletonList(tagCategories.createTagReference("old")));
        MIconController iconController = mock(MIconController.class);
        TagsContentEditor uut = new TagsContentEditor(iconController);

        uut.editExistingTagsContent(nodeModel, EditOperation.REPLACE, null, 0, "new");

        ArgumentCaptor<List<TagReference>> referencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(iconController).setTagReferences(eq(nodeModel), referencesCaptor.capture());
        List<TagReference> references = referencesCaptor.getValue();
        assertThat(references).hasSize(1);
        assertThat(references.get(0).getContent()).isEqualTo("new");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void editExistingTagsContent_addsTagsWithoutPreservingRemovedTombstones() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        Tags.setTagReferences(nodeModel, Collections.singletonList(new TagReference(Tag.REMOVED_TAG)));
        MIconController iconController = mock(MIconController.class);
        TagsContentEditor uut = new TagsContentEditor(iconController);

        uut.editExistingTagsContent(nodeModel, EditOperation.ADD, null, null, "tag");

        ArgumentCaptor<List<TagReference>> referencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(iconController).setTagReferences(eq(nodeModel), referencesCaptor.capture());
        List<TagReference> references = referencesCaptor.getValue();
        assertThat(references).hasSize(1);
        assertThat(references.get(0).getContent()).isEqualTo("tag");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void editExistingTagsContent_replacesVisibleIndexIgnoringRemovedTombstones() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        TagCategories tagCategories = mapModel.getIconRegistry().getTagCategories();
        Tags.setTagReferences(nodeModel, Arrays.asList(
            new TagReference(Tag.REMOVED_TAG),
            tagCategories.createTagReference("old")));
        MIconController iconController = mock(MIconController.class);
        TagsContentEditor uut = new TagsContentEditor(iconController);

        uut.editExistingTagsContent(nodeModel, EditOperation.REPLACE, null, 0, "new");

        ArgumentCaptor<List<TagReference>> referencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(iconController).setTagReferences(eq(nodeModel), referencesCaptor.capture());
        List<TagReference> references = referencesCaptor.getValue();
        assertThat(references).hasSize(1);
        assertThat(references.get(0).getContent()).isEqualTo("new");
    }

    private static IconRegistry iconRegistry() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("tags");
        DefaultMutableTreeNode uncategorized = new DefaultMutableTreeNode("uncategorized");
        return new IconRegistry(new TagCategories(root, uncategorized, "/"));
    }
}

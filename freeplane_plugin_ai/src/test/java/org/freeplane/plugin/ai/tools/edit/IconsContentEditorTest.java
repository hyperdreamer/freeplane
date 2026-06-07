package org.freeplane.plugin.ai.tools.edit;

import java.util.Collections;
import javax.swing.tree.DefaultMutableTreeNode;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.IconDescriptionResolver;
import org.freeplane.plugin.ai.tools.content.IconsContent;
import org.freeplane.plugin.ai.tools.text.DefaultEnglishTextProvider;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IconsContentEditorTest {
    @Test
    public void setInitialContent_addsIconByDescription() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        DefaultEnglishTextProvider englishTextProvider = new DefaultEnglishTextProvider();
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        NamedIcon sampleIcon = new MindIcon("test", "/images/test.svg", "test", 0);
        IconsContentEditor uut = new IconsContentEditor(
            resolver, Collections.singletonList(sampleIcon), mock(MIconController.class));
        IconsContent iconsContent = new IconsContent(Collections.singletonList(sampleIcon.getName()));

        uut.setInitialContent(nodeModel, iconsContent);

        assertThat(nodeModel.getIcons()).hasSize(1);
        assertThat(nodeModel.getIcons().get(0)).isSameAs(sampleIcon);
    }

    @Test
    public void editExistingIconsContent_addsIconsThroughController() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        DefaultEnglishTextProvider englishTextProvider = new DefaultEnglishTextProvider();
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        NamedIcon sampleIcon = new MindIcon("test", "/images/test.svg", "test", 0);
        MIconController iconController = mock(MIconController.class);
        IconsContentEditor uut = new IconsContentEditor(
            resolver, Collections.singletonList(sampleIcon), iconController);

        uut.editExistingIconsContent(nodeModel, EditOperation.ADD, null, null, sampleIcon.getName());

        verify(iconController).addIcon(eq(nodeModel), eq(sampleIcon));
    }

    @Test
    public void editExistingIconsContent_removesIconsThroughController() {
        MapModel mapModel = new MapModel(
            (source, targetMap, withChildren) -> null, iconRegistry(), null);
        NodeModel nodeModel = new NodeModel("node", mapModel);
        DefaultEnglishTextProvider englishTextProvider = new DefaultEnglishTextProvider();
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        NamedIcon sampleIcon = new MindIcon("test", "/images/test.svg", "test", 0);
        nodeModel.addIcon(sampleIcon);
        MIconController iconController = mock(MIconController.class);
        IconsContentEditor uut = new IconsContentEditor(
            resolver, Collections.singletonList(sampleIcon), iconController);

        uut.editExistingIconsContent(nodeModel, EditOperation.DELETE, null, 0, null);

        verify(iconController).removeIcon(eq(nodeModel), eq(0));
    }

    private static IconRegistry iconRegistry() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("tags");
        DefaultMutableTreeNode uncategorized = new DefaultMutableTreeNode("uncategorized");
        return new IconRegistry(new TagCategories(root, uncategorized, "/"));
    }
}

package org.freeplane.plugin.ai.tools.content;

import java.util.Arrays;
import java.util.Collections;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.UserIcon;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.styles.LogicalStyleController.StyleOption;
import org.freeplane.plugin.ai.tools.text.EnglishTextProvider;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IconsContentReaderTest {
    @Test
    public void readIconsContent_usesEnglishDescriptionsForBuiltinIcons() {
        EnglishTextProvider englishTextProvider = key -> "English Description";
        IconController iconController = mock(IconController.class);
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        IconsContentReader uut = new IconsContentReader(resolver, iconController);
        MindIcon mindIcon = new MindIcon("idea", "idea.svg", "icon_idea", 1);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE))
            .thenReturn(Collections.singletonList(mindIcon));

        IconsContent content = uut.readIconsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getDescriptions()).containsExactly("English Description");
    }

    @Test
    public void readIconsContent_decodesEmojiFromName() {
        EnglishTextProvider englishTextProvider = key -> "English Description";
        IconController iconController = mock(IconController.class);
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        IconsContentReader uut = new IconsContentReader(resolver, iconController);
        String emoji = new String(new int[] { 0x1F4D9 }, 0, 1);
        MindIcon emojiIcon = new MindIcon("emoji-1f4d9", "1f4d9.svg", "emoji_description", 1);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE))
            .thenReturn(Collections.singletonList(emojiIcon));

        IconsContent content = uut.readIconsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getDescriptions()).containsExactly(emoji);
    }

    @Test
    public void readIconsContent_usesRelativePathForUserIcons() {
        EnglishTextProvider englishTextProvider = key -> "English Description";
        IconController iconController = mock(IconController.class);
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        IconsContentReader uut = new IconsContentReader(resolver, iconController);
        UserIcon userIcon = new UserIcon("custom", "custom/icon.svg", "Custom", 1);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE))
            .thenReturn(Collections.singletonList(userIcon));

        IconsContent content = uut.readIconsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getDescriptions()).containsExactly("custom/icon.svg");
    }

    @Test
    public void readIconsContent_fallsBackToIconNameWhenEnglishTextMissing() {
        EnglishTextProvider englishTextProvider = key -> null;
        IconController iconController = mock(IconController.class);
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        IconsContentReader uut = new IconsContentReader(resolver, iconController);
        MindIcon mindIcon = new MindIcon("idea", "idea.svg", "icon_idea", 1);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE))
            .thenReturn(Collections.singletonList(mindIcon));

        IconsContent content = uut.readIconsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getDescriptions()).containsExactly("Idea");
    }

    @Test
    public void readIconsContent_preservesIconOrder() {
        EnglishTextProvider englishTextProvider = key -> {
            if ("icon_idea".equals(key)) {
                return "Idea";
            }
            return null;
        };
        IconController iconController = mock(IconController.class);
        IconDescriptionResolver resolver = new IconDescriptionResolver(englishTextProvider);
        IconsContentReader uut = new IconsContentReader(resolver, iconController);
        MindIcon firstIcon = new MindIcon("idea", "idea.svg", "icon_idea", 1);
        UserIcon secondIcon = new UserIcon("custom", "custom/icon.svg", "Custom", 2);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE))
            .thenReturn(Arrays.asList(firstIcon, secondIcon));

        IconsContent content = uut.readIconsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getDescriptions()).containsExactly("Idea", "custom/icon.svg");
    }
}

package org.freeplane.plugin.ai.tools.content;

import java.util.Arrays;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.Tag;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TagsContentReaderTest {
    @Test
    public void readTagsContent_returnsTagsForFullPreset() {
        IconController iconController = mock(IconController.class);
        NodeModel nodeModel = mock(NodeModel.class);
        when(iconController.getTags(nodeModel)).thenReturn(Arrays.asList(new Tag("Tag 1"), new Tag("Tag 2")));
        TagsContentReader uut = new TagsContentReader(iconController);

        TagsContent content = uut.readTagsContent(nodeModel, NodeContentPreset.FULL);

        assertThat(content.getTags()).containsExactly("Tag 1", "Tag 2");
    }
}

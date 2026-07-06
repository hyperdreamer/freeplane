package org.freeplane.plugin.ai.tools.read;

import java.util.Arrays;
import java.util.Collections;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;
import org.freeplane.plugin.ai.tools.content.IconsContent;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.TagsContent;
import org.freeplane.plugin.ai.tools.content.TextualContent;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadNodeContentMapperTest {
    @Test
    public void fromFullContent_flattensPlainFields() {
        NodeContentResponse internalContent = new NodeContentResponse(
            null,
            new TextualContent("Text", "Details", "Note"),
            new AttributesContent(Collections.singletonList(new AttributeEntry("name", "value"))),
            new TagsContent(Collections.singletonList("tag")),
            new IconsContent(Collections.singletonList("button_ok")),
            Arrays.asList("Default"),
            "Default",
            null);

        ReadNodeContent content = ReadNodeContentMapper.fromFullContent(internalContent);

        assertThat(content.getShortText()).isNull();
        assertThat(content.getText()).isEqualTo("Text");
        assertThat(content.getDetails()).isEqualTo("Details");
        assertThat(content.getNote()).isEqualTo("Note");
        assertThat(content.getAttributes()).hasSize(1);
        assertThat(content.getAttributes().get(0).getName()).isEqualTo("name");
        assertThat(content.getAttributes().get(0).getValue()).isEqualTo("value");
        assertThat(content.getTags()).containsExactly("tag");
        assertThat(content.getIcons()).containsExactly("button_ok");
    }

    @Test
    public void fromFullContent_normalizesAttributeValuesToPlainText() {
        NodeContentResponse internalContent = new NodeContentResponse(
            null,
            null,
            new AttributesContent(Collections.singletonList(new AttributeEntry(
                "html",
                "<html><body><p>A paragraph followed by an empty one</p><p></p></body></html>"))),
            null,
            null,
            null,
            null,
            null);

        ReadNodeContent content = ReadNodeContentMapper.fromFullContent(internalContent);

        assertThat(content.getAttributes()).hasSize(1);
        assertThat(content.getAttributes().get(0).getValue()).isEqualTo("A paragraph followed by an empty one");
    }

    @Test
    public void fromShortText_setsOnlyShortText() {
        ReadNodeContent content = ReadNodeContentMapper.fromShortText("Short");

        assertThat(content.getShortText()).isEqualTo("Short");
        assertThat(content.getText()).isNull();
        assertThat(content.getDetails()).isNull();
        assertThat(content.getNote()).isNull();
        assertThat(content.getAttributes()).isNull();
        assertThat(content.getTags()).isNull();
        assertThat(content.getIcons()).isNull();
    }
}

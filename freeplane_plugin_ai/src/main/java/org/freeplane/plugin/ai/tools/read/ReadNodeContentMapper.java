package org.freeplane.plugin.ai.tools.read;

import java.util.ArrayList;
import java.util.List;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;
import org.freeplane.plugin.ai.tools.content.IconsContent;
import org.freeplane.plugin.ai.tools.content.NodeContentResponse;
import org.freeplane.plugin.ai.tools.content.TagsContent;
import org.freeplane.plugin.ai.tools.content.TextualContent;

final class ReadNodeContentMapper {
    private ReadNodeContentMapper() {
    }

    static ReadNodeContent fromFullContent(NodeContentResponse content) {
        if (content == null) {
            return null;
        }
        TextualContent textualContent = content.getTextualContent();
        String text = textualContent == null ? null : textualContent.getText();
        String details = textualContent == null ? null : textualContent.getDetails();
        String note = textualContent == null ? null : textualContent.getNote();
        List<AttributeEntry> attributes = normalizeAttributes(content.getAttributesContent());
        List<String> tags = copyOrNull(content.getTagsContent());
        List<String> icons = copyOrNull(content.getIconsContent());
        if (text == null && details == null && note == null
            && attributes == null && tags == null && icons == null) {
            return null;
        }
        return new ReadNodeContent(null, text, details, note, attributes, tags, icons);
    }

    static ReadNodeContent fromShortText(String shortText) {
        if (shortText == null) {
            return null;
        }
        return new ReadNodeContent(shortText, null, null, null, null, null, null);
    }

    private static List<AttributeEntry> normalizeAttributes(AttributesContent attributesContent) {
        if (attributesContent == null || attributesContent.getAttributes() == null
            || attributesContent.getAttributes().isEmpty()) {
            return null;
        }
        List<AttributeEntry> normalizedAttributes = new ArrayList<>(attributesContent.getAttributes().size());
        for (AttributeEntry attribute : attributesContent.getAttributes()) {
            if (attribute == null) {
                continue;
            }
            normalizedAttributes.add(new AttributeEntry(
                attribute.getName(),
                HtmlUtils.htmlToPlain(attribute.getValue())));
        }
        return normalizedAttributes.isEmpty() ? null : normalizedAttributes;
    }

    private static List<String> copyOrNull(TagsContent tagsContent) {
        if (tagsContent == null || tagsContent.getTags() == null || tagsContent.getTags().isEmpty()) {
            return null;
        }
        return new ArrayList<>(tagsContent.getTags());
    }

    private static List<String> copyOrNull(IconsContent iconsContent) {
        if (iconsContent == null || iconsContent.getDescriptions() == null
            || iconsContent.getDescriptions().isEmpty()) {
            return null;
        }
        return new ArrayList<>(iconsContent.getDescriptions());
    }
}

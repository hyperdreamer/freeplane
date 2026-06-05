package org.freeplane.plugin.ai.tools.content;

import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.NodeModel;

public class NodeContentReader {
    private final TextualContentReader textualContentReader;
    private final AttributesContentReader attributesContentReader;
    private final TagsContentReader tagsContentReader;
    private final IconsContentReader iconsContentReader;
    private final NodeStyleContentReader nodeStyleContentReader;
    private final EditableContentReader editableContentReader;

    public NodeContentReader(TextualContentReader textualContentReader,
                             AttributesContentReader attributesContentReader,
                             TagsContentReader tagsContentReader,
                             IconsContentReader iconsContentReader,
                             NodeStyleContentReader nodeStyleContentReader,
                             EditableContentReader editableContentReader) {
        this.textualContentReader = Objects.requireNonNull(textualContentReader, "textualContentReader");
        this.attributesContentReader = Objects.requireNonNull(attributesContentReader, "attributesContentReader");
        this.tagsContentReader = Objects.requireNonNull(tagsContentReader, "tagsContentReader");
        this.iconsContentReader = Objects.requireNonNull(iconsContentReader, "iconsContentReader");
        this.nodeStyleContentReader = Objects.requireNonNull(nodeStyleContentReader, "nodeStyleContentReader");
        this.editableContentReader = Objects.requireNonNull(editableContentReader, "editableContentReader");
    }

    public NodeContentResponse readNodeContent(NodeModel nodeModel, NodeContentPreset preset) {
        if (nodeModel == null) {
            return null;
        }
        if (preset == NodeContentPreset.BRIEF) {
            String briefText = textualContentReader.readBriefText(nodeModel);
            return new NodeContentResponse(briefText, null, null, null, null, null, null, null);
        }
        TextualContent textualContent = textualContentReader.readTextualContent(nodeModel, preset);
        AttributesContent attributesContent = attributesContentReader.readAttributesContent(nodeModel, preset);
        TagsContent tagsContent = tagsContentReader.readTagsContent(nodeModel, preset);
        IconsContent iconsContent = iconsContentReader.readIconsContent(nodeModel, preset);
        return new NodeContentResponse(null, textualContent, attributesContent, tagsContent, iconsContent,
            nodeStyleContentReader.readActiveStyles(nodeModel),
            nodeStyleContentReader.readMainStyle(nodeModel),
            null);
    }

    public NodeContentResponse readNodeContent(NodeModel nodeModel, NodeContentRequest request,
                                               NodeContentPreset fallbackPreset) {
        if (nodeModel == null) {
            return null;
        }
        if (request == null) {
            return readNodeContent(nodeModel, fallbackPreset);
        }
        TextualContent textualContent = textualContentReader.readTextualContent(
            nodeModel, request.getTextualContentRequest());
        AttributesContent attributesContent = attributesContentReader.readAttributesContent(
            nodeModel, request.getAttributesContentRequest());
        TagsContent tagsContent = tagsContentReader.readTagsContent(
            nodeModel, request.getTagsContentRequest());
        IconsContent iconsContent = iconsContentReader.readIconsContent(
            nodeModel, request.getIconsContentRequest());
        List<String> activeStyles = nodeStyleContentReader.readActiveStyles(nodeModel);
        String mainStyle = nodeStyleContentReader.readMainStyle(nodeModel);
        EditableContent editableContent = editableContentReader.readEditableContent(
            nodeModel, request.getEditableContentRequest());
        if (textualContent == null && attributesContent == null && tagsContent == null && iconsContent == null
            && editableContent == null && activeStyles == null && mainStyle == null) {
            return null;
        }
        return new NodeContentResponse(null, textualContent, attributesContent, tagsContent, iconsContent,
            activeStyles, mainStyle, editableContent);
    }

    public boolean matches(NodeModel nodeModel, NodeContentRequest request, NodeContentValueMatcher valueMatcher) {
        if (nodeModel == null || valueMatcher == null) {
            return false;
        }
        if (request == null) {
            return false;
        }
        if (textualContentReader.matches(nodeModel, request.getTextualContentRequest(), valueMatcher)) {
            return true;
        }
        if (attributesContentReader.matches(nodeModel, request.getAttributesContentRequest(), valueMatcher)) {
            return true;
        }
        if (tagsContentReader.matches(nodeModel, request.getTagsContentRequest(), valueMatcher)) {
            return true;
        }
        return iconsContentReader.matches(nodeModel, request.getIconsContentRequest(), valueMatcher);
    }
}

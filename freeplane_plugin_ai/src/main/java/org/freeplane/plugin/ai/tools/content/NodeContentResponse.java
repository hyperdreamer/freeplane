package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeContentResponse {
    private final String briefText;
    private final TextualContent textualContent;
    private final AttributesContent attributesContent;
    private final TagsContent tagsContent;
    private final IconsContent iconsContent;
    private final List<String> activeStyles;
    private final String mainStyle;
    private final EditableContent editableContent;

    @JsonCreator
    public NodeContentResponse(@JsonProperty("briefText") String briefText,
                               @JsonProperty("textualContent") TextualContent textualContent,
                               @JsonProperty("attributesContent") AttributesContent attributesContent,
                               @JsonProperty("tagsContent") TagsContent tagsContent,
                               @JsonProperty("iconsContent") IconsContent iconsContent,
                               @JsonProperty("activeStyles") List<String> activeStyles,
                               @JsonProperty("mainStyle") String mainStyle,
                               @JsonProperty("editableContent") EditableContent editableContent) {
        this.briefText = briefText;
        this.textualContent = textualContent;
        this.attributesContent = attributesContent;
        this.tagsContent = tagsContent;
        this.iconsContent = iconsContent;
        this.activeStyles = activeStyles;
        this.mainStyle = mainStyle;
        this.editableContent = editableContent;
    }

    public String getBriefText() {
        return briefText;
    }

    public TextualContent getTextualContent() {
        return textualContent;
    }

    public AttributesContent getAttributesContent() {
        return attributesContent;
    }

    public TagsContent getTagsContent() {
        return tagsContent;
    }

    public IconsContent getIconsContent() {
        return iconsContent;
    }

    public List<String> getActiveStyles() {
        return activeStyles;
    }

    public String getMainStyle() {
        return mainStyle;
    }

    public EditableContent getEditableContent() {
        return editableContent;
    }
}

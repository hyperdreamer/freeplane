package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadNodeContent {
    @Description("Short plain-text node text used when full node content is not requested.")
    private final String shortText;
    @Description("Plain-text node text, not HTML.")
    private final String text;
    @Description("Plain-text node details, not HTML.")
    private final String details;
    @Description("Plain-text node note, not HTML.")
    private final String note;
    @Description("Node attributes with plain-text values.")
    private final List<AttributeEntry> attributes;
    @Description("Node tags.")
    private final List<String> tags;
    @Description("Node icons as icon descriptions or emoji characters.")
    private final List<String> icons;

    @JsonCreator
    public ReadNodeContent(@JsonProperty("shortText") String shortText,
                           @JsonProperty("text") String text,
                           @JsonProperty("details") String details,
                           @JsonProperty("note") String note,
                           @JsonProperty("attributes") List<AttributeEntry> attributes,
                           @JsonProperty("tags") List<String> tags,
                           @JsonProperty("icons") List<String> icons) {
        this.shortText = shortText;
        this.text = text;
        this.details = details;
        this.note = note;
        this.attributes = attributes;
        this.tags = tags;
        this.icons = icons;
    }

    public String getShortText() {
        return shortText;
    }

    public String getText() {
        return text;
    }

    public String getDetails() {
        return details;
    }

    public String getNote() {
        return note;
    }

    public List<AttributeEntry> getAttributes() {
        return attributes;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getIcons() {
        return icons;
    }
}

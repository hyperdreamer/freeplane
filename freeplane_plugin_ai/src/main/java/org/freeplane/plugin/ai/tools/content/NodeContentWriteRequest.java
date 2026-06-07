package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeContentWriteRequest {
    private final String text;
    @JsonProperty(required = false)
    @Description("Optional text content type. Formula values are rejected in createNodes/createSummary; create the node first, then use previewFormulaUpdates/applyFormulaUpdates.")
    private final ContentType textContentType;
    @JsonProperty(required = false)
    @Description("Optional details.")
    private final String details;
    @JsonProperty(required = false)
    @Description("Optional details content type. Formula values are rejected in createNodes/createSummary; create the node first, then use previewFormulaUpdates/applyFormulaUpdates.")
    private final ContentType detailsContentType;
    @JsonProperty(required = false)
    @Description("Optional note.")
    private final String note;
    @JsonProperty(required = false)
    @Description("Optional note content type. Formula values are rejected in createNodes/createSummary; create the node first, then use previewFormulaUpdates/applyFormulaUpdates.")
    private final ContentType noteContentType;
    @JsonProperty(required = false)
    @Description("Optional attributes. Formula values are rejected in createNodes/createSummary; create the node first, then use previewFormulaUpdates/applyFormulaUpdates.")
    private final List<AttributeEntry> attributes;
    @JsonProperty(required = false)
    @Description("Optional tags.")
    private final List<String> tags;
    @JsonProperty(required = false)
    @Description("Optional icons.")
    private final List<String> icons;
    @JsonProperty(required = false)
    @Description("Optional hyperlink for the new node.")
    private final String hyperlink;

    @JsonCreator
    public NodeContentWriteRequest(@JsonProperty("text") String text,
                                   @JsonProperty(value = "textContentType", required = false) ContentType textContentType,
                                   @JsonProperty(value = "details", required = false) String details,
                                   @JsonProperty(value = "detailsContentType", required = false) ContentType detailsContentType,
                                   @JsonProperty(value = "note", required = false) String note,
                                   @JsonProperty(value = "noteContentType", required = false) ContentType noteContentType,
                                   @JsonProperty(value = "attributes", required = false) List<AttributeEntry> attributes,
                                   @JsonProperty(value = "tags", required = false) List<String> tags,
                                   @JsonProperty(value = "icons", required = false) List<String> icons,
                                   @JsonProperty(value = "hyperlink", required = false) String hyperlink) {
        this.text = text;
        this.textContentType = textContentType;
        this.details = details;
        this.detailsContentType = detailsContentType;
        this.note = note;
        this.noteContentType = noteContentType;
        this.attributes = attributes;
        this.tags = tags;
        this.icons = icons;
        this.hyperlink = hyperlink;
    }

    public String getText() {
        return text;
    }

    public ContentType getTextContentType() {
        return textContentType;
    }

    public String getDetails() {
        return details;
    }

    public ContentType getDetailsContentType() {
        return detailsContentType;
    }

    public String getNote() {
        return note;
    }

    public ContentType getNoteContentType() {
        return noteContentType;
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

    public String getHyperlink() {
        return hyperlink;
    }
}

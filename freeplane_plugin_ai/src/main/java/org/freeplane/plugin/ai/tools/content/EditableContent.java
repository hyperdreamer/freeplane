package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableContent {
    private final EditableText editableText;
    private final EditableText editableDetails;
    private final EditableText editableNote;
    private final List<EditableAttribute> editableAttributes;
    private final List<EditableTag> editableTags;
    private final List<EditableIcon> editableIcons;

    @JsonCreator
    public EditableContent(@JsonProperty("editableText") EditableText editableText,
                           @JsonProperty("editableDetails") EditableText editableDetails,
                           @JsonProperty("editableNote") EditableText editableNote,
                           @JsonProperty("editableAttributes") List<EditableAttribute> editableAttributes,
                           @JsonProperty("editableTags") List<EditableTag> editableTags,
                           @JsonProperty("editableIcons") List<EditableIcon> editableIcons) {
        this.editableText = editableText;
        this.editableDetails = editableDetails;
        this.editableNote = editableNote;
        this.editableAttributes = editableAttributes;
        this.editableTags = editableTags;
        this.editableIcons = editableIcons;
    }

    public EditableText getEditableText() {
        return editableText;
    }

    public EditableText getEditableDetails() {
        return editableDetails;
    }

    public EditableText getEditableNote() {
        return editableNote;
    }

    public List<EditableAttribute> getEditableAttributes() {
        return editableAttributes;
    }

    public List<EditableTag> getEditableTags() {
        return editableTags;
    }

    public List<EditableIcon> getEditableIcons() {
        return editableIcons;
    }
}

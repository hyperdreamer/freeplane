package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class EditableContentRequest {
    private final Set<EditableContentField> fields;

    @JsonCreator
    public EditableContentRequest(@JsonProperty("fields") List<EditableContentField> fields) {
        this.fields = fields == null || fields.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(fields);
    }

    public boolean includesField(EditableContentField field) {
        if (field == null) {
            return false;
        }
        return fields.isEmpty() || fields.contains(field);
    }
}

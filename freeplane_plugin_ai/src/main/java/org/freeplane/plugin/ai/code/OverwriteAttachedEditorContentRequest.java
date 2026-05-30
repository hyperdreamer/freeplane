package org.freeplane.plugin.ai.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OverwriteAttachedEditorContentRequest {
    private final String text;

    @JsonCreator
    public OverwriteAttachedEditorContentRequest(@JsonProperty("text") String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

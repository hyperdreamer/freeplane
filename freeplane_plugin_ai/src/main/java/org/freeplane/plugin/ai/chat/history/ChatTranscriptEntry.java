package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "role",
    visible = true,
    defaultImpl = ChatTranscriptEntry.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AssistantProfileTranscriptEntry.class, name = "ASSISTANT_PROFILE_SYSTEM")
})
public class ChatTranscriptEntry {
    private ChatTranscriptRole role;
    private String text;
    private String baseSystemText;

    public ChatTranscriptEntry() {
    }

    public ChatTranscriptEntry(ChatTranscriptRole role, String text) {
        this(role, text, null);
    }

    public ChatTranscriptEntry(ChatTranscriptRole role, String text, String baseSystemText) {
        this.role = role;
        this.text = text;
        this.baseSystemText = baseSystemText;
    }

    public ChatTranscriptRole getRole() {
        return role;
    }

    public void setRole(ChatTranscriptRole role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getBaseSystemText() {
        return baseSystemText;
    }

    public void setBaseSystemText(String baseSystemText) {
        this.baseSystemText = baseSystemText;
    }
}

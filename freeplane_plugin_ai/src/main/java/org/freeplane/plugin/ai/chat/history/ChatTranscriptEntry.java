package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private boolean isSystemMessageExact;
    private String promptName;
    private String promptText;
    private String modelFacingText;
    private Integer promptReferenceEndOffset;

    public ChatTranscriptEntry() {
    }

    public ChatTranscriptEntry(ChatTranscriptRole role, String text) {
        this(role, text, null, false);
    }

    public ChatTranscriptEntry(ChatTranscriptRole role,
                               String text,
                               String baseSystemText,
                               boolean isSystemMessageExact) {
        this.role = role;
        this.text = text;
        this.baseSystemText = baseSystemText;
        this.isSystemMessageExact = isSystemMessageExact;
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

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonProperty("isSystemMessageExact")
    public boolean isSystemMessageExact() {
        return isSystemMessageExact;
    }

    @JsonProperty("isSystemMessageExact")
    public void setSystemMessageExact(boolean isSystemMessageExact) {
        this.isSystemMessageExact = isSystemMessageExact;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPromptName() {
        return promptName;
    }

    public void setPromptName(String promptName) {
        this.promptName = promptName;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getModelFacingText() {
        return modelFacingText;
    }

    public void setModelFacingText(String modelFacingText) {
        this.modelFacingText = modelFacingText;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getPromptReferenceEndOffset() {
        return promptReferenceEndOffset;
    }

    public void setPromptReferenceEndOffset(Integer promptReferenceEndOffset) {
        this.promptReferenceEndOffset = promptReferenceEndOffset;
    }
}

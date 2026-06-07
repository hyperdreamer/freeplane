package org.freeplane.plugin.ai.chat.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

class AssistantProfile {
    public static final String DEFAULT_ID = "default";

    private String id;
    private String name;
    private String prompt;

    public AssistantProfile() {
    }

    public AssistantProfile(String id, String name, String prompt) {
        this.id = id;
        this.name = name;
        this.prompt = prompt;
    }

    public static AssistantProfile defaultProfile() {
        return new AssistantProfile(DEFAULT_ID, "Default", "");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    @JsonIgnore
    public boolean isDefault() {
        return Objects.equals(DEFAULT_ID, id);
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}

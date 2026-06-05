package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IconsContent {
    @Description("Icon names; use listAvailableIcons(). Emoji characters are valid icon names (use the emoji itself).")
    private final List<String> descriptions;

    @JsonCreator
    public IconsContent(@JsonProperty("descriptions") List<String> descriptions) {
        this.descriptions = descriptions;
    }

    public List<String> getDescriptions() {
        return descriptions;
    }
}

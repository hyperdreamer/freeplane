package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.search.Omissions;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadNodesWithDescendantsAsPlainTextResponse {
    private final String mapIdentifier;
    private final String plainText;
    private final Omissions omissions;
    @JsonIgnore
    private final List<String> focusNodePreviewTexts;

    @JsonCreator
    public ReadNodesWithDescendantsAsPlainTextResponse(
        @JsonProperty("mapIdentifier") String mapIdentifier,
        @JsonProperty("plainText") String plainText,
        @JsonProperty("omissions") Omissions omissions) {
        this(mapIdentifier, plainText, omissions, null);
    }

    ReadNodesWithDescendantsAsPlainTextResponse(String mapIdentifier, String plainText, Omissions omissions,
                                                List<String> focusNodePreviewTexts) {
        this.mapIdentifier = mapIdentifier;
        this.plainText = plainText;
        this.omissions = omissions;
        this.focusNodePreviewTexts = focusNodePreviewTexts;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getPlainText() {
        return plainText;
    }

    public Omissions getOmissions() {
        return omissions;
    }

    @JsonIgnore
    public List<String> getFocusNodePreviewTexts() {
        return focusNodePreviewTexts;
    }
}

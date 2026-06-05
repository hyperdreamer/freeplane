package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.search.Omissions;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadNodesWithDescendantsResponse {
    private final String mapIdentifier;
    private final List<ReadNodesWithDescendantsItem> items;
    private final Omissions omissions;
    @JsonIgnore
    private final List<String> focusNodePreviewTexts;

    @JsonCreator
    public ReadNodesWithDescendantsResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                        @JsonProperty("items") List<ReadNodesWithDescendantsItem> items,
                                        @JsonProperty("omissions") Omissions omissions) {
        this(mapIdentifier, items, omissions, null);
    }

    ReadNodesWithDescendantsResponse(String mapIdentifier, List<ReadNodesWithDescendantsItem> items, Omissions omissions,
                                     List<String> focusNodePreviewTexts) {
        this.mapIdentifier = mapIdentifier;
        this.items = items;
        this.omissions = omissions;
        this.focusNodePreviewTexts = focusNodePreviewTexts;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<ReadNodesWithDescendantsItem> getItems() {
        return items;
    }

    public Omissions getOmissions() {
        return omissions;
    }

    @JsonIgnore
    public List<String> getFocusNodePreviewTexts() {
        return focusNodePreviewTexts;
    }
}

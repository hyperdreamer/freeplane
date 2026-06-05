package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchNodesResponse {
    private final String mapIdentifier;
    private final List<SearchResultItem> results;
    private final Omissions omissions;
    @JsonIgnore
    private final List<String> resultPreviewTexts;

    @JsonCreator
    public SearchNodesResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                               @JsonProperty("results") List<SearchResultItem> results,
                               @JsonProperty("omissions") Omissions omissions) {
        this(mapIdentifier, results, omissions, null);
    }

    SearchNodesResponse(String mapIdentifier, List<SearchResultItem> results, Omissions omissions,
                        List<String> resultPreviewTexts) {
        this.mapIdentifier = mapIdentifier;
        this.results = results;
        this.omissions = omissions;
        this.resultPreviewTexts = resultPreviewTexts;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<SearchResultItem> getResults() {
        return results;
    }

    public Omissions getOmissions() {
        return omissions;
    }

    @JsonIgnore
    public List<String> getResultPreviewTexts() {
        return resultPreviewTexts;
    }
}

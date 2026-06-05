package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SearchOverviewSection {
    private final String nodeIdentifier;
    private final String nodeText;
    private final List<String> keywords;

    @JsonCreator
    public SearchOverviewSection(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                                 @JsonProperty("nodeText") String nodeText,
                                 @JsonProperty("keywords") List<String> keywords) {
        this.nodeIdentifier = nodeIdentifier;
        this.nodeText = nodeText;
        this.keywords = keywords;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public String getNodeText() {
        return nodeText;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}

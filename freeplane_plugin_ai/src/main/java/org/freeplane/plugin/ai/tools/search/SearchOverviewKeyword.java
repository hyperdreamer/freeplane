package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SearchOverviewKeyword {
    private final String term;
    private final List<String> nodeIdentifiers;

    @JsonCreator
    public SearchOverviewKeyword(@JsonProperty("term") String term,
                                 @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers) {
        this.term = term;
        this.nodeIdentifiers = nodeIdentifiers;
    }

    public String getTerm() {
        return term;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }
}

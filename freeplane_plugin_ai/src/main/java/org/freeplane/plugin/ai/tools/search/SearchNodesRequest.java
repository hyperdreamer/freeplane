package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeplane.plugin.ai.tools.content.NodeContentRequest;
import org.freeplane.plugin.ai.tools.content.TextualContentRequest;

public class SearchNodesRequest {
    private static final int DEFAULT_LIMIT = 200;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS = 65536;
    private static final SearchMatchingMode DEFAULT_MATCHING_MODE = SearchMatchingMode.CONTAINS;
    private static final SearchCaseSensitivity DEFAULT_CASE_SENSITIVITY = SearchCaseSensitivity.CASE_INSENSITIVE;
    private static final NodeContentRequest DEFAULT_CONTENT_REQUEST = new NodeContentRequest(
        new TextualContentRequest(true, false, false),
        null,
        null,
        null,
        null);
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @Description("Search query text.")
    private final String queryText;
    @JsonProperty(required = false)
    @Description("Subtree root IDs to search (default: root).")
    private final List<String> subtreeRootNodeIdentifiers;
    @JsonProperty(required = false)
    @Description("Content fields to search (default: text only).")
    private final NodeContentRequest nodeContentRequestForSearch;
    @JsonProperty(required = false)
    @Description("Matching mode (default: CONTAINS).")
    private final SearchMatchingMode matchingMode;
    @JsonProperty(required = false)
    @Description("Case sensitivity (default: CASE_INSENSITIVE).")
    private final SearchCaseSensitivity caseSensitivity;
    @JsonProperty(required = false)
    @Description("Result sections to include (default: none).")
    private final List<SearchResultSection> resultSections;
    @JsonProperty(required = false)
    @Description("Result offset (default: 0).")
    private final Integer offset;
    @JsonProperty(required = false)
    @Description("Maximum results (default: 200).")
    private final Integer limit;
    @JsonProperty(required = false)
    @Description("Maximum response length in characters (default: 65536).")
    private final Integer maximumTotalTextCharacters;
    private final boolean hasMatchingMode;
    private final boolean hasCaseSensitivity;
    private final boolean hasOffset;
    private final boolean hasLimit;
    private final boolean hasMaximumTotalTextCharacters;

    @JsonCreator
    public SearchNodesRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                              @JsonProperty("queryText") String queryText,
                              @JsonProperty("subtreeRootNodeIdentifiers") List<String> subtreeRootNodeIdentifiers,
                              @JsonProperty("nodeContentRequestForSearch") NodeContentRequest nodeContentRequestForSearch,
                              @JsonProperty("matchingMode") SearchMatchingMode matchingMode,
                              @JsonProperty("caseSensitivity") SearchCaseSensitivity caseSensitivity,
                              @JsonProperty("resultSections") List<SearchResultSection> resultSections,
                              @JsonProperty("offset") Integer offset,
                              @JsonProperty("limit") Integer limit,
                              @JsonProperty("maximumTotalTextCharacters") Integer maximumTotalTextCharacters) {
        this.mapIdentifier = mapIdentifier;
        this.queryText = queryText;
        this.subtreeRootNodeIdentifiers = subtreeRootNodeIdentifiers;
        this.nodeContentRequestForSearch = nodeContentRequestForSearch == null
            ? DEFAULT_CONTENT_REQUEST
            : nodeContentRequestForSearch;
        this.hasMatchingMode = matchingMode != null;
        this.matchingMode = matchingMode == null ? DEFAULT_MATCHING_MODE : matchingMode;
        this.hasCaseSensitivity = caseSensitivity != null;
        this.caseSensitivity = caseSensitivity == null ? DEFAULT_CASE_SENSITIVITY : caseSensitivity;
        this.resultSections = normalizeResultSections(resultSections);
        this.hasOffset = offset != null;
        this.offset = offset == null ? DEFAULT_OFFSET : Math.max(0, offset);
        this.hasLimit = limit != null;
        this.limit = limit == null ? DEFAULT_LIMIT : Math.max(0, limit);
        this.hasMaximumTotalTextCharacters = maximumTotalTextCharacters != null;
        this.maximumTotalTextCharacters = maximumTotalTextCharacters == null
            ? DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS
            : maximumTotalTextCharacters;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getQueryText() {
        return queryText;
    }

    public List<String> getSubtreeRootNodeIdentifiers() {
        return subtreeRootNodeIdentifiers;
    }

    public NodeContentRequest getNodeContentRequestForSearch() {
        return nodeContentRequestForSearch;
    }

    public SearchMatchingMode getMatchingMode() {
        return matchingMode;
    }

    public SearchCaseSensitivity getCaseSensitivity() {
        return caseSensitivity;
    }

    public List<SearchResultSection> getResultSections() {
        return resultSections;
    }

    public Integer getOffset() {
        return offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getMaximumTotalTextCharacters() {
        return maximumTotalTextCharacters;
    }

    public boolean hasMatchingMode() {
        return hasMatchingMode;
    }

    public boolean hasCaseSensitivity() {
        return hasCaseSensitivity;
    }

    public boolean hasOffset() {
        return hasOffset;
    }

    public boolean hasLimit() {
        return hasLimit;
    }

    public boolean hasMaximumTotalTextCharacters() {
        return hasMaximumTotalTextCharacters;
    }

    private static List<SearchResultSection> normalizeResultSections(List<SearchResultSection> resultSections) {
        if (resultSections == null || resultSections.isEmpty()) {
            return Collections.emptyList();
        }
        List<SearchResultSection> normalized = new ArrayList<>();
        for (SearchResultSection section : resultSections) {
            if (section != null) {
                normalized.add(section);
            }
        }
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(normalized);
    }
}

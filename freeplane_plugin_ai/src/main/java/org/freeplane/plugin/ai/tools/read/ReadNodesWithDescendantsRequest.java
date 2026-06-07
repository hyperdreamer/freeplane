package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadNodesWithDescendantsRequest {
    private static final int DEFAULT_FULL_CONTENT_DEPTH = 0;
    private static final int DEFAULT_SUMMARY_DEPTH = 1;
    private static final int DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS = 65536;
    @Description("Target map ID (from getSelectedMapAndNodeIdentifiers).")
    private final String mapIdentifier;
    @JsonProperty(required = false)
    @Description("Node IDs to read (default: root).")
    private final List<String> nodeIdentifiers;
    @JsonProperty(required = false)
    @Description("Extra sections (default: none). QUALIFIERS adds summary_node/first_group_node.")
    private final List<ContextSection> contextSections;
    @JsonProperty(required = false)
    @Description("Depth of full content (default: 0).")
    private final Integer fullContentDepth;
    @JsonProperty(required = false)
    @Description("Summary depth beyond fullContentDepth (default: 1).")
    private final Integer summaryDepth;
    @JsonProperty(required = false)
    @Description("Maximum response length in characters (default: 65536).")
    private final Integer maximumTotalTextCharacters;
    private final boolean hasFullContentDepth;
    private final boolean hasSummaryDepth;
    private final boolean hasMaximumTotalTextCharacters;

    @JsonCreator
    public ReadNodesWithDescendantsRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                       @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers,
                                       @JsonProperty("contextSections") List<ContextSection> contextSections,
                                       @JsonProperty("fullContentDepth") Integer fullContentDepth,
                                       @JsonProperty("summaryDepth") Integer summaryDepth,
                                       @JsonProperty("maximumTotalTextCharacters") Integer maximumTotalTextCharacters) {
        this.mapIdentifier = mapIdentifier;
        this.nodeIdentifiers = nodeIdentifiers;
        this.contextSections = normalizeContextSections(contextSections);
        this.hasFullContentDepth = fullContentDepth != null;
        this.fullContentDepth = fullContentDepth == null ? DEFAULT_FULL_CONTENT_DEPTH : fullContentDepth;
        this.hasSummaryDepth = summaryDepth != null;
        this.summaryDepth = summaryDepth == null ? DEFAULT_SUMMARY_DEPTH : summaryDepth;
        this.hasMaximumTotalTextCharacters = maximumTotalTextCharacters != null;
        this.maximumTotalTextCharacters = maximumTotalTextCharacters == null
            ? DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS
            : maximumTotalTextCharacters;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public List<String> getNodeIdentifiers() {
        return nodeIdentifiers;
    }

    public List<ContextSection> getContextSections() {
        return contextSections;
    }

    public Integer getFullContentDepth() {
        return fullContentDepth;
    }

    public Integer getSummaryDepth() {
        return summaryDepth;
    }

    public Integer getMaximumTotalTextCharacters() {
        return maximumTotalTextCharacters;
    }

    public boolean hasFullContentDepth() {
        return hasFullContentDepth;
    }

    public boolean hasSummaryDepth() {
        return hasSummaryDepth;
    }

    public boolean hasMaximumTotalTextCharacters() {
        return hasMaximumTotalTextCharacters;
    }

    private static List<ContextSection> normalizeContextSections(List<ContextSection> contextSections) {
        if (contextSections == null || contextSections.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContextSection> normalized = new ArrayList<>();
        for (ContextSection section : contextSections) {
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

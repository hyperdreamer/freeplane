package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadNodesWithDescendantsRequest {
    private static final int DEFAULT_FULL_CONTENT_DEPTH = 0;
    private static final int DEFAULT_ADDITIONAL_SUMMARY_DEPTH = 1;
    private static final int DEFAULT_MAX_CHARACTERS = 65536;
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
    @Description("Additional summary-only depth beyond fullContentDepth (default: 1).")
    private final Integer additionalSummaryDepth;
    @JsonProperty(required = false)
    @Description("Maximum response length in characters (default: 65536).")
    private final Integer maxCharacters;

    @JsonCreator
    public ReadNodesWithDescendantsRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                                           @JsonProperty("nodeIdentifiers") List<String> nodeIdentifiers,
                                           @JsonProperty("contextSections") List<ContextSection> contextSections,
                                           @JsonProperty("fullContentDepth") Integer fullContentDepth,
                                           @JsonProperty("additionalSummaryDepth") Integer additionalSummaryDepth,
                                           @JsonProperty("maxCharacters") Integer maxCharacters) {
        this.mapIdentifier = mapIdentifier;
        this.nodeIdentifiers = nodeIdentifiers;
        this.contextSections = normalizeContextSections(contextSections);
        this.fullContentDepth = fullContentDepth;
        this.additionalSummaryDepth = additionalSummaryDepth;
        this.maxCharacters = maxCharacters;
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
        return fullContentDepth == null ? DEFAULT_FULL_CONTENT_DEPTH : fullContentDepth;
    }

    public Integer getAdditionalSummaryDepth() {
        return additionalSummaryDepth == null ? DEFAULT_ADDITIONAL_SUMMARY_DEPTH : additionalSummaryDepth;
    }

    public Integer getMaxCharacters() {
        return maxCharacters == null ? DEFAULT_MAX_CHARACTERS : maxCharacters;
    }

    public boolean hasFullContentDepth() {
        return fullContentDepth != null;
    }

    public boolean hasAdditionalSummaryDepth() {
        return additionalSummaryDepth != null;
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

package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SearchOverviewResponse {
    private final String mapIdentifier;
    private final String summary;
    private final List<String> themes;
    private final List<SearchOverviewSection> sections;
    private final List<SearchOverviewKeyword> keywords;

    @JsonCreator
    public SearchOverviewResponse(@JsonProperty("mapIdentifier") String mapIdentifier,
                                  @JsonProperty("summary") String summary,
                                  @JsonProperty("themes") List<String> themes,
                                  @JsonProperty("sections") List<SearchOverviewSection> sections,
                                  @JsonProperty("keywords") List<SearchOverviewKeyword> keywords) {
        this.mapIdentifier = mapIdentifier;
        this.summary = summary;
        this.themes = themes;
        this.sections = sections;
        this.keywords = keywords;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getThemes() {
        return themes;
    }

    public List<SearchOverviewSection> getSections() {
        return sections;
    }

    public List<SearchOverviewKeyword> getKeywords() {
        return keywords;
    }
}

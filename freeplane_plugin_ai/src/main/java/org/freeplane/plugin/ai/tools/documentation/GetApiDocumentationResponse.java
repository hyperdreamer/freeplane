package org.freeplane.plugin.ai.tools.documentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetApiDocumentationResponse {
    private final String mapIdentifier;
    private final String rootNodeIdentifier;
    private final String packagesRootNodeIdentifier;
    private final String apiGroupsRootNodeIdentifier;
    private final String structureSummary;

    @JsonCreator
    public GetApiDocumentationResponse(
        @JsonProperty("mapIdentifier") String mapIdentifier,
        @JsonProperty("rootNodeIdentifier") String rootNodeIdentifier,
        @JsonProperty("packagesRootNodeIdentifier") String packagesRootNodeIdentifier,
        @JsonProperty("apiGroupsRootNodeIdentifier") String apiGroupsRootNodeIdentifier,
        @JsonProperty("structureSummary") String structureSummary) {
        this.mapIdentifier = mapIdentifier;
        this.rootNodeIdentifier = rootNodeIdentifier;
        this.packagesRootNodeIdentifier = packagesRootNodeIdentifier;
        this.apiGroupsRootNodeIdentifier = apiGroupsRootNodeIdentifier;
        this.structureSummary = structureSummary;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getRootNodeIdentifier() {
        return rootNodeIdentifier;
    }

    public String getPackagesRootNodeIdentifier() {
        return packagesRootNodeIdentifier;
    }

    public String getApiGroupsRootNodeIdentifier() {
        return apiGroupsRootNodeIdentifier;
    }

    public String getStructureSummary() {
        return structureSummary;
    }
}

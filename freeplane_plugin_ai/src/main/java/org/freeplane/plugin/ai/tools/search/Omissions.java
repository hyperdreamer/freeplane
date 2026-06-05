package org.freeplane.plugin.ai.tools.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Omissions {
    private final Integer omittedFocusNodeCount;
    private final Integer omittedChildCount;
    private final Integer omittedDescendantCount;
    private final Integer omittedResultCount;
    private final List<OmissionReason> omissionReasons;

    @JsonCreator
    public Omissions(@JsonProperty("omittedFocusNodeCount") Integer omittedFocusNodeCount,
                     @JsonProperty("omittedChildCount") Integer omittedChildCount,
                     @JsonProperty("omittedDescendantCount") Integer omittedDescendantCount,
                     @JsonProperty("omittedResultCount") Integer omittedResultCount,
                     @JsonProperty("omissionReasons") List<OmissionReason> omissionReasons) {
        this.omittedFocusNodeCount = omittedFocusNodeCount;
        this.omittedChildCount = omittedChildCount;
        this.omittedDescendantCount = omittedDescendantCount;
        this.omittedResultCount = omittedResultCount;
        this.omissionReasons = omissionReasons;
    }

    public Integer getOmittedFocusNodeCount() {
        return omittedFocusNodeCount;
    }

    public Integer getOmittedChildCount() {
        return omittedChildCount;
    }

    public Integer getOmittedDescendantCount() {
        return omittedDescendantCount;
    }

    public Integer getOmittedResultCount() {
        return omittedResultCount;
    }

    public List<OmissionReason> getOmissionReasons() {
        return omissionReasons;
    }
}

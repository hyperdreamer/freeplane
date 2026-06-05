package org.freeplane.plugin.ai.tools.read;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.freeplane.plugin.ai.tools.search.Omissions;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadNodesWithDescendantsItem {
    private final List<NodeDepthItem> nodes;
    private final NodeDepthItem parentNode;
    private final String breadcrumbPath;
    private final Omissions childOmissions;

    @JsonCreator
    public ReadNodesWithDescendantsItem(@JsonProperty("nodes") List<NodeDepthItem> nodes,
                                    @JsonProperty("parentNode") NodeDepthItem parentNode,
                                    @JsonProperty("breadcrumbPath") String breadcrumbPath,
                                    @JsonProperty("childOmissions") Omissions childOmissions) {
        this.nodes = nodes;
        this.parentNode = parentNode;
        this.breadcrumbPath = breadcrumbPath;
        this.childOmissions = childOmissions;
    }

    public List<NodeDepthItem> getNodes() {
        return nodes;
    }

    public NodeDepthItem getParentNode() {
        return parentNode;
    }

    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    public Omissions getChildOmissions() {
        return childOmissions;
    }
}

package org.freeplane.plugin.ai.tools.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloneMetadata {
    private final List<String> cloneNodeIdentifiers;
    private final boolean isCloneTreeRoot;
    private final boolean isCloneTreeNode;

    @JsonCreator
    public CloneMetadata(@JsonProperty("cloneNodeIdentifiers") List<String> cloneNodeIdentifiers,
                         @JsonProperty("isCloneTreeRoot") boolean isCloneTreeRoot,
                         @JsonProperty("isCloneTreeNode") boolean isCloneTreeNode) {
        this.cloneNodeIdentifiers = cloneNodeIdentifiers;
        this.isCloneTreeRoot = isCloneTreeRoot;
        this.isCloneTreeNode = isCloneTreeNode;
    }

    public List<String> getCloneNodeIdentifiers() {
        return cloneNodeIdentifiers;
    }

    public boolean isCloneTreeRoot() {
        return isCloneTreeRoot;
    }

    public boolean isCloneTreeNode() {
        return isCloneTreeNode;
    }
}

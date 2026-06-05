package org.freeplane.plugin.ai.tools.create;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import org.freeplane.plugin.ai.tools.content.NodeContentWriteRequest;

public class NodeCreationItem {
    @Description("Item index in the nodes list.")
    private final Integer index;
    @Description("Parent index in this list (-1 uses anchor).")
    private final Integer parentIndex;
    private final NodeContentWriteRequest content;
    @JsonProperty(required = false)
    @Description("Optional folding state for new non-leaf nodes (default: UNFOLD).")
    private final NodeFoldingState foldingState;
    @JsonProperty(required = false)
    @Description("Optional logical style name for the new node.")
    private final String mainStyle;

    @JsonCreator
    public NodeCreationItem(@JsonProperty("index") Integer index,
                            @JsonProperty("parentIndex") Integer parentIndex,
                            @JsonProperty("content") NodeContentWriteRequest content,
                            @JsonProperty(value = "foldingState", required = false) NodeFoldingState foldingState,
                            @JsonProperty(value = "mainStyle", required = false) String mainStyle) {
        this.index = index;
        this.parentIndex = parentIndex;
        this.content = content;
        this.foldingState = foldingState;
        this.mainStyle = mainStyle;
    }

    public Integer getIndex() {
        return index;
    }

    public Integer getParentIndex() {
        return parentIndex;
    }

    public NodeContentWriteRequest getContent() {
        return content;
    }

    public NodeFoldingState getFoldingState() {
        return foldingState;
    }

    public String getMainStyle() {
        return mainStyle;
    }
}

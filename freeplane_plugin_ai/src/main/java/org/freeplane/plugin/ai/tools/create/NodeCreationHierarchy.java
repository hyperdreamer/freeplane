package org.freeplane.plugin.ai.tools.create;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.freeplane.features.map.NodeModel;

public class NodeCreationHierarchy {
    private final List<NodeModel> rootNodes;
    private final List<NodeModel> createdNodes;
    private final Map<NodeModel, NodeFoldingState> foldingStates;

    public NodeCreationHierarchy(List<NodeModel> rootNodes, List<NodeModel> createdNodes) {
        this(rootNodes, createdNodes, Collections.emptyMap());
    }

    public NodeCreationHierarchy(List<NodeModel> rootNodes, List<NodeModel> createdNodes,
                                 Map<NodeModel, NodeFoldingState> foldingStates) {
        this.rootNodes = rootNodes == null ? Collections.emptyList() : rootNodes;
        this.createdNodes = createdNodes == null ? Collections.emptyList() : createdNodes;
        this.foldingStates = foldingStates == null ? Collections.emptyMap() : foldingStates;
    }

    public List<NodeModel> getRootNodes() {
        return rootNodes;
    }

    public List<NodeModel> getCreatedNodes() {
        return createdNodes;
    }

    public Map<NodeModel, NodeFoldingState> getFoldingStates() {
        return foldingStates;
    }
}

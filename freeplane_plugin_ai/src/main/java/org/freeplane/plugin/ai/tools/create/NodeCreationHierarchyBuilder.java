package org.freeplane.plugin.ai.tools.create;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.NodeContentApplier;
import org.freeplane.plugin.ai.tools.edit.NodeStyleContentEditor;

public class NodeCreationHierarchyBuilder {
    private final NodeModelCreator nodeModelCreator;
    private final NodeContentApplier nodeContentApplier;
    private final NodeStyleContentEditor nodeStyleContentEditor;

    public NodeCreationHierarchyBuilder(NodeModelCreator nodeModelCreator, NodeContentApplier nodeContentApplier,
                                        NodeStyleContentEditor nodeStyleContentEditor) {
        this.nodeModelCreator = Objects.requireNonNull(nodeModelCreator, "nodeModelCreator");
        this.nodeContentApplier = Objects.requireNonNull(nodeContentApplier, "nodeContentApplier");
        this.nodeStyleContentEditor = Objects.requireNonNull(nodeStyleContentEditor, "nodeStyleContentEditor");
    }

    public NodeCreationHierarchy buildHierarchy(List<NodeCreationItem> items, MapModel mapModel) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Nodes list must be non-empty.");
        }
        if (mapModel == null) {
            throw new IllegalArgumentException("Missing map model.");
        }
        Map<Integer, NodeModel> nodesByIndex = new HashMap<>();
        Set<Integer> indices = new HashSet<>();
        Set<Integer> parentIndices = new HashSet<>();
        List<NodeModel> createdNodes = new ArrayList<>(items.size());
        for (NodeCreationItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Node item must be non-null.");
            }
            Integer index = item.getIndex();
            if (index == null) {
                throw new IllegalArgumentException("Missing index.");
            }
            if (index < 0) {
                throw new IllegalArgumentException("Invalid index: " + index);
            }
            if (!indices.add(index)) {
                throw new IllegalArgumentException("Duplicate index: " + index);
            }
            NodeModel nodeModel = nodeModelCreator.createNodeModel(mapModel);
            nodesByIndex.put(index, nodeModel);
            createdNodes.add(nodeModel);
            nodeContentApplier.apply(nodeModel, item.getContent());
            nodeStyleContentEditor.setInitialMainStyle(nodeModel, item.getMainStyle());
            Integer parentIndex = item.getParentIndex();
            if (parentIndex != null && parentIndex >= 0) {
                parentIndices.add(parentIndex);
            }
        }

        List<NodeModel> rootNodes = new ArrayList<>();
        Map<NodeModel, NodeFoldingState> foldingStates = new HashMap<>();
        for (NodeCreationItem item : items) {
            Integer parentIndex = item.getParentIndex();
            NodeModel nodeModel = nodesByIndex.get(item.getIndex());
            if (parentIndex == null || parentIndex == -1) {
                rootNodes.add(nodeModel);
            } else {
                if (parentIndex < 0) {
                    throw new IllegalArgumentException("Invalid parentIndex: " + parentIndex);
                }
                if (parentIndex.equals(item.getIndex())) {
                    throw new IllegalArgumentException("parentIndex must differ from index: " + parentIndex);
                }
                NodeModel parentNode = nodesByIndex.get(parentIndex);
                if (parentNode == null) {
                    throw new IllegalArgumentException("Unknown parentIndex: " + parentIndex);
                }
                parentNode.insert(nodeModel, parentNode.getChildCount());
            }
            if (parentIndices.contains(item.getIndex())) {
                NodeFoldingState foldingState = item.getFoldingState();
                foldingStates.put(nodeModel,
                    foldingState == null ? NodeFoldingState.UNFOLD : foldingState);
            }
        }

        return new NodeCreationHierarchy(rootNodes, createdNodes, foldingStates);
    }
}

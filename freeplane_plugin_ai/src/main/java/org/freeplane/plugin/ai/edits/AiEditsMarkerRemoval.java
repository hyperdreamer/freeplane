package org.freeplane.plugin.ai.edits;

import java.util.LinkedHashMap;
import java.util.Map;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeIterator;
import org.freeplane.features.map.NodeModel;

public class AiEditsMarkerRemoval {
    public Map<NodeModel, AIEdits> collectNodesWithAiEdits(NodeModel rootNode) {
        Map<NodeModel, AIEdits> removedEditsByNode = new LinkedHashMap<>();
        if (rootNode == null) {
            return removedEditsByNode;
        }
        NodeIterator<NodeModel> iterator = NodeIterator.of(rootNode, NodeModel::getChildren);
        while (iterator.hasNext()) {
            NodeModel node = iterator.next();
            AIEdits aiEdits = node.getExtension(AIEdits.class);
            if (aiEdits != null) {
                removedEditsByNode.put(node, aiEdits);
            }
        }
        return removedEditsByNode;
    }

    public Map<NodeModel, AIEdits> collectNodesWithAiEdits(Iterable<NodeModel> nodes) {
        Map<NodeModel, AIEdits> removedEditsByNode = new LinkedHashMap<>();
        if (nodes == null) {
            return removedEditsByNode;
        }
        for (NodeModel node : nodes) {
            if (node == null) {
                continue;
            }
            AIEdits aiEdits = node.getExtension(AIEdits.class);
            if (aiEdits != null) {
                removedEditsByNode.put(node, aiEdits);
            }
        }
        return removedEditsByNode;
    }

    public IActor createUndoableRemoval(Map<NodeModel, AIEdits> removedEditsByNode, MapController mapController,
                                        String description) {
        return new IActor() {
            @Override
            public void act() {
                removeAiEdits(removedEditsByNode, mapController);
            }

            @Override
            public void undo() {
                restoreAiEdits(removedEditsByNode, mapController);
            }

            @Override
            public String getDescription() {
                return description == null ? "" : description;
            }
        };
    }

    private void removeAiEdits(Map<NodeModel, AIEdits> removedEditsByNode, MapController mapController) {
        if (removedEditsByNode == null || mapController == null) {
            return;
        }
        for (Map.Entry<NodeModel, AIEdits> entry : removedEditsByNode.entrySet()) {
            NodeModel node = entry.getKey();
            AIEdits existingEdits = entry.getValue();
            node.removeExtension(AIEdits.class);
            mapController.nodeChanged(node, AIEdits.class, existingEdits, null);
        }
    }

    private void restoreAiEdits(Map<NodeModel, AIEdits> removedEditsByNode, MapController mapController) {
        if (removedEditsByNode == null || mapController == null) {
            return;
        }
        for (Map.Entry<NodeModel, AIEdits> entry : removedEditsByNode.entrySet()) {
            NodeModel node = entry.getKey();
            AIEdits existingEdits = entry.getValue();
            node.addExtension(existingEdits);
            mapController.nodeChanged(node, AIEdits.class, null, existingEdits);
        }
    }
}

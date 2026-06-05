package org.freeplane.plugin.ai.tools.create;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeModel.Side;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.OperationErrorHandler;

public class NodeInserter {
    private final MMapController mapController;
    private final AnchorPlacementCalculator anchorPlacementCalculator;

    public NodeInserter(MMapController mapController, AnchorPlacementCalculator anchorPlacementCalculator) {
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.anchorPlacementCalculator = Objects.requireNonNull(anchorPlacementCalculator, "anchorPlacementCalculator");
    }

    public List<NodeModel> insertNodes(List<NodeModel> nodes, NodeModel anchorNode, AnchorPlacementMode placementMode) {
        return insertNodes(nodes, anchorNode, placementMode, null);
    }

    public List<NodeModel> insertNodes(List<NodeModel> nodes, NodeModel anchorNode, AnchorPlacementMode placementMode,
                                       OperationErrorHandler errorHandler) {
        if (nodes == null) {
            throw new IllegalArgumentException("Missing nodes.");
        }
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (anchorNode == null) {
            throw new IllegalArgumentException("Missing anchor node.");
        }
        if (placementMode == null) {
            throw new IllegalArgumentException("Missing placement mode.");
        }
        AnchorPlacementResult placement = anchorPlacementCalculator.calculatePlacement(anchorNode, placementMode);
        NodeModel parentNode = placement.getParentNode();
        if (!mapController.isWriteable(parentNode)) {
            throw new IllegalArgumentException("Target node is write protected.");
        }
        int insertionIndex = placement.getInsertionIndex();
        for (NodeModel node : nodes) {
            applySide(node, anchorNode, parentNode, placementMode);
            if (errorHandler == null) {
                mapController.insertNode(node, parentNode, insertionIndex);
            } else {
                mapController.insertNode(node, parentNode, insertionIndex, errorHandler);
            }
            insertionIndex += 1;
        }
        return nodes;
    }

    private void applySide(NodeModel node, NodeModel anchorNode, NodeModel parentNode,
                           AnchorPlacementMode placementMode) {
        if (placementMode == AnchorPlacementMode.SIBLING_BEFORE
            || placementMode == AnchorPlacementMode.SIBLING_AFTER) {
            node.setSide(anchorNode.getSide());
            return;
        }
        if (parentNode.isRoot()) {
            Side side = MapController.suggestNewChildSide(parentNode, Side.DEFAULT);
            node.setSide(side);
        }
    }
}

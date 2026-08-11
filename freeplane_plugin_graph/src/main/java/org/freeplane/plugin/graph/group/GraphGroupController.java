package org.freeplane.plugin.graph.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SharedNodeData;
import org.freeplane.features.mode.ModeController;

public final class GraphGroupController implements IExtension {
    private GraphGroupAction graphGroupAction;
    private GraphGroupBuilder graphGroupBuilder;
    private final MapController mapController;
    private final ModeController modeController;

    public GraphGroupController(final ModeController modeController) {
        if (modeController == null) {
            throw new IllegalArgumentException("modeController must not be null");
        }
        this.modeController = modeController;
        mapController = modeController.getMapController();
        if (mapController == null) {
            throw new IllegalArgumentException("modeController must provide a map controller");
        }
        graphGroupBuilder = new GraphGroupBuilder();
        final ReadManager reader = mapController.getReadManager();
        final WriteManager writer = mapController.getWriteManager();
        graphGroupBuilder.registerBy(reader, writer);
        graphGroupAction = new GraphGroupAction(modeController, this);
        modeController.addAction(graphGroupAction);
    }

    public void close() {
        if (graphGroupBuilder == null) {
            return;
        }
        graphGroupBuilder.unregisterFrom(mapController.getReadManager(), mapController.getWriteManager());
        modeController.removeAction(graphGroupAction.getKey());
        graphGroupAction = null;
        graphGroupBuilder = null;
    }

    public boolean isMarked(final NodeModel node) {
        return GraphGroupModel.isMarked(node);
    }

    public void setMarked(final Collection<NodeModel> nodes, final boolean marked) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        final MapModel map = validateNodes(nodes);
        validateMap(map);
        final List<NodeModel> changedNodes = changedNodes(nodes, marked);
        if (changedNodes.isEmpty()) {
            return;
        }
        final List<Boolean> previousStates = previousStates(changedNodes);
        modeController.execute(new IActor() {
            @Override
            public void act() {
                for (NodeModel node : changedNodes) {
                    setMarker(node, marked);
                }
            }

            @Override
            public String getDescription() {
                return "setGraphGroup";
            }

            @Override
            public void undo() {
                for (int index = 0; index < changedNodes.size(); index++) {
                    setMarker(changedNodes.get(index), previousStates.get(index).booleanValue());
                }
            }
        }, map);
    }

    public int affectedClonePositionCount(final Collection<NodeModel> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = 0;
        final Set<SharedNodeData> sharedData = identitySet();
        for (NodeModel node : nodes) {
            if (node != null && node.allClones().contains(node) && sharedData.add(node.getSharedData())) {
                count += node.allClones().size();
            }
        }
        return count;
    }

    private List<NodeModel> changedNodes(final Collection<NodeModel> nodes, final boolean marked) {
        final List<NodeModel> changedNodes = new ArrayList<NodeModel>();
        for (NodeModel node : uniqueNodes(nodes)) {
            if (GraphGroupModel.isMarked(node) != marked) {
                changedNodes.add(node);
            }
        }
        return changedNodes;
    }

    private Set<SharedNodeData> identitySet() {
        final Map<SharedNodeData, Boolean> values = new IdentityHashMap<SharedNodeData, Boolean>();
        return Collections.newSetFromMap(values);
    }

    private List<Boolean> previousStates(final List<NodeModel> nodes) {
        final List<Boolean> previousStates = new ArrayList<Boolean>(nodes.size());
        for (NodeModel node : nodes) {
            previousStates.add(Boolean.valueOf(GraphGroupModel.isMarked(node)));
        }
        return previousStates;
    }

    private void setMarker(final NodeModel node, final boolean marked) {
        if (marked) {
            node.putExtension(new GraphGroupModel());
        }
        else {
            node.removeExtension(GraphGroupModel.class);
        }
        mapController.nodeChanged(node);
    }

    private List<NodeModel> uniqueNodes(final Collection<NodeModel> nodes) {
        final List<NodeModel> uniqueNodes = new ArrayList<NodeModel>();
        final Set<SharedNodeData> sharedData = identitySet();
        for (NodeModel node : nodes) {
            if (sharedData.add(node.getSharedData())) {
                uniqueNodes.add(node);
            }
        }
        return uniqueNodes;
    }

    private void validateMap(final MapModel map) {
        if (map.isReadOnly() || !modeController.canEdit(map)) {
            throw new IllegalStateException("Graph Group requires an editable map");
        }
        if (map.getExtension(IUndoHandler.class) == null) {
            throw new IllegalStateException("Graph Group requires undo support");
        }
    }

    private MapModel validateNodes(final Collection<NodeModel> nodes) {
        MapModel map = null;
        for (NodeModel node : nodes) {
            if (node == null || node.getMap() == null || !node.allClones().contains(node)) {
                throw new IllegalArgumentException("Graph Group requires attached nodes");
            }
            if (map == null) {
                map = node.getMap();
            }
            else if (map != node.getMap()) {
                throw new IllegalArgumentException("Graph Group selection must belong to one map");
            }
        }
        return map;
    }
}

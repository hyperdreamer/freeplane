package org.freeplane.plugin.ai.tools.utilities;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.OperationErrorHandler;

public class ToolErrorHandler implements OperationErrorHandler {

    private final String prefix;

    public ToolErrorHandler(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    @Override
    public void handleError(String description, List<NodeModel> involvedNodes) {
        throw new IllegalStateException(prefix + description + describeNodes(involvedNodes));
    }

    private static String describeNodes(List<NodeModel> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        return " [nodes=" + nodes.stream().map(NodeModel::createID).collect(Collectors.joining(",")) + "]";
    }
}

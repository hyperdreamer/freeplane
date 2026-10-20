package org.freeplane.plugin.graph.group;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;

public final class GraphGroupModel implements IExtension {
    public static final int FORMAT_VERSION = 1;

    public static boolean isMarked(final NodeModel node) {
        return node != null && node.getExtension(GraphGroupModel.class) != null;
    }
}

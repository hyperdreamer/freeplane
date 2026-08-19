package org.freeplane.plugin.graph.canvas;

public final class AdaptiveRenderingPolicy {
    private static final int FULL_NODE_LIMIT = 500;
    private static final int ENGINEERING_NODE_LIMIT = 2000;
    private static final int ENGINEERING_EDGE_LIMIT = 5000;

    public RenderingLevel forCounts(final int nodes, final int edges) {
        requireNonnegative(nodes, "nodes");
        requireNonnegative(edges, "edges");
        if (nodes < FULL_NODE_LIMIT && edges <= ENGINEERING_EDGE_LIMIT) {
            return RenderingLevel.FULL;
        }
        if (nodes <= ENGINEERING_NODE_LIMIT && edges <= ENGINEERING_EDGE_LIMIT) {
            return RenderingLevel.DENSE;
        }
        return RenderingLevel.OVER_TARGET;
    }

    public boolean exceedsEngineeringTarget(final int nodes, final int edges) {
        requireNonnegative(nodes, "nodes");
        requireNonnegative(edges, "edges");
        return nodes > ENGINEERING_NODE_LIMIT || edges > ENGINEERING_EDGE_LIMIT;
    }

    private static void requireNonnegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}

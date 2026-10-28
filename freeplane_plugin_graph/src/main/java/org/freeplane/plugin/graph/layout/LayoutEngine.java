package org.freeplane.plugin.graph.layout;

import java.util.Map;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public interface LayoutEngine extends AutoCloseable {
    LayoutFrame apply(LayoutRequest request);

    LayoutFrame step();

    void reset();

    /**
     * Reports the accepted, post-correction node centres for the frame just published, so the
     * relaxation can continue from the repaired arrangement instead of from an arrangement the
     * correction has already rejected. Implementations that hold no such state may ignore it.
     */
    default void seedPositions(final Map<ProjectedNodeKey, LayoutPoint> accepted) {
    }

    @Override
    void close();
}

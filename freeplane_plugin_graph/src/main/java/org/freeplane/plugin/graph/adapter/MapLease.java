package org.freeplane.plugin.graph.adapter;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public interface MapLease extends AutoCloseable {
    MapReferenceId mapReferenceId();
    MapOperationalState state();
    @Override
    void close();
}

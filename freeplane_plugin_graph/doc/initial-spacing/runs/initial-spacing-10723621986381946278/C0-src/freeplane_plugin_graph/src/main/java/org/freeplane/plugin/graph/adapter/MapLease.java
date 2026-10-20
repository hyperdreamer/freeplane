package org.freeplane.plugin.graph.adapter;

import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public interface MapLease extends AutoCloseable {
    MapReferenceId mapReferenceId();
    MapOperationalState state();
    @Override
    void close();
}

interface MapLeaseAccess {
    <T> T withModelOnEdt(MapModelCallback<T> callback);
}

interface MapModelCallback<T> {
    T apply(MapModel model, int workspaceOrder);
}

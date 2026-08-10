package org.freeplane.plugin.graph.workspace;

public interface ListenerRegistration extends AutoCloseable {
    @Override
    void close();
}

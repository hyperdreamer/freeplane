package org.freeplane.plugin.graph.layout;

public interface LayoutEngine extends AutoCloseable {
    LayoutFrame apply(LayoutRequest request);

    LayoutFrame step();

    void reset();

    @Override
    void close();
}

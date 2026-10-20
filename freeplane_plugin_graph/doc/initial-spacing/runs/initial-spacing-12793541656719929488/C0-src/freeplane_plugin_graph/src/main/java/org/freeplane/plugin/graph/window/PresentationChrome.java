package org.freeplane.plugin.graph.window;

interface PresentationChrome {
    void capture();
    void hide();
    void restore();
    void discard();
}
